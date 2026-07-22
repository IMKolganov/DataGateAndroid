package com.imkolganov.datagate.vpn

/**
 * OpenVPN core [ClientAPI_LogInfo] has no severity field — only free-form text.
 * Persisting every line into the VPN debug file balloons to the 8 MB rotation cap.
 * Keep logcat for all lines; file only WARN/ERROR-ish text (plus a small rate limit).
 */
internal object OpenVpnCoreLogFilter {
    private const val RATE_LIMIT_WINDOW_MS = 1_000L
    private const val RATE_LIMIT_MAX_PER_WINDOW = 8

    @Volatile
    private var windowStartMs = 0L

    @Volatile
    private var windowCount = 0

    private val severityRegex = Regex(
        pattern = """(?i)(?:^|\b)(?:ERROR|WARN(?:ING)?|FATAL|CRITICAL)\b""",
    )

    fun shouldPersistToDebugFile(text: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (!severityRegex.containsMatchIn(trimmed)) return false
        return allowUnderRateLimit(nowMs)
    }

    @Synchronized
    private fun allowUnderRateLimit(nowMs: Long): Boolean {
        if (nowMs - windowStartMs >= RATE_LIMIT_WINDOW_MS) {
            windowStartMs = nowMs
            windowCount = 0
        }
        if (windowCount >= RATE_LIMIT_MAX_PER_WINDOW) return false
        windowCount++
        return true
    }

    /** Test hook — resets rate-limit state. */
    @Synchronized
    internal fun resetRateLimitForTests() {
        windowStartMs = 0L
        windowCount = 0
    }
}
