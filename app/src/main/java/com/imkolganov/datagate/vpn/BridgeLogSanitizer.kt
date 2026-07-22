package com.imkolganov.datagate.vpn

/** Keeps diagnostic log tokens on a single line. */
internal object BridgeLogSanitizer {
    fun line(value: String?, maxLen: Int = 240): String {
        if (value.isNullOrEmpty()) return ""
        return value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .let { if (it.length <= maxLen) it else it.take(maxLen - 3) + "..." }
    }
}
