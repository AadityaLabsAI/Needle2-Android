package com.aadityalabs.needle2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MonitorAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: MonitorAccessibilityService? = null
        private val scanRequested = AtomicBoolean(false)
        private var enabled = false

        fun requestStart() { enabled = true }
        fun requestScan() {
            scanRequested.set(true)
            instance?.scanActiveWindow()
        }
        fun requestStop() {
            enabled = false
            scanRequested.set(false)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var googleClickInProgress = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 300
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (enabled && scanRequested.get()) scanActiveWindow()
    }

    private fun scanActiveWindow() {
        if (!enabled || !scanRequested.compareAndSet(true, false)) return
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString()?.lowercase(Locale.US) ?: return
        if (!isLifePointsPackage(packageName)) return

        if (findAndClickGoogle(root)) return

        val texts = ArrayList<String>()
        collectText(root, texts)
        val joined = texts.joinToString(" ").lowercase(Locale.US)

        when {
            containsSorry(joined) -> {
                alert("Life Points: Sorry detected. Returning to Home.")
                performGlobalAction(GLOBAL_ACTION_HOME)
                scheduleNextCycle()
            }
            containsTarget(joined) -> {
                alert("Life Points: target value detected: " + findTarget(joined))
                // Avoid repeatedly alarming while the same value remains visible.
                handler.postDelayed({ if (enabled) requestScan() }, 30_000L)
            }
            else -> {
                // Loading/logo/dashboard-without-target: wait for the next scheduled check.
            }
        }
    }

    private fun findAndClickGoogle(root: AccessibilityNodeInfo): Boolean {
        if (googleClickInProgress) return true

        val matches = root.findAccessibilityNodeInfosByText("Google")
        val node = matches.firstOrNull { it.isVisibleToUser && (it.isClickable || it.isFocusable) }
            ?: matches.firstOrNull { it.isVisibleToUser }

        matches.filter { it !== node }.forEach { it.recycle() }
        if (node == null) return false

        googleClickInProgress = true
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()

        if (clicked) {
            handler.postDelayed({
                googleClickInProgress = false
                if (enabled) requestScan()
            }, 15_000L)
            return true
        }

        googleClickInProgress = false
        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, out)
                child.recycle()
            }
        }
    }

    private fun containsTarget(text: String): Boolean =
        Regex("""(?<!\d)(40|65|90|100)(?!\d)""").containsMatchIn(text)

    private fun findTarget(text: String): String =
        Regex("""(?<!\d)(40|65|90|100)(?!\d)""").find(text)?.value ?: "target"

    private fun containsSorry(text: String): Boolean =
        Regex("""\bsorry\b""").containsMatchIn(text)

    private fun isLifePointsPackage(packageName: String): Boolean =
        packageName.contains("lifepoints") ||
        packageName.contains("life.points") ||
        packageName.contains("lifepoint")

    private fun alert(message: String) {
        MonitorForegroundServiceBridge.alert(this, message)
    }

    private fun scheduleNextCycle() {
        // The foreground service owns the randomized 8–10 minute loop.
        MonitorForegroundService.scheduleNextCycle()
    }

    override fun onInterrupt() {
        scanRequested.set(false)
    }

    override fun onDestroy() {
        instance = null
        scanRequested.set(false)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
