package com.aadityalabs.needle2

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var wifi: WifiManager
    private lateinit var usb: UsbManager
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private val aps = mutableListOf<ScanResult>()
    private lateinit var usbAdapters: UsbAdapterManager
    private val bg = 0xFF0B0F17.toInt()
    private val panel = 0xFF141A24.toInt()
    private val textColor = 0xFFF5F7FA.toInt()
    private val muted = 0xFF9AA4B2.toInt()
    private val accent = 0xFF4F7CFF.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        usb = applicationContext.getSystemService(USB_SERVICE) as UsbManager
        usbAdapters = UsbAdapterManager(this)
        buildUi()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(24, 22, 24, 16)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "◉"
            textSize = 30f
            setTextColor(accent)
            setPadding(0, 0, 14, 0)
        })
        header.addView(TextView(this).apply {
            text = "Needle2"
            textSize = 28f
            setTextColor(textColor)
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Wi‑Fi security audit • Android edition"
            textSize = 14f
            setTextColor(muted)
            setPadding(0, 2, 0, 20)
        })
        status = card("Checking device capabilities…")
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 12) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("SCAN") { startWifiScan() }, LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(0, 0, 8, 0) })
        actions.addView(actionButton("CSV") { exportReport("text/csv", "csv") }, LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(8, 0, 0, 0) })
        root.addView(actions)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 24)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateCapabilities()
    }

    private fun card(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(textColor)
        setPadding(18, 16, 18, 16)
        setBackgroundColor(panel)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        setOnClickListener { action() }
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
        val wifiAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
        val usbHost = packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        status.text = buildString {
            append("DEVICE CAPABILITIES\n\n")
            append("Wi‑Fi hardware       ${if (wifiAvailable) "AVAILABLE" else "NOT FOUND"}\n")
            append("Wi‑Fi service        ${if (wifi.isWifiEnabled) "ON" else "OFF"}\n")
            append("Location services    ${if (locationEnabled) "ON" else "OFF"}\n")
            append("USB host             ${if (usbHost) "AVAILABLE" else "NOT AVAILABLE"}\n")
            append("USB devices          ${usb.deviceList.size}\n")
            val adapters = usbAdapters.listAdapters()
            if (adapters.isNotEmpty()) {
                append("\nUSB WI-FI ADAPTERS\n")
                adapters.take(3).forEach { adapter ->
                    append("${adapter.deviceName}  (${adapter.vendorId}:${adapter.productId})\n")
                }
            }
            append("Monitor / injection  HARDWARE + OS DEPENDENT\n")
            append("Passive scan         READY")
        }
    }

    private fun startWifiScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestNeededPermissions()
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try { unregisterReceiver(this) } catch (_: Exception) {}
                showResults()
            }
        }
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        val started = try { wifi.startScan() } catch (_: SecurityException) { false }
        status.text = if (started) "SCANNING…\n\nAndroid may throttle repeated scans." else "SCAN FAILED\n\nCheck Wi‑Fi, permissions and location services."
    }

    private fun showResults() {
        aps.clear()
        aps.addAll(try { wifi.scanResults } catch (_: SecurityException) { emptyList() })
        list.removeAllViews()
        val sorted = aps.sortedWith(compareByDescending<ScanResult> { it.level }.thenBy { it.SSID })
        val audit = AuditReport.summary(sorted)
        status.text = "NETWORK INVENTORY\n\n${sorted.size} access points discovered\n" +
            "Protected: ${audit.protected}  •  Open: ${audit.open}  •  Legacy WEP: ${audit.legacy}"
        for (ap in sorted) {
            val band = when {
                ap.frequency in 2400..2500 -> "2.4 GHz"
                ap.frequency in 4900..5900 -> "5 GHz"
                else -> "${ap.frequency} MHz"
            }
            val security = ap.capabilities.ifBlank { "OPEN / UNKNOWN" }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 16, 18, 16)
                setBackgroundColor(panel)
            }
            item.addView(TextView(this).apply {
                text = ap.SSID.ifBlank { "<hidden SSID>" }
                textSize = 17f
                setTextColor(textColor)
            })
            item.addView(TextView(this).apply {
                text = "${ap.BSSID}  •  $band  •  ${ap.level} dBm\n$security"
                textSize = 12f
                setTextColor(muted)
                setPadding(0, 5, 0, 0)
            })
            list.addView(item, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 10) })
        }
        updateCapabilities()
    }

    private fun exportReport(type: String, extension: String) {
        if (aps.isEmpty()) {
            Toast.makeText(this, "Run a Wi‑Fi scan first.", Toast.LENGTH_SHORT).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = type
            putExtra(Intent.EXTRA_TITLE, "needle2-audit-$stamp.$extension")
        }, 200)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 200 || resultCode != RESULT_OK || data?.data == null) return
        val csv = buildString {
            append("ssid,bssid,frequency_mhz,signal_dbm,security\n")
            for (ap in aps) {
                append(listOf(ap.SSID, ap.BSSID, ap.frequency, ap.level, ap.capabilities).joinToString(",") { v ->
                    "\"" + v.toString().replace("\"", "\"\"") + "\""
                })
                append("\n")
            }
        }
        try {
            contentResolver.openOutputStream(data.data!!)?.use { it.write(csv.toByteArray()) }
            Toast.makeText(this, "Audit report exported.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
