package com.imkolganov.datagate.json

import org.json.JSONException
import org.json.JSONObject

/**
 * Backend uses [OpenVPNGateMonitor.SharedModels.Responses.ApiResponse] for both success and errors:
 * `success` / `message` (camelCase or PascalCase in JSON).
 *
 * When HTTP status is not 2xx but the body is still this JSON, we surface [Message] instead of raw HTML or huge payloads.
 */
/** Extracts user-facing error from envelope or ASP.NET middleware JSON (`message` / `detail`). */
fun parseApiErrorMessage(body: String): String? {
    parseBackendApiMessageOrNull(body)?.let { return it }
    val trimmed = body.trim()
    if (trimmed.length < 2 || !trimmed.startsWith("{")) return null
    return try {
        val root = JSONObject(trimmed)
        root.optString("detail", "").trim().takeIf { it.isNotEmpty() }
            ?: root.optString("Detail", "").trim().takeIf { it.isNotEmpty() }
    } catch (_: JSONException) {
        null
    }
}

fun parseBackendApiMessageOrNull(body: String): String? {
    val trimmed = body.trim()
    if (trimmed.length < 2 || !trimmed.startsWith("{")) return null
    return try {
        val root = JSONObject(trimmed)
        val hasEnvelopeKey =
            root.has("success") || root.has("Success") ||
                root.has("message") || root.has("Message")
        if (!hasEnvelopeKey) return null
        root.optString("message", root.optString("Message", "")).trim().takeIf { it.isNotEmpty() }
    } catch (_: JSONException) {
        null
    }
}

/**
 * Builds an [IOException] message: prefers API [Message] when body parses as [ApiResponse]; otherwise keeps body for [userFriendlyApiError].
 */
fun formatHttpErrorDetail(operation: String, httpCode: Int, body: String): String {
    parseApiErrorMessage(body)?.let { msg ->
        return "$operation (HTTP $httpCode): $msg"
    }
    return "$operation: HTTP $httpCode, body=$body"
}

/** Backend [ApiResponse] wrapper: `success` / `message` / `data` (camelCase or PascalCase). */
fun parseBackendApiEnvelopeOrThrow(operation: String, httpCode: Int, body: String): JSONObject {
    val trimmed = body.trim()
    if (trimmed.length < 2 || !trimmed.startsWith("{")) {
        throw java.io.IOException(formatHttpErrorDetail(operation, httpCode, body))
    }
    val root = try {
        JSONObject(trimmed)
    } catch (_: JSONException) {
        throw java.io.IOException(formatHttpErrorDetail(operation, httpCode, body))
    }
    val ok = when {
        root.has("success") && !root.isNull("success") -> root.getBoolean("success")
        root.has("Success") && !root.isNull("Success") -> root.getBoolean("Success")
        else -> true
    }
    if (!ok) {
        val msg = root.optString("message", root.optString("Message", "Request failed")).trim()
            .ifEmpty { "Request failed" }
        throw java.io.IOException("$operation (HTTP $httpCode): $msg")
    }
    return root
}

fun JSONObject.optDataObject(): JSONObject? {
    if (has("data") && !isNull("data") && get("data") is JSONObject) {
        return getJSONObject("data")
    }
    if (has("Data") && !isNull("Data") && get("Data") is JSONObject) {
        return getJSONObject("Data")
    }
    return null
}

fun JSONObject.optDataString(): String? {
    val d = if (has("data") && !isNull("data")) get("data") else if (has("Data") && !isNull("Data")) get("Data") else null
    return when (d) {
        is String -> d.trim().takeIf { it.isNotEmpty() }
        else -> null
    }
}
