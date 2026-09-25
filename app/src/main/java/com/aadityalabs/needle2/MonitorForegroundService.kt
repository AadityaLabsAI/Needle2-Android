package com.aadityalabs.needle2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
    companion object {\n        private var instance: MonitorForegroundService? = null
        const val CHANNEL_ID = "needle_monitor"
        const val NOTIFICATION_ID = 2001
        const val ALERT_ID = 3000
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    fun scheduleNextCycle() { instance?.scheduleNextWindow() }\n\n    override fun onCreate() {\n        instance = this
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildStatus("Monitor ready"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            MonitorController.isStopAction(intent?.action) -> {
                running = false
                handler.removeCallbacksAndMessages(null)
                MonitorAccessibilityService.requestStop()
                stopSelf()
            }
            MonitorController.isStartAction(intent?.action) || intent?.action == null -> {
                startMonitoring()
            }
            intent?.action == "com.aadityalabs.needle2.ALERT" -> {
                alert(intent.getStringExtra("message") ?: "Life Points alert")
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (running) return
        running = true
        MonitorAccessibilityService.requestStart()
        scheduleNextWindow()
    }

    private fun scheduleNextWindow() {
        if (!running) return
        val delayMs = Random.nextLong(8 * 60 * 1000L, 10 * 60 * 1000L + 1)
        val minutes = delayMs / 60000
        val seconds = (delayMs / 1000) % 60
        updateStatus(String.format("Next screen check in %dm %02ds", minutes, seconds))
        handler.postDelayed({
            if (running) {
                MonitorAccessibilityService.requestScan()
                scheduleNextWindow()
            }
        }, delayMs)
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
                .build()
        )
        val ringtone = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        )
        ringtone?.let {
            it.play()
            handler.postDelayed({ if (it.isPlaying) it.stop() }, 12000)
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
                description = "Monitoring status and urgent Life Points alerts"
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(
                    alarmUri,
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

    override fun onDestroy() {\n        instance = null\n        handler.removeCallbacksAndMessages(null)\n        super.onDestroy()\n    }\n\n    override fun onBind(intent: Intent?): IBinder? = null
}
