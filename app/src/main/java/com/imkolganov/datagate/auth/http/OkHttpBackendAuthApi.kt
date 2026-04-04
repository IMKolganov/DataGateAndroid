
package com.imkolganov.datagate.auth.http

import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.json.formatHttpErrorDetail
import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import com.imkolganov.datagate.model.auth.GoogleLoginResponseDto
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import com.imkolganov.datagate.model.auth.RefreshResponseDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OkHttpBackendAuthApi(
    private val http: OkHttpClient,
    private val baseUrl: String
) : BackendAuthApi {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")

    override suspend fun googleLogin(request: GoogleLoginRequestDto): GoogleLoginResponseDto {
        val url = joinUrl(baseUrl, ApiConfig.GOOGLE_LOGIN_PATH)

        val bodyJson = JSONObject()
            .put("idToken", request.idToken)
            .toString()

        val req = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val resp = http.newCall(req).execute()
        resp.use { r ->
            val raw = r.body.string()
            if (r.code !in 200..299) {
                throw IOException(formatHttpErrorDetail("google-login failed", r.code, raw.orEmpty()))
            }

            val obj = JSONObject(raw)
            val data = obj.getJSONObject("data")

            val token = data.getString("token")
            val expirationIso = data.getString("expiration")
            val refreshToken = data.optStringOrNull("refreshToken")
            val refreshExpirationIso = data.optStringOrNull("refreshExpiration")

            return GoogleLoginResponseDto(
                token = token,
                expiration = expirationIso,
                refreshToken = refreshToken,
                refreshExpiration = refreshExpirationIso
            )
        }
    }

    override suspend fun refresh(request: RefreshRequestDto): RefreshResponseDto {
        val url = joinUrl(baseUrl, ApiConfig.REFRESH_PATH)

        val bodyJson = JSONObject()
            .put("refreshToken", request.refreshToken)
            .put("deviceId", request.deviceId)
            .put("userAgent", request.userAgent)
            .toString()

        val req = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val resp = http.newCall(req).execute()
        resp.use { r ->
            val raw = r.body.string()
            if (r.code !in 200..299) {
                throw IOException(formatHttpErrorDetail("refresh failed", r.code, raw.orEmpty()))
            }

            val obj = JSONObject(raw)
            val data = obj.getJSONObject("data")

            val token = data.getString("token")
            val expirationIso = data.getString("expiration")
            val refreshToken = data.optStringOrNull("refreshToken")
            val refreshExpirationIso = data.optStringOrNull("refreshExpiration")

            return RefreshResponseDto(
                token = token,
                expiration = expirationIso,
                refreshToken = refreshToken,
                refreshExpiration = refreshExpirationIso
            )
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key, "").trim()
        return v.takeIf { it.isNotEmpty() }
    }

    private fun parseIsoToEpochSeconds(iso: String): Long? {
        val millis = parseIsoToMillis(iso) ?: return null
        return millis / 1000L
    }

    private fun parseIsoToMillis(iso: String): Long? {
        val normalized = iso.trim().replace("+00:00", "Z")
        val fixed = normalizeFractionToMillis(normalized)

        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )

        for (p in patterns) {
            try {
                val df = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = utcTz
                }
                val d = df.parse(fixed)
                if (d != null) return d.time
            } catch (_: Throwable) {
                // try next
            }
        }
        return null
    }

    private fun normalizeFractionToMillis(input: String): String {
        val dot = input.indexOf('.')
        if (dot < 0) return input

        val tzIndex = run {
            val z = input.indexOf('Z', startIndex = dot)
            if (z >= 0) return@run z
            val plus = input.indexOf('+', startIndex = dot)
            if (plus >= 0) return@run plus
            val minus = input.indexOf('-', startIndex = dot + 1)
            if (minus >= 0) return@run minus
            input.length
        }

        val fraction = input.substring(dot + 1, tzIndex)
        val ms = when {
            fraction.length >= 3 -> fraction.substring(0, 3)
            fraction.isEmpty() -> "000"
            fraction.length == 1 -> fraction + "00"
            else -> fraction + "0"
        }

        return input.substring(0, dot) + "." + ms + input.substring(tzIndex)
    }

    private fun joinUrl(base: String, path: String): String {
        val b = if (base.endsWith("/")) base.dropLast(1) else base
        val p = if (path.startsWith("/")) path.drop(1) else path
        return "$b/$p"
    }
}
