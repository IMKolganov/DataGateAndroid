package com.imkolganov.datagate.stats

import com.imkolganov.datagate.configs.ApiConfig
import com.imkolganov.datagate.json.formatHttpErrorDetail
import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.json.optLongOrNull
import com.imkolganov.datagate.model.overview.OverviewMeta
import com.imkolganov.datagate.model.overview.OverviewRow
import com.imkolganov.datagate.model.overview.OverviewSeriesResponse
import com.imkolganov.datagate.model.overview.OverviewSummary
import com.imkolganov.datagate.model.overview.OverviewSummaryTotals
import executeSuspending
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

open class StatsApiClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val tokenProvider: () -> String?
) {
    open suspend fun getOverviewSeries(
        fromIso: String,
        toIso: String,
        grouping: Int,
        externalId: String
    ): ApiResponse<OverviewSeriesResponse> {
        val qs = buildQuery(
            "From" to fromIso,
            "To" to toIso,
            "Grouping" to grouping.toString(),
            "ExternalId" to externalId
        )

        val url = joinUrl(baseUrl, "${ApiConfig.API_OPEN_VPN_CLIENTS_OVERVIEW_SERIES_PATH}?$qs")

        val request = Request.Builder()
            .url(url)
            .get()
            .applyAuth()
            .build()

        return http.executeSuspending(request).use { resp ->
            val body = resp.body.string().orEmpty()
            if (resp.code !in 200..299) {
                throw IOException(formatHttpErrorDetail("Request failed", resp.code, body))
            }
            parseOverviewSeriesResponse(body)
        }
    }

    open suspend fun getOverviewSummary(
        fromIso: String,
        toIso: String,
        externalId: String
    ): ApiResponse<OverviewSummaryTotals> {
        val qs = buildQuery(
            "From" to fromIso,
            "To" to toIso,
            "ExternalId" to externalId
        )
        val url = joinUrl(baseUrl, "${ApiConfig.API_OPEN_VPN_CLIENTS_OVERVIEW_SUMMARY_PATH}?$qs")
        val request = Request.Builder()
            .url(url)
            .get()
            .applyAuth()
            .build()

        return http.executeSuspending(request).use { resp ->
            val body = resp.body.string().orEmpty()
            if (resp.code !in 200..299) {
                throw IOException(formatHttpErrorDetail("Overview summary failed", resp.code, body))
            }
            parseOverviewSummaryResponse(body)
        }
    }

    private fun parseOverviewSeriesResponse(body: String): ApiResponse<OverviewSeriesResponse> {
        val obj = JSONObject(body)
        val success = obj.optBoolean("success", false)
        val message = obj.optString("message", "")

        val dataObj = obj.optJSONObject("data")
        val data = if (dataObj != null) parseData(dataObj) else null

        return ApiResponse(
            success = success,
            message = message,
            data = data
        )
    }

    private fun parseOverviewSummaryResponse(body: String): ApiResponse<OverviewSummaryTotals> {
        val obj = JSONObject(body)
        val success = obj.optBoolean("success", false)
        val message = obj.optString("message", "")
        val dataObj = obj.optJSONObject("data") ?: JSONObject()
        val totals = dataObj.optJSONObject("totals") ?: dataObj.optJSONObject("Totals") ?: JSONObject()
        val direct = totals.optLongOrNull("trafficTotalBytes") ?: totals.optLongOrNull("TrafficTotalBytes")
        val totalBytes = when {
            direct != null && direct >= 0L -> direct
            else -> {
                val inn = totals.optLong("trafficInBytes", totals.optLong("TrafficInBytes", 0L))
                val out = totals.optLong("trafficOutBytes", totals.optLong("TrafficOutBytes", 0L))
                inn + out
            }
        }
        return ApiResponse(
            success = success,
            message = message,
            data = OverviewSummaryTotals(trafficTotalBytes = totalBytes)
        )
    }

    private fun parseData(dataObj: JSONObject): OverviewSeriesResponse {
        val metaObj = dataObj.getJSONObject("meta")
        val summaryObj = dataObj.getJSONObject("summary")
        val rowsArr = dataObj.getJSONArray("overviewSeriesRows")

        val meta = OverviewMeta(
            from = metaObj.getString("from"),
            to = metaObj.getString("to"),
            grouping = metaObj.getString("grouping"),
            timezone = metaObj.getString("timezone"),
            trafficUnit = metaObj.getString("trafficUnit"),
        )

        val summary = OverviewSummary(
            totalTrafficInBytes = summaryObj.getLong("totalTrafficInBytes"),
            totalTrafficOutBytes = summaryObj.getLong("totalTrafficOutBytes"),
            peakActiveClients = summaryObj.getInt("peakActiveClients")
        )

        val rows = parseRows(rowsArr)

        return OverviewSeriesResponse(
            meta = meta,
            summary = summary,
            overviewSeriesRows = rows
        )
    }

    private fun parseRows(arr: JSONArray): List<OverviewRow> {
        val out = ArrayList<OverviewRow>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            out.add(
                OverviewRow(
                    ts = r.getString("ts"),
                    activeClients = r.getInt("activeClients"),
                    trafficInBytes = r.getLong("trafficInBytes"),
                    trafficOutBytes = r.getLong("trafficOutBytes"),
                    trafficTotalBytes = r.getLong("trafficTotalBytes")
                )
            )
        }
        return out
    }

    private fun Request.Builder.applyAuth(): Request.Builder {
        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            header("Authorization", "Bearer $token")
        }
        return this
    }

    private fun joinUrl(base: String, path: String): String {
        val b = if (base.endsWith("/")) base.dropLast(1) else base
        val p = if (path.startsWith("/")) path.drop(1) else path
        return "$b/$p"
    }

    private fun buildQuery(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (k, v) ->
            val encK = URLEncoder.encode(k, StandardCharsets.UTF_8.toString())
            val encV = URLEncoder.encode(v, StandardCharsets.UTF_8.toString())
            "$encK=$encV"
        }
    }
}
