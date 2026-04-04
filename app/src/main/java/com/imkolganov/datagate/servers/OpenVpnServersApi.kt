package com.imkolganov.datagate.servers

import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.configs.AuthConfig
import com.imkolganov.datagate.json.formatHttpErrorDetail
import com.imkolganov.datagate.json.optBooleanOrNull
import com.imkolganov.datagate.json.optDoubleOrNull
import com.imkolganov.datagate.json.optIntOrNull
import com.imkolganov.datagate.json.optLongOrNull
import com.imkolganov.datagate.json.optStringOrNull
import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.servers.OpenVpnServerStatusLogResponse
import com.imkolganov.datagate.model.servers.OpenVpnServerV2Dto
import com.imkolganov.datagate.model.servers.OpenVpnServerWithStatusV2Item
import com.imkolganov.datagate.model.servers.OpenVpnServersWithStatusV2Data
import com.imkolganov.datagate.model.servers.QuotaPlanGroupDto
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OpenVpnServersApi(
    private val http: OkHttpClient,
    private val baseUrl: String = AuthConfig.BACKEND_BASE_URL
) {
    fun getOpenVpnServersWithStatusV2(): ApiResponse<OpenVpnServersWithStatusV2Data> {
        val url = baseUrl.trimEnd('/') + "/" + ApiConfig.API_OPEN_VPN_SERVERS_V2_GET_ALL_WITH_STATUS_PATH.trimStart('/')

        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = resp.body.string().orEmpty()

            if (code !in 200..299) {
                throw IOException(formatHttpErrorDetail("Request failed", code, body))
            }

            return parseWithStatusResponse(body)
        }
    }

    private fun parseWithStatusResponse(body: String): ApiResponse<OpenVpnServersWithStatusV2Data> {
        val root = JSONObject(body)

        val success = root.optBoolean("success", false)
        val message = root.optString("message")

        val dataObj = root.optJSONObject("data")
        val arr = dataObj?.let { o ->
            o.optJSONArray("openVpnServerWithStatuses")
                ?: o.optJSONArray("OpenVpnServerWithStatuses")
        } ?: JSONArray()

        val list = parseWithStatusItems(arr)

        return ApiResponse(
            success = success,
            message = message,
            data = OpenVpnServersWithStatusV2Data(openVpnServerWithStatuses = list)
        )
    }

    private fun parseWithStatusItems(arr: JSONArray): List<OpenVpnServerWithStatusV2Item> {
        val out = ArrayList<OpenVpnServerWithStatusV2Item>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val item = parseWithStatusItem(o) ?: continue
            out.add(item)
        }
        return out
    }

    private fun parseWithStatusItem(obj: JSONObject): OpenVpnServerWithStatusV2Item? {
        val serverResponsesObj = obj.optJSONObject("openVpnServerResponses")
            ?: obj.optJSONObject("OpenVpnServerResponses")
        val openVpnServerObj = serverResponsesObj?.optJSONObject("openVpnServer")
            ?: serverResponsesObj?.optJSONObject("OpenVpnServer")
        val server = openVpnServerObj?.let { parseServerV2(it) } ?: return null

        val logObj = obj.optJSONObject("openVpnServerStatusLogResponse")
            ?: obj.optJSONObject("OpenVpnServerStatusLogResponse")
        val log = logObj?.let { parseStatusLog(it) }

        return OpenVpnServerWithStatusV2Item(
            server = server,
            openVpnServerStatusLogResponse = log,
            countConnectedClients = obj.optIntOrNull("countConnectedClients")
                ?: obj.optIntOrNull("CountConnectedClients"),
            countSessions = obj.optIntOrNull("countSessions")
                ?: obj.optIntOrNull("CountSessions"),
            totalBytesIn = obj.optLongOrNull("totalBytesIn")
                ?: obj.optLongOrNull("TotalBytesIn"),
            totalBytesOut = obj.optLongOrNull("totalBytesOut")
                ?: obj.optLongOrNull("TotalBytesOut")
        )
    }

    private fun parseStatusLog(o: JSONObject): OpenVpnServerStatusLogResponse {
        return OpenVpnServerStatusLogResponse(
            vpnServerId = o.optIntOrNull("vpnServerId") ?: o.optIntOrNull("VpnServerId"),
            sessionId = o.optStringOrNull("sessionId") ?: o.optStringOrNull("SessionId"),
            upSince = o.optStringOrNull("upSince") ?: o.optStringOrNull("UpSince"),
            serverLocalIp = o.optStringOrNull("serverLocalIp") ?: o.optStringOrNull("ServerLocalIp"),
            serverRemoteIp = o.optStringOrNull("serverRemoteIp") ?: o.optStringOrNull("ServerRemoteIp"),
            bytesIn = o.optLongOrNull("bytesIn") ?: o.optLongOrNull("BytesIn"),
            bytesOut = o.optLongOrNull("bytesOut") ?: o.optLongOrNull("BytesOut"),
            version = o.optStringOrNull("version") ?: o.optStringOrNull("Version")
        )
    }

    private fun parseServerV2(o: JSONObject): OpenVpnServerV2Dto? {
        val id = o.optIntOrNull("id") ?: o.optIntOrNull("Id") ?: return null
        if (id < 0) return null
        val tagsArr = o.optJSONArray("tags") ?: o.optJSONArray("Tags") ?: JSONArray()
        val tags = ArrayList<String>(tagsArr.length())
        for (i in 0 until tagsArr.length()) {
            tags.add(tagsArr.optString(i))
        }

        val qpgArr = o.optJSONArray("quotaPlanGroups")
            ?: o.optJSONArray("QuotaPlanGroups")
            ?: JSONArray()
        val quotaGroups = ArrayList<QuotaPlanGroupDto>(qpgArr.length())
        for (i in 0 until qpgArr.length()) {
            val q = qpgArr.optJSONObject(i) ?: continue
            quotaGroups.add(
                QuotaPlanGroupDto(
                    id = q.optInt("id", q.optInt("Id", 0)),
                    name = q.optString("name", q.optString("Name", ""))
                )
            )
        }

        return OpenVpnServerV2Dto(
            id = id,
            serverName = o.optString("serverName", o.optString("ServerName", "")),
            isOnline = o.optBoolean("isOnline", o.optBoolean("IsOnline", false)),
            isDefault = o.optBoolean("isDefault", o.optBoolean("IsDefault", false)),
            apiUrl = o.optString("apiUrl", o.optString("ApiUrl", "")),
            latitude = o.optDoubleOrNull("latitude") ?: o.optDoubleOrNull("Latitude"),
            longitude = o.optDoubleOrNull("longitude") ?: o.optDoubleOrNull("Longitude"),
            isEnableWss = o.optBoolean("isEnableWss", o.optBoolean("IsEnableWss", false)),
            createDate = o.optStringOrNull("createDate") ?: o.optStringOrNull("CreateDate"),
            lastUpdate = o.optStringOrNull("lastUpdate") ?: o.optStringOrNull("LastUpdate"),
            isDeleted = o.optBoolean("isDeleted", o.optBoolean("IsDeleted", false)),
            dcoIsEnabled = o.optBooleanOrNull("dcoIsEnabled") ?: o.optBooleanOrNull("DcoIsEnabled"),
            tags = tags,
            quotaPlanGroups = quotaGroups,
            isAccessibleForUserQuotaPlan = o.optBoolean(
                "isAccessibleForUserQuotaPlan",
                o.optBoolean("IsAccessibleForUserQuotaPlan", true)
            )
        )
    }
}
