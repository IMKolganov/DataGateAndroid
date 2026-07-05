package com.imkolganov.datagate.freetier

import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.configs.AuthConfig
import com.imkolganov.datagate.json.formatHttpErrorDetail
import com.imkolganov.datagate.json.optDataObject
import com.imkolganov.datagate.json.optIntOrNull
import com.imkolganov.datagate.json.optStringOrNull
import com.imkolganov.datagate.json.parseBackendApiEnvelopeOrThrow
import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.freetier.FreeTierAccessStatusResponse
import com.imkolganov.datagate.model.freetier.RequestTelegramAccountLinkCodeResponse
import executeSuspending
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

class FreeTierApi(
    private val http: OkHttpClient,
    private val baseUrl: String = AuthConfig.BACKEND_BASE_URL
) {

    suspend fun getAccessStatus(): ApiResponse<FreeTierAccessStatusResponse> {
        val url = baseUrl.trimEnd('/') + ApiConfig.FREE_TIER_ACCESS_STATUS_PATH
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        http.executeSuspending(req).use { resp ->
            val code = resp.code
            val body = resp.body.string().orEmpty()
            if (code !in 200..299) {
                throw IOException(formatHttpErrorDetail("Free tier access status failed", code, body))
            }
            return parseAccessStatusResponse(body)
        }
    }

    suspend fun requestAccountLinkCode(): ApiResponse<RequestTelegramAccountLinkCodeResponse> {
        val url = baseUrl.trimEnd('/') + ApiConfig.TELEGRAM_REQUEST_ACCOUNT_LINK_CODE_PATH
        val req = Request.Builder()
            .url(url)
            .post("{}".toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()

        http.executeSuspending(req).use { resp ->
            val code = resp.code
            val body = resp.body.string().orEmpty()
            if (code !in 200..299) {
                throw IOException(formatHttpErrorDetail("Telegram account link code failed", code, body))
            }
            return parseAccountLinkCodeResponse(body)
        }
    }

    internal fun parseAccessStatusResponse(body: String): ApiResponse<FreeTierAccessStatusResponse> {
        val root = JSONObject(body)
        val success = root.optBoolean("success", root.optBoolean("Success", false))
        val message = root.optString("message", root.optString("Message", "")).trim().ifEmpty { null }
        if (!success) {
            return ApiResponse(success = false, message = message, data = null)
        }
        val dataObj = root.optDataObject()
        val data = dataObj?.let { parseAccessStatus(it) }
        return ApiResponse(success = success, message = message, data = data)
    }

    internal fun parseAccountLinkCodeResponse(body: String): ApiResponse<RequestTelegramAccountLinkCodeResponse> {
        val root = parseBackendApiEnvelopeOrThrow("Telegram account link code", 200, body)
        val message = root.optString("message", root.optString("Message", "")).trim().ifEmpty { null }
        val dataObj = root.optDataObject()
        val data = dataObj?.let { parseAccountLinkCode(it) }
        return ApiResponse(success = true, message = message, data = data)
    }

    private fun parseAccessStatus(o: JSONObject): FreeTierAccessStatusResponse {
        return FreeTierAccessStatusResponse(
            isApplicable = o.bool("isApplicable", "IsApplicable"),
            isCompliant = o.bool("isCompliant", "IsCompliant"),
            isMergedAccount = o.bool("isMergedAccount", "IsMergedAccount"),
            isChannelSubscribed = o.bool("isChannelSubscribed", "IsChannelSubscribed"),
            isGracePeriod = o.bool("isGracePeriod", "IsGracePeriod"),
            isLinkedToTelegram = o.bool("isLinkedToTelegram", "IsLinkedToTelegram"),
            canRequestAccountLinkCode = o.bool("canRequestAccountLinkCode", "CanRequestAccountLinkCode"),
            activePlanName = o.optStringOrNull("activePlanName") ?: o.optStringOrNull("ActivePlanName"),
            requiredChannel = o.optStringOrNull("requiredChannel") ?: o.optStringOrNull("RequiredChannel"),
        )
    }

    private fun parseAccountLinkCode(o: JSONObject): RequestTelegramAccountLinkCodeResponse {
        return RequestTelegramAccountLinkCodeResponse(
            code = o.optString("code", o.optString("Code", "")),
            expiresInSeconds = o.optIntOrNull("expiresInSeconds")
                ?: o.optIntOrNull("ExpiresInSeconds")
                ?: 0
        )
    }

    private fun JSONObject.bool(camel: String, pascal: String, default: Boolean = false): Boolean {
        if (has(camel) && !isNull(camel)) return getBoolean(camel)
        if (has(pascal) && !isNull(pascal)) return getBoolean(pascal)
        return default
    }
}
