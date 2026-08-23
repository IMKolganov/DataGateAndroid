package com.imkolganov.datagate.quota

import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.configs.AuthConfig
import com.imkolganov.datagate.json.formatHttpErrorDetail
import com.imkolganov.datagate.json.optIntOrNull
import com.imkolganov.datagate.json.optLongOrNull
import com.imkolganov.datagate.json.optStringOrNull
import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.quota.QuotaPlanDto
import com.imkolganov.datagate.model.quota.UserQuotaPlanDto
import executeSuspending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

class QuotaPlanApi(
    private val http: OkHttpClient,
    private val baseUrl: String = AuthConfig.BACKEND_BASE_URL
) {

    suspend fun getAllQuotaPlans(includeInactive: Boolean): ApiResponse<QuotaPlansPayload> {
        val url = baseUrl.trimEnd('/') + "/" + ApiConfig.API_QUOTA_PLANS_GET_ALL.trimStart('/')
        val bodyJson = JSONObject().apply { put("includeInactive", includeInactive) }.toString()
        val req = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .header("Accept", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            http.executeSuspending(req).use { resp ->
                val code = resp.code
                val body = resp.body.string().orEmpty()
                if (code !in 200..299) {
                    throw IOException(formatHttpErrorDetail("Quota plans failed", code, body))
                }
                parseQuotaPlansResponse(body)
            }
        }
    }

    suspend fun getUserQuotaPlansByUserId(userId: Int): ApiResponse<UserQuotaPlansByUserPayload> {
        val url = baseUrl.trimEnd('/') + "/" +
            ApiConfig.API_USER_QUOTA_PLANS_GET_BY_USER_ID_PREFIX.trimStart('/') + userId

        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            http.executeSuspending(req).use { resp ->
                val code = resp.code
                val body = resp.body.string().orEmpty()
                if (code !in 200..299) {
                    throw IOException(formatHttpErrorDetail("User quota plans failed", code, body))
                }
                parseUserQuotaPlansResponse(body)
            }
        }
    }

    private fun parseQuotaPlansResponse(body: String): ApiResponse<QuotaPlansPayload> {
        val root = JSONObject(body)
        val success = root.optBoolean("success", false)
        val message = root.optString("message")
        val dataObj = root.optJSONObject("data") ?: JSONObject()
        val arr = dataObj.optJSONArray("quotaPlans") ?: dataObj.optJSONArray("QuotaPlans") ?: JSONArray()
        val list = ArrayList<QuotaPlanDto>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(parseQuotaPlan(it)) }
        }
        return ApiResponse(success = success, message = message, data = QuotaPlansPayload(quotaPlans = list))
    }

    private fun parseQuotaPlan(o: JSONObject): QuotaPlanDto {
        return QuotaPlanDto(
            id = o.optInt("id", o.optInt("Id", -1)),
            name = o.optString("name", o.optString("Name", "")),
            description = o.optStringOrNull("description") ?: o.optStringOrNull("Description"),
            dailyQuotaBytes = o.optLongOrNull("dailyQuotaBytes") ?: o.optLongOrNull("DailyQuotaBytes"),
            monthlyQuotaBytes = o.optLongOrNull("monthlyQuotaBytes") ?: o.optLongOrNull("MonthlyQuotaBytes"),
            upKbps = o.optIntOrNull("upKbps") ?: o.optIntOrNull("UpKbps"),
            downKbps = o.optIntOrNull("downKbps") ?: o.optIntOrNull("DownKbps"),
            overlimitAction = o.optIntOrNull("overlimitAction") ?: o.optIntOrNull("OverlimitAction"),
            throttleUpKbps = o.optIntOrNull("throttleUpKbps") ?: o.optIntOrNull("ThrottleUpKbps"),
            throttleDownKbps = o.optIntOrNull("throttleDownKbps") ?: o.optIntOrNull("ThrottleDownKbps"),
            isActive = o.optBoolean("isActive", o.optBoolean("IsActive", true)),
            isDefault = o.optBoolean("isDefault", o.optBoolean("IsDefault", false))
        )
    }

    private fun parseUserQuotaPlansResponse(body: String): ApiResponse<UserQuotaPlansByUserPayload> {
        val root = JSONObject(body)
        val success = root.optBoolean("success", false)
        val message = root.optString("message")
        val dataObj = root.optJSONObject("data") ?: JSONObject()
        val arr = dataObj.optJSONArray("items") ?: dataObj.optJSONArray("Items") ?: JSONArray()
        val list = ArrayList<UserQuotaPlanDto>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(parseUserQuotaPlan(it)) }
        }
        return ApiResponse(success = success, message = message, data = UserQuotaPlansByUserPayload(items = list))
    }

    private fun parseUserQuotaPlan(o: JSONObject): UserQuotaPlanDto {
        return UserQuotaPlanDto(
            id = o.optInt("id", o.optInt("Id", -1)),
            userId = o.optInt("userId", o.optInt("UserId", -1)),
            quotaPlanId = o.optInt("quotaPlanId", o.optInt("QuotaPlanId", -1)),
            effectiveFrom = o.optStringOrNull("effectiveFrom") ?: o.optStringOrNull("EffectiveFrom"),
            effectiveTo = o.optStringOrNull("effectiveTo") ?: o.optStringOrNull("EffectiveTo"),
            assignedBy = o.optIntOrNull("assignedBy") ?: o.optIntOrNull("AssignedBy"),
            note = o.optStringOrNull("note") ?: o.optStringOrNull("Note")
        )
    }
}

data class QuotaPlansPayload(val quotaPlans: List<QuotaPlanDto>)

data class UserQuotaPlansByUserPayload(val items: List<UserQuotaPlanDto>)
