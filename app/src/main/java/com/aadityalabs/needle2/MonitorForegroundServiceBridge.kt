package com.aadityalabs.needle2

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MonitorForegroundServiceBridge {
    fun alert(context: Context, message: String) {
        val intent = Intent(context, MonitorForegroundService::class.java)
            .setAction("com.aadityalabs.needle2.ALERT")
            .putExtra("message", message)
        ContextCompat.startForegroundService(context, intent)
    }
}
