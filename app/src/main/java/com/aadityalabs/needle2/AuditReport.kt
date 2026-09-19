package com.aadityalabs.needle2

import android.net.wifi.ScanResult
import java.text.SimpleDateFormat
import java.util.*

data class AuditSummary(
    val generatedAt: String,
    val networks: Int,
    val protected: Int,
    val open: Int,
    val legacy: Int
)

object AuditReport {
    fun summary(results: List<ScanResult>): AuditSummary {
        var open = 0
        var legacy = 0
        var protected = 0
        for (ap in results) {
            val c = ap.capabilities.uppercase(Locale.US)
            when {
                c.isBlank() || (c.contains("ESS") && !c.contains("WPA") && !c.contains("WEP")) -> open++
                c.contains("WEP") -> legacy++
                else -> protected++
            }
        }
        return AuditSummary(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()),
            results.size, protected, open, legacy
        )
    }

    fun json(results: List<ScanResult>): String {
        val s = summary(results)
        return buildString {
            append("{\n")
            append("  \"generated_at\": \"${s.generatedAt}\",\n")
            append("  \"networks\": ${s.networks},\n")
            append("  \"protected\": ${s.protected},\n")
            append("  \"open\": ${s.open},\n")
            append("  \"legacy\": ${s.legacy}\n")
            append("}\n")
        }
    }
}
