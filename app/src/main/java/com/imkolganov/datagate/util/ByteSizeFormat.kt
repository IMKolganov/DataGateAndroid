package com.imkolganov.datagate.util

import java.util.Locale
import kotlin.math.abs

/**
 * Human-readable size (B, KB, MB, GB, TB) using binary units (1024).
 */
fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "—"
    if (bytes == 0L) return "0 B"
    val negative = bytes < 0
    var v = abs(bytes).toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var u = 0
    while (v >= 1024.0 && u < units.lastIndex) {
        v /= 1024.0
        u++
    }
    val decimals = when (u) {
        0 -> 0
        1 -> if (v >= 100) 0 else 1
        else -> if (v >= 100) 1 else 2
    }
    val num = String.format(Locale.US, "%.${decimals}f", v)
    val sign = if (negative) "−" else ""
    return "$sign$num ${units[u]}"
}
