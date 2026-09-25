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

        root.addView(TextView(this).apply {
            text = "Needle 2"
            textSize = 28f
        })

        root.addView(TextView(this).apply {
            text = """
                Life Points monitor

                • Checks at a genuinely random time between 8 and 10 minutes.
                • Opens Life Points when a check is due.
                • If the visible Life Points screen exposes a Google sign-in/continue control, Needle 2 can tap that navigation control once and wait about 15 seconds for the dashboard.
                • Watches the accessible screen for 40, 65, 90, or 100.
                • Plays a loud alarm and notification when a target is detected.
                • If “Sorry” appears, it leaves the app and continues monitoring.
                
                Needle 2 does not answer surveys, choose responses, submit surveys, or collect rewards.
                
                For automatic navigation, Android Accessibility access must be enabled.
            """.trimIndent()
            textSize = 16f
            setPadding(0, 24, 0, 24)
        })

        root.addView(Button(this).apply {
            text = "Enable Accessibility"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(Button(this).apply {
            text = "Start Monitor"
            setOnClickListener {
                MonitorController.start(this@MainActivity)
            }
        })

        root.addView(Button(this).apply {
            text = "Stop Monitor"
            setOnClickListener {
                MonitorController.stop(this@MainActivity)
            }
        })

        setContentView(root)
    }
}
