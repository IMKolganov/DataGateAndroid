package com.imkolganov.datagate.util

import android.content.res.Resources
import com.imkolganov.datagate.R
import java.util.Locale

/**
 * Picks the richest message from the throwable chain (often the root [java.io.IOException] with HTTP body).
 */
fun Throwable.deepMessageForApiError(): String {
    val parts = ArrayList<String>(4)
    var c: Throwable? = this
    var depth = 0
    while (c != null && depth++ < 8) {
        c.message?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        c = c.cause
    }
    return parts.maxByOrNull { it.length }.orEmpty()
}

/**
 * Turns raw API/HTTP exception text (often including nginx HTML bodies) into short, user-facing strings.
 */
fun Resources.userFriendlyApiError(raw: String?): String {
    if (raw.isNullOrBlank()) return getString(R.string.error_request_failed)

    val trimmed = raw.trim()
    val lower = trimmed.lowercase(Locale.US)

    val hasHtmlPayload =
        trimmed.startsWith("<") ||
            trimmed.contains("<html", ignoreCase = true) ||
            trimmed.contains("<!doctype", ignoreCase = true) ||
            trimmed.contains("</html>", ignoreCase = true) ||
            trimmed.contains("<head>", ignoreCase = true) ||
            (trimmed.contains("body=", ignoreCase = true) && trimmed.contains("<", ignoreCase = true))

    if (hasHtmlPayload) {
        return classifyHttpStatus(lower)
    }

    if (lower.contains("account picker fallback also failed")) {
        return getString(R.string.error_google_account_reauth_fallback_failed)
    }

    if (lower.contains("account reauth failed") || lower.contains("[16]")) {
        return getString(R.string.error_google_account_reauth_failed)
    }

    if (lower.contains("502") || lower.contains("bad gateway")) {
        return getString(R.string.error_http_bad_gateway)
    }
    if (lower.contains("503") && lower.contains("unavailable")) {
        return getString(R.string.error_http_service_unavailable)
    }
    if (lower.contains("504") || lower.contains("gateway timeout")) {
        return getString(R.string.error_http_gateway_timeout)
    }

    val maxLen = 400
    return if (trimmed.length > maxLen) trimmed.take(maxLen) + "…" else trimmed
}

private fun Resources.classifyHttpStatus(lower: String): String {
    return when {
        lower.contains("502") || lower.contains("bad gateway") ->
            getString(R.string.error_http_bad_gateway)
        lower.contains("503") || lower.contains("service unavailable") ->
            getString(R.string.error_http_service_unavailable)
        lower.contains("504") || lower.contains("gateway timeout") ->
            getString(R.string.error_http_gateway_timeout)
        else -> getString(R.string.error_http_proxy_html)
    }
}
