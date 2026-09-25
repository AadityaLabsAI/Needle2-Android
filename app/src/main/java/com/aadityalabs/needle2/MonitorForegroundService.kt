package com.aadityalabs.needle2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class MonitorForegroundService : Service() {

    companion object {
        private var instance: MonitorForegroundService? = null

        private const val ACTION_ALERT = "com.aadityalabs.needle2.ALERT"
        private const val EXTRA_MESSAGE = "message"
        private const val LIFE_POINTS_PACKAGE = "com.kantarprofiles.lifepoints"

        const val CHANNEL_ID = "needle_monitor"
        const val NOTIFICATION_ID = 2001
        const val ALERT_ID = 3000

        fun alertNow(context: Context, message: String) {
            instance?.alert(message) ?: run {
                val intent = Intent(context, MonitorForegroundService::class.java)
                    .setAction(ACTION_ALERT)
                    .putExtra(EXTRA_MESSAGE, message)
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        fun scheduleNextCycle() {
            instance?.scheduleNextWindow()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var nextWindowScheduled = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        startForeground(NOTIFICATION_ID, buildStatus("Monitor ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MonitorController.ACTION_STOP -> stopMonitoring()
            MonitorController.ACTION_START, null -> startMonitoring()
            ACTION_ALERT -> alert(intent.getStringExtra(EXTRA_MESSAGE) ?: "Life Points alert")
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (running) return
        running = true
        MonitorAccessibilityService.requestStart()
        scheduleNextWindow()
    }

    private fun stopMonitoring() {
        running = false
        nextWindowScheduled = false
        handler.removeCallbacksAndMessages(null)
        MonitorAccessibilityService.requestStop()
        stopSelf()
    }

    private fun scheduleNextWindow() {
        if (!running || nextWindowScheduled) return

        nextWindowScheduled = true
        val delayMs = Random.nextLong(8 * 60 * 1000L, 10 * 60 * 1000L + 1L)
        val minutes = delayMs / 60000L
        val seconds = (delayMs / 1000L) % 60L
        updateStatus(String.format("Next check in %dm %02ds", minutes, seconds))

        handler.postDelayed({
            nextWindowScheduled = false
            if (!running) return@postDelayed

            launchLifePointsIfNeeded()
            handler.postDelayed({
                if (running) MonitorAccessibilityService.requestScan()
            }, 2500L)

            scheduleNextWindow()
        }, delayMs)
    }

    private fun launchLifePointsIfNeeded() {
        if (MonitorAccessibilityService.isLifePointsForeground()) return

        val launchIntent = packageManager.getLaunchIntentForPackage(LIFE_POINTS_PACKAGE)
        if (launchIntent == null) {
            alert("Life Points app was not found on this phone.")
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(launchIntent)
    }

    fun alert(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            ALERT_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Needle 2 alert")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
        )

        RingtoneManager.getRingtone(
            this,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        )?.let { ringtone ->
            ringtone.play()
            handler.postDelayed({
                if (ringtone.isPlaying) ringtone.stop()
            }, 12_000L)
        }
    }

    private fun updateStatus(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildStatus(text))
    }

    private fun buildStatus(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Needle 2 monitoring")
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Needle 2 monitoring",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Needle 2 monitoring status and urgent alerts"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        instance = null
        running = false
        nextWindowScheduled = false
        handler.removeCallbacksAndMessages(null)
        MonitorAccessibilityService.requestStop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
