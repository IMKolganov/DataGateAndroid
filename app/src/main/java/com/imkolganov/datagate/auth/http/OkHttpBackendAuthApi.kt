
package com.imkolganov.datagate.auth.http

import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.json.formatHttpErrorDetail
import com.imkolganov.datagate.json.optDataObject
import com.imkolganov.datagate.json.optDataString
import com.imkolganov.datagate.json.parseBackendApiEnvelopeOrThrow
import com.imkolganov.datagate.model.auth.ConfirmEmailResultDto
import com.imkolganov.datagate.model.auth.GoogleLoginRequestDto
import com.imkolganov.datagate.model.auth.GoogleLoginResponseDto
import com.imkolganov.datagate.model.auth.LoginPasswordRequestDto
import com.imkolganov.datagate.model.auth.RefreshRequestDto
import com.imkolganov.datagate.model.auth.RefreshResponseDto
import com.imkolganov.datagate.model.auth.RegisterUserRequestDto
import com.imkolganov.datagate.model.auth.RegisterUserResponseDto
import com.imkolganov.datagate.model.auth.ResetPasswordResultDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
class OkHttpBackendAuthApi(
    private val http: OkHttpClient,
    private val baseUrl: String
) : BackendAuthApi {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

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
            return parseTokenBundle(data)
        }
    }

    override suspend fun register(request: RegisterUserRequestDto): RegisterUserResponseDto {
        val url = joinUrl(baseUrl, ApiConfig.REGISTER_PATH)
        val body = JSONObject()
            .put("displayName", request.displayName.trim())
            .put("login", request.login.trim())
            .put("password", request.password)
            .put("confirmPassword", request.confirmPassword)
        if (!request.email.isNullOrBlank()) {
            body.put("email", request.email.trim())
        }
        val raw = postJson(url, body.toString(), "register")
        val root = parseBackendApiEnvelopeOrThrow("register", 200, raw)
        val data = root.optDataObject()
            ?: throw IOException("register: missing data in response")
        val userId = when {
            data.has("userId") && !data.isNull("userId") -> data.getInt("userId")
            data.has("UserId") && !data.isNull("UserId") -> data.getInt("UserId")
            else -> throw IOException("register: missing userId in response")
        }
        val dashboard = when {
            data.has("hasDashboardAccess") && !data.isNull("hasDashboardAccess") ->
                data.getBoolean("hasDashboardAccess")
            data.has("HasDashboardAccess") && !data.isNull("HasDashboardAccess") ->
                data.getBoolean("HasDashboardAccess")
            else -> false
        }
        return RegisterUserResponseDto(
            userId = userId,
            displayName = data.optString("displayName", data.optString("DisplayName", "")),
            email = data.optStringOrNull("email") ?: data.optStringOrNull("Email"),
            hasDashboardAccess = dashboard
        )
    }

    override suspend fun loginWithPassword(request: LoginPasswordRequestDto): GoogleLoginResponseDto {
        val url = joinUrl(baseUrl, ApiConfig.LOGIN_PATH)
        val bodyJson = JSONObject()
            .put("login", request.login.trim())
            .put("password", request.password)
            .toString()
        val raw = postJson(url, bodyJson, "login")
        val root = parseBackendApiEnvelopeOrThrow("login", 200, raw)
        val data = root.optDataObject()
            ?: throw IOException("login: missing data in response")
        return parseTokenBundle(data)
    }

    override suspend fun requestEmailConfirmation(email: String): String {
        val url = joinUrl(baseUrl, ApiConfig.EMAIL_REQUEST_CONFIRMATION_PATH)
        val bodyJson = JSONObject()
            .put("email", email.trim())
            .toString()
        val raw = postJson(url, bodyJson, "request-email-confirmation")
        val root = parseBackendApiEnvelopeOrThrow("request-email-confirmation", 200, raw)
        return root.optDataString()
            ?: root.optString("message", root.optString("Message", "")).trim()
                .ifEmpty { "OK" }
    }

    override suspend fun confirmEmail(email: String, code: String): ConfirmEmailResultDto {
        val url = joinUrl(baseUrl, ApiConfig.EMAIL_CONFIRM_PATH)
        val bodyJson = JSONObject()
            .put("email", email.trim())
            .put("code", code.trim())
            .toString()
        val raw = postJson(url, bodyJson, "confirm-email")
        val root = parseBackendApiEnvelopeOrThrow("confirm-email", 200, raw)
        val data = root.optDataObject()
            ?: throw IOException("confirm-email: missing data in response")
        val innerOk = data.optBoolean("success", data.optBoolean("Success", false))
        val innerMsg = data.optString("message", data.optString("Message", "")).trim()
        return ConfirmEmailResultDto(success = innerOk, message = innerMsg)
    }

    override suspend fun forgotPassword(loginOrEmail: String): String {
        val url = joinUrl(baseUrl, ApiConfig.FORGOT_PASSWORD_PATH)
        val bodyJson = JSONObject()
            .put("loginOrEmail", loginOrEmail.trim())
            .toString()
        val raw = postJson(url, bodyJson, "forgot-password")
        val root = parseBackendApiEnvelopeOrThrow("forgot-password", 200, raw)
        val data = root.optDataObject()
            ?: throw IOException("forgot-password: missing data in response")
        return data.optString("message", data.optString("Message", "")).trim()
            .ifEmpty { "OK" }
    }

    override suspend fun resetPassword(code: String, newPassword: String, confirmPassword: String): ResetPasswordResultDto {
        val url = joinUrl(baseUrl, ApiConfig.RESET_PASSWORD_PATH)
        val bodyJson = JSONObject()
            .put("code", code.trim())
            .put("newPassword", newPassword)
            .put("confirmPassword", confirmPassword)
            .toString()
        val raw = postJson(url, bodyJson, "reset-password")
        val root = parseBackendApiEnvelopeOrThrow("reset-password", 200, raw)
        val data = root.optDataObject()
            ?: throw IOException("reset-password: missing data in response")
        val innerOk = data.optBoolean("success", data.optBoolean("Success", false))
        val innerMsg = data.optString("message", data.optString("Message", "")).trim()
        return ResetPasswordResultDto(success = innerOk, message = innerMsg)
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
            val bundle = parseTokenBundle(data)
            return RefreshResponseDto(
                token = bundle.token,
                expiration = bundle.expiration,
                refreshToken = bundle.refreshToken,
                refreshExpiration = bundle.refreshExpiration
            )
        }
    }

    private fun postJson(url: String, body: String, opLabel: String): String {
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        val resp = http.newCall(req).execute()
        resp.use { r ->
            val raw = r.body.string().orEmpty()
            if (r.code !in 200..299) {
                throw IOException(formatHttpErrorDetail(opLabel, r.code, raw))
            }
            return raw
        }
    }

    private fun parseTokenBundle(data: JSONObject): GoogleLoginResponseDto {
        val token = data.optString("token", data.optString("Token", "")).trim()
            .ifEmpty { throw IOException("auth response: missing token") }
        val expirationIso = data.optString("expiration", data.optString("Expiration", "")).trim()
            .ifEmpty { throw IOException("auth response: missing expiration") }
        val refreshToken = data.optStringOrNull("refreshToken") ?: data.optStringOrNull("RefreshToken")
        val refreshExpirationIso =
            data.optStringOrNull("refreshExpiration") ?: data.optStringOrNull("RefreshExpiration")
        return GoogleLoginResponseDto(
            token = token,
            expiration = expirationIso,
            refreshToken = refreshToken,
            refreshExpiration = refreshExpirationIso
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key, "").trim()
        return v.takeIf { it.isNotEmpty() }
    }

    private fun joinUrl(base: String, path: String): String {
        val b = if (base.endsWith("/")) base.dropLast(1) else base
        val p = if (path.startsWith("/")) path.drop(1) else path
        return "$b/$p"
    }
}
