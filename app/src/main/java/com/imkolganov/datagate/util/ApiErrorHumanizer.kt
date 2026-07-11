package com.imkolganov.datagate.util

import android.content.res.Resources
import com.imkolganov.datagate.R
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

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
 * Turns a caught [Throwable] from a network/API call into a short, user-facing string.
 *
 * Classifies by walking the full cause chain and matching known network exception *types* first
 * (locale/OEM-independent, unlike message text), then falls back to the message-based
 * [userFriendlyApiError] below for anything else (HTTP error bodies, API-level messages, etc.).
 */
fun Resources.userFriendlyApiError(throwable: Throwable): String {
    classifyNetworkException(throwable)?.let { return getString(it) }
    return userFriendlyApiError(throwable.deepMessageForApiError())
}

/**
 * Walks the throwable's cause chain looking for well-known "the network misbehaved" exception
 * types, so transient issues (e.g. "Socket closed" while a VPN tunnel is coming up/down) get a
 * friendly, actionable message instead of raw Java exception text.
 */
private fun classifyNetworkException(throwable: Throwable): Int? {
    var c: Throwable? = throwable
    var depth = 0
    while (c != null && depth++ < 8) {
        when (c) {
            is UnknownHostException -> return R.string.error_network_no_internet
            is SocketTimeoutException -> return R.string.error_network_timeout
            is NoRouteToHostException,
            is PortUnreachableException,
            is ConnectException -> return R.string.error_network_no_internet
            is SSLException -> return R.string.error_network_tls
            is SocketException -> return R.string.error_network_interrupted
        }
        c = c.cause
    }
    return null
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

    classifyNetworkErrorMessage(lower)?.let { return getString(it) }

    val maxLen = 400
    return if (trimmed.length > maxLen) trimmed.take(maxLen) + "…" else trimmed
}

/**
 * Fallback for when only a message string (not the original [Throwable]) is available, e.g.
 * callers that pass [Throwable.message] directly instead of the exception itself. Matches common,
 * OS/OkHttp-level network error phrases that [classifyNetworkException] would otherwise catch by type.
 */
private fun classifyNetworkErrorMessage(lower: String): Int? {
    return when {
        lower.contains("unable to resolve host") ||
            lower.contains("no address associated with hostname") ||
            lower.contains("network is unreachable") ||
            lower.contains("no route to host") ->
            R.string.error_network_no_internet
        lower.contains("timed out") || lower.contains("timeout") ->
            R.string.error_network_timeout
        lower.contains("socket closed") ||
            lower.contains("connection reset") ||
            lower.contains("software caused connection abort") ||
            lower.contains("broken pipe") ||
            lower.contains("unexpected end of stream") ||
            lower.contains("stream was reset") ||
            lower.contains("connection abort") ||
            lower.contains("connection refused") ->
            R.string.error_network_interrupted
        lower.contains("ssl") || lower.contains("certificate") || lower.contains("trust anchor") ->
            R.string.error_network_tls
        else -> null
    }
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
