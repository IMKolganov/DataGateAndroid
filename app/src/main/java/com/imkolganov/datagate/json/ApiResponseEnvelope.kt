package com.imkolganov.datagate.json

import org.json.JSONException
import org.json.JSONObject

/**
 * Backend uses [OpenVPNGateMonitor.SharedModels.Responses.ApiResponse] for both success and errors:
 * `success` / `message` (camelCase or PascalCase in JSON).
 *
 * When HTTP status is not 2xx but the body is still this JSON, we surface [Message] instead of raw HTML or huge payloads.
 */
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
    parseBackendApiMessageOrNull(body)?.let { msg ->
        return "$operation (HTTP $httpCode): $msg"
    }
    return "$operation: HTTP $httpCode, body=$body"
}
