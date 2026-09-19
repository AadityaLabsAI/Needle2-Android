package com.aadityalabs.needle2

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private lateinit var wifi: WifiManager
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private var receiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        buildUi()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Needle2"
            textSize = 30f
        })
        root.addView(TextView(this).apply {
            text = "Android Wi‑Fi security auditing foundation"
            textSize = 15f
            setPadding(0, 4, 0, 18)
        })
        status = TextView(this).apply {
            text = "Checking device capabilities…"
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFF3F4F6.toInt())
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Scan nearby Wi‑Fi"
            setOnClickListener { startWifiScan() }
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateCapabilities()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    private fun updateCapabilities() {
        val locationEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF
        } catch (_: Exception) { true }
        status.text = buildString {
            append("Device capability check\n")
            append("Wi‑Fi hardware: ${if (wifi.isWifiEnabled) "available" else "off"}\n")
            append("Location services: ${if (locationEnabled) "enabled" else "disabled"}\n")
            append("Monitor mode / packet injection: not assumed on stock Android\n")
            append("External adapter backend: planned")
        }
    }

    private fun startWifiScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is required for Wi‑Fi scan results.", Toast.LENGTH_LONG).show()
            requestNeededPermissions()
            return
        }
        registerReceiverIfNeeded()
        val started = try { wifi.startScan() } catch (_: SecurityException) { false }
        if (!started) {
            Toast.makeText(this, "Wi‑Fi scan could not be started on this device.", Toast.LENGTH_LONG).show()
            return
        }
        status.text = "Scanning… Android may throttle repeated scans."
    }

    private fun registerReceiverIfNeeded() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { showResults() }
        }
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    private fun showResults() {
        val results: List<ScanResult> = try { wifi.scanResults } catch (_: SecurityException) { emptyList() }
        list.removeAllViews()
        val sorted = results.sortedWith(compareByDescending<ScanResult> { it.level }.thenBy { it.SSID })
        status.text = "Found ${sorted.size} Wi‑Fi networks"
        for (ap in sorted) {
            val band = when {
                ap.frequency in 2400..2500 -> "2.4 GHz"
                ap.frequency in 4900..5900 -> "5 GHz"
                else -> "${ap.frequency} MHz"
            }
            val security = ap.capabilities.ifBlank { "Open / unknown" }
            list.addView(TextView(this).apply {
                text = buildString {
                    append(ap.SSID.ifBlank { "<hidden SSID>" })
                    append("\n${ap.BSSID}  •  $band  •  ${ap.level} dBm")
                    append("\n$security")
                }
                textSize = 15f
                setPadding(14, 14, 14, 14)
                setBackgroundColor(0xFFF9FAFB.toInt())
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 10) })
        }
    }

    override fun onDestroy() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        super.onDestroy()
    }
}
