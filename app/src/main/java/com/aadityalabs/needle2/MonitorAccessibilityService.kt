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
        private const val LIFE_POINTS_PACKAGE = "com.kantarprofiles.lifepoints"
        private var instance: MonitorAccessibilityService? = null
        private val scanRequested = AtomicBoolean(false)
        private var enabled = false

        fun requestStart() {
            enabled = true
        }

        fun requestScan() {
            scanRequested.set(true)
            instance?.scanActiveWindow()
        }

        fun requestStop() {
            enabled = false
            scanRequested.set(false)
            instance?.handler?.removeCallbacksAndMessages(null)
        }

        fun isLifePointsForeground(): Boolean =
            instance?.rootInActiveWindow?.packageName?.toString()?.lowercase(Locale.US) ==
                LIFE_POINTS_PACKAGE
    }

    private val handler = Handler(Looper.getMainLooper())
    private var googleClickInProgress = false
    private var lastTargetAlertAt = 0L

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
        if (packageName != LIFE_POINTS_PACKAGE) return

        val texts = ArrayList<String>()
        collectText(root, texts)
        val joined = texts.joinToString(" ").lowercase(Locale.US)

        // Only perform the narrow navigation action needed to continue into the
        // already-authenticated Life Points dashboard. Never answer or submit surveys.
        if (findAndClickGoogle(root, joined)) return

        when {
            containsSorry(joined) -> {
                alert("Life Points: Sorry detected.")
                // Leave the app without attempting to answer anything.
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            containsTarget(joined) -> {
                val now = System.currentTimeMillis()
                if (now - lastTargetAlertAt >= 60_000L) {
                    lastTargetAlertAt = now
                    alert("Life Points: target value detected: " + findTarget(joined))
                }
            }
        }
    }

    private fun findAndClickGoogle(
        root: AccessibilityNodeInfo,
        screenText: String
    ): Boolean {
        if (googleClickInProgress) return true

        val googleNodes = ArrayList<AccessibilityNodeInfo>()
        collectGoogleNodes(root, googleNodes)

        val loginContext = screenText.contains("sign in") ||
            screenText.contains("continue") ||
            screenText.contains("log in") ||
            screenText.contains("login")

        val candidate = googleNodes.firstOrNull { node ->
            node.isVisibleToUser &&
                (isGoogleLabel(node) && (loginContext || node.isClickable))
        }

        googleNodes.filter { it !== candidate }.forEach { it.recycle() }
        if (candidate == null) return false

        val clickable = findClickableAncestor(candidate)
        val clicked = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true

        if (clickable != null && clickable !== candidate) clickable.recycle()
        candidate.recycle()

        if (!clicked) return false

        googleClickInProgress = true
        // Life Points needs time to redirect to the already-authenticated dashboard.
        handler.postDelayed({
            googleClickInProgress = false
            if (enabled) requestScan()
        }, 15_000L)
        return true
    }

    private fun collectGoogleNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (isGoogleLabel(node)) out.add(node)
        else {
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectGoogleNodes(child, out)
                    if (child !in out) child.recycle()
                }
            }
        }
    }

    private fun isGoogleLabel(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
        val desc = node.contentDescription?.toString()?.trim()?.lowercase(Locale.US).orEmpty()
        return text == "google" ||
            desc == "google" ||
            text.contains("sign in with google") ||
            desc.contains("sign in with google") ||
            text.contains("continue with google") ||
            desc.contains("continue with google")
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var parent = node.parent
        repeat(4) {
            if (parent == null) return null
            if (parent.isClickable) return parent
            parent = parent.parent
        }
        return null
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

    private fun alert(message: String) {
        MonitorForegroundService.alertNow(this, message)
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
