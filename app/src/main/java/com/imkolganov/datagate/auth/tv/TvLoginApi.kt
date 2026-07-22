package com.imkolganov.datagate.auth.tv

import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.configs.AuthConfig
import com.imkolganov.datagate.json.formatHttpErrorDetail
import com.imkolganov.datagate.json.optDataObject
import com.imkolganov.datagate.json.optStringOrNull
import com.imkolganov.datagate.json.parseBackendApiEnvelopeOrThrow
import com.imkolganov.datagate.model.auth.CreateTvLoginSessionResponse
import com.imkolganov.datagate.model.auth.TvLoginSessionPollResponse
import executeSuspending
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

class TvLoginApi(
    private val http: OkHttpClient,
    private val baseUrl: String = AuthConfig.BACKEND_BASE_URL,
) {

    suspend fun createSession(
        deviceName: String?,
        client: String = TvDeviceInfo.CLIENT_ANDROID_TV,
    ): CreateTvLoginSessionResponse {
        val url = joinUrl(baseUrl, ApiConfig.TV_LOGIN_SESSION_PATH)
        val body = JSONObject()
        if (!deviceName.isNullOrBlank()) {
            body.put("deviceName", deviceName.trim())
        }
        if (client.isNotBlank()) {
            body.put("client", client.trim())
        }
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        http.executeSuspending(req).use { resp ->
            val code = resp.code
            val raw = resp.body.string().orEmpty()
            if (code !in 200..299) {
                throw IOException(formatHttpErrorDetail("tv-login-create", code, raw))
            }
            val root = parseBackendApiEnvelopeOrThrow("tv-login-create", code, raw)
            val data = root.optDataObject()
                ?: throw IOException("tv-login-create: missing data in response")
            return parseCreateResponse(data)
        }
    }

    suspend fun getSession(sessionId: String): TvLoginSessionPollResponse {
        val id = sessionId.trim()
        if (id.isEmpty()) throw IOException("tv-login-poll: empty sessionId")
        val url = joinUrl(baseUrl, ApiConfig.TV_LOGIN_SESSION_BY_ID_PREFIX + id)
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        http.executeSuspending(req).use { resp ->
            val code = resp.code
            val raw = resp.body.string().orEmpty()
            if (code !in 200..299) {
                throw IOException(formatHttpErrorDetail("tv-login-poll", code, raw))
            }
            val root = parseBackendApiEnvelopeOrThrow("tv-login-poll", code, raw)
            val data = root.optDataObject()
                ?: throw IOException("tv-login-poll: missing data in response")
            return parsePollResponse(data)
        }
    }

    internal fun parseCreateResponse(data: JSONObject): CreateTvLoginSessionResponse {
        val sessionId = data.optStringOrNull("sessionId")
            ?: data.optStringOrNull("SessionId")
            ?: throw IOException("tv-login-create: missing sessionId")
        val userCode = data.optStringOrNull("userCode")
            ?: data.optStringOrNull("UserCode")
            ?: throw IOException("tv-login-create: missing userCode")
        val qrPayload = data.optStringOrNull("qrPayload")
            ?: data.optStringOrNull("QrPayload")
            ?: throw IOException("tv-login-create: missing qrPayload")
        val expiresAt = data.optStringOrNull("expiresAt")
            ?: data.optStringOrNull("ExpiresAt")
            ?: throw IOException("tv-login-create: missing expiresAt")
        val pollInterval = when {
            data.has("pollIntervalSeconds") && !data.isNull("pollIntervalSeconds") ->
                data.getInt("pollIntervalSeconds")
            data.has("PollIntervalSeconds") && !data.isNull("PollIntervalSeconds") ->
                data.getInt("PollIntervalSeconds")
            else -> 2
        }.coerceAtLeast(1)
        val hubPath = data.optStringOrNull("signalRHubPath")
            ?: data.optStringOrNull("SignalRHubPath")
            ?: "/api/hubs/tv-login"
        return CreateTvLoginSessionResponse(
            sessionId = sessionId,
            userCode = userCode,
            verificationUrl = data.optStringOrNull("verificationUrl")
                ?: data.optStringOrNull("VerificationUrl"),
            qrPayload = qrPayload,
            expiresAt = expiresAt,
            pollIntervalSeconds = pollInterval,
            signalRHubPath = hubPath,
        )
    }

    internal fun parsePollResponse(data: JSONObject): TvLoginSessionPollResponse {
        val status = data.optStringOrNull("status")
            ?: data.optStringOrNull("Status")
            ?: throw IOException("tv-login-poll: missing status")
        val userId = when {
            data.has("userId") && !data.isNull("userId") -> data.getInt("userId")
            data.has("UserId") && !data.isNull("UserId") -> data.getInt("UserId")
            else -> 0
        }
        return TvLoginSessionPollResponse(
            status = status,
            expiresAt = data.optStringOrNull("expiresAt") ?: data.optStringOrNull("ExpiresAt"),
            userId = userId,
            displayName = data.optStringOrNull("displayName") ?: data.optStringOrNull("DisplayName"),
            email = data.optStringOrNull("email") ?: data.optStringOrNull("Email"),
            token = data.optStringOrNull("token") ?: data.optStringOrNull("Token"),
            expiration = data.optStringOrNull("expiration") ?: data.optStringOrNull("Expiration"),
            refreshToken = data.optStringOrNull("refreshToken")
                ?: data.optStringOrNull("RefreshToken"),
            refreshExpiration = data.optStringOrNull("refreshExpiration")
                ?: data.optStringOrNull("RefreshExpiration"),
            requiresTotp = data.optBoolean("requiresTotp", data.optBoolean("RequiresTotp", false)),
            loginChallengeId = data.optStringOrNull("loginChallengeId")
                ?: data.optStringOrNull("LoginChallengeId"),
            requiresTotpSetup = data.optBoolean(
                "requiresTotpSetup",
                data.optBoolean("RequiresTotpSetup", false),
            ),
        )
    }

    private fun joinUrl(base: String, path: String): String {
        val b = if (base.endsWith("/")) base.dropLast(1) else base
        val p = if (path.startsWith("/")) path.drop(1) else path
        return "$b/$p"
    }
}
