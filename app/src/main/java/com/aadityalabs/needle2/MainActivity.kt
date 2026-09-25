package com.aadityalabs.needle2

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Needle 2"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Life Points visual monitor\n\nNeedle 2 observes accessible screen text and alerts you. It does not click buttons, answer surveys, submit anything, or collect rewards.\n\nEnable Accessibility, then start the monitor."
            textSize = 16f
            setPadding(0, 24, 0, 24)
        })
        root.addView(Button(this).apply {
            text = "Enable Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = "Start Monitor"
            setOnClickListener { MonitorController.start(this@MainActivity) }
        })
        root.addView(Button(this).apply {
            text = "Stop Monitor"
            setOnClickListener { MonitorController.stop(this@MainActivity) }
        })
        setContentView(root)
    }
}
