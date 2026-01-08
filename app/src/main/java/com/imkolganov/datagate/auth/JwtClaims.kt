package com.imkolganov.datagate.auth

import android.util.Base64
import org.json.JSONObject

data class JwtClaims(
    val userId: String?,
    val externalId: String?,
    val role: String?,
    val displayName: String?,
    val email: String?
)

object JwtClaimsReader {

    fun read(token: String?): JwtClaims {
        if (token.isNullOrBlank()) {
            return JwtClaims(null, null, null, null, null)
        }

        val parts = token.split(".")
        if (parts.size < 2) {
            return JwtClaims(null, null, null, null, null)
        }

        val payloadJson = try {
            val payloadBytes = base64UrlDecode(parts[1])
            String(payloadBytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            return JwtClaims(null, null, null, null, null)
        }

        val obj = try {
            JSONObject(payloadJson)
        } catch (_: Throwable) {
            return JwtClaims(null, null, null, null, null)
        }

        val userId = firstNonBlank(
            obj.optStringOrNull("nameid"),
            obj.optStringOrNull("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier")
        )

        val role = firstNonBlank(
            obj.optStringOrNull("role"),
            obj.optStringOrNull("http://schemas.microsoft.com/ws/2008/06/identity/claims/role")
        )

        val displayName = firstNonBlank(
            obj.optStringOrNull("displayName"),
            obj.optStringOrNull("unique_name"),
            obj.optStringOrNull("name")
        )

        val externalId = firstNonBlank(
            obj.optStringOrNull("externalId"),
            obj.optStringOrNull("sub")
        )

        val email = obj.optStringOrNull("email")

        return JwtClaims(
            userId = userId,
            externalId = externalId,
            role = role,
            displayName = displayName,
            email = email
        )
    }

    private fun base64UrlDecode(input: String): ByteArray {
        var normalized = input.replace('-', '+').replace('_', '/')
        val pad = normalized.length % 4
        if (pad != 0) {
            normalized += "=".repeat(4 - pad)
        }
        return Base64.decode(normalized, Base64.NO_WRAP)
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key, "").trim()
        return v.takeIf { it.isNotEmpty() }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (v in values) {
            if (!v.isNullOrBlank()) return v
        }
        return null
    }
}