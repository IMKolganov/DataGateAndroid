package com.imkolganov.datagate.servers

import com.imkolganov.datagate.configs.AuthConfig
import com.imkolganov.datagate.json.optBooleanOrNull
import com.imkolganov.datagate.json.optDoubleOrNull
import com.imkolganov.datagate.json.optIntOrNull
import com.imkolganov.datagate.json.optLongOrNull
import com.imkolganov.datagate.json.optStringOrNull
import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.servers.GetAllWithStatusData
import com.imkolganov.datagate.model.servers.OpenVpnServer
import com.imkolganov.datagate.model.servers.OpenVpnServerResponses
import com.imkolganov.datagate.model.servers.OpenVpnServerStatusLogResponse
import com.imkolganov.datagate.model.servers.OpenVpnServerWithStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OpenVpnServersApi(
    private val http: OkHttpClient,
    private val baseUrl: String = AuthConfig.BACKEND_BASE_URL
) {
    fun getAllWithStatus(): ApiResponse<GetAllWithStatusData> {
        val url = baseUrl.trimEnd('/') + "/api/open-vpn-servers/get-all-with-status"

        val req = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string().orEmpty()

            if (code !in 200..299) {
                throw IOException("Request failed: HTTP $code, body=$body")
            }

            return parseApiResponse(body)
        }
    }

    private fun parseApiResponse(body: String): ApiResponse<GetAllWithStatusData> {
        val root = JSONObject(body)

        val success = root.optBoolean("success", false)
        val message = root.optString("message", null)

        val dataObj = root.optJSONObject("data")
        val list = if (dataObj != null) {
            val arr = dataObj.optJSONArray("openVpnServerWithStatuses") ?: JSONArray()
            parseStatuses(arr)
        } else {
            emptyList()
        }

        return ApiResponse(
            success = success,
            message = message,
            data = GetAllWithStatusData(openVpnServerWithStatuses = list)
        )
    }

    private fun parseStatuses(arr: JSONArray): List<OpenVpnServerWithStatus> {
        val out = ArrayList<OpenVpnServerWithStatus>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue

            val serverResponsesObj = obj.optJSONObject("openVpnServerResponses")
            val openVpnServerObj = serverResponsesObj?.optJSONObject("openVpnServer")
            val server = openVpnServerObj?.let { parseServer(it) }

            val responses = if (server != null) OpenVpnServerResponses(openVpnServer = server) else null

            val logObj = obj.optJSONObject("openVpnServerStatusLogResponse")
            val log = logObj?.let { parseLog(it) }

            out.add(
                OpenVpnServerWithStatus(
                    openVpnServerResponses = responses,
                    openVpnServerStatusLogResponse = log,
                    countConnectedClients = obj.optIntOrNull("countConnectedClients"),
                    countSessions = obj.optIntOrNull("countSessions"),
                    totalBytesIn = obj.optLongOrNull("totalBytesIn"),
                    totalBytesOut = obj.optLongOrNull("totalBytesOut")
                )
            )
        }
        return out
    }

    private fun parseServer(o: JSONObject): OpenVpnServer {
        return OpenVpnServer(
            id = o.optIntOrNull("id"),
            serverName = o.optStringOrNull("serverName"),
            isOnline = o.optBooleanOrNull("isOnline"),
            isDefault = o.optBooleanOrNull("isDefault"),
            apiUrl = o.optStringOrNull("apiUrl"),
            latitude = o.optDoubleOrNull("latitude"),
            longitude = o.optDoubleOrNull("longitude"),
            createDate = o.optStringOrNull("createDate"),
            lastUpdate = o.optStringOrNull("lastUpdate")
        )
    }

    private fun parseLog(o: JSONObject): OpenVpnServerStatusLogResponse {
        return OpenVpnServerStatusLogResponse(
            vpnServerId = o.optIntOrNull("vpnServerId"),
            sessionId = o.optStringOrNull("sessionId"),
            upSince = o.optStringOrNull("upSince"),
            serverLocalIp = o.optStringOrNull("serverLocalIp"),
            serverRemoteIp = o.optStringOrNull("serverRemoteIp"),
            bytesIn = o.optLongOrNull("bytesIn"),
            bytesOut = o.optLongOrNull("bytesOut"),
            version = o.optStringOrNull("version")
        )
    }
}
