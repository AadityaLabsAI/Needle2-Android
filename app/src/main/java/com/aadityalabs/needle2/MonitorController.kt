package com.aadityalabs.needle2

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MonitorController {
    const val ACTION_START = "com.aadityalabs.needle2.START"
    const val ACTION_STOP = "com.aadityalabs.needle2.STOP"

    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, MonitorForegroundService::class.java).setAction(ACTION_START)
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, MonitorForegroundService::class.java).setAction(ACTION_STOP)
        )
    }

    fun isStartAction(action: String?) = action == ACTION_START
    fun isStopAction(action: String?) = action == ACTION_STOP
}
