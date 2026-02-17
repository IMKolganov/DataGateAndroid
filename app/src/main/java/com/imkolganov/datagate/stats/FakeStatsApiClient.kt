package com.imkolganov.datagate.stats

import com.imkolganov.datagate.model.base.ApiResponse
import com.imkolganov.datagate.model.overview.OverviewMeta
import com.imkolganov.datagate.model.overview.OverviewRow
import com.imkolganov.datagate.model.overview.OverviewSeriesResponse
import com.imkolganov.datagate.model.overview.OverviewSummary

public class FakeStatsApiClient : StatsApiClient(
    http = okhttp3.OkHttpClient(),
    baseUrl = "http://localhost",
    tokenProvider = { null }
) {
    override suspend fun getOverviewSeries(
        fromIso: String,
        toIso: String,
        grouping: Int,
        externalId: String
    ): ApiResponse<OverviewSeriesResponse> {
        val data = OverviewSeriesResponse(
            meta = OverviewMeta(
                from = fromIso,
                to = toIso,
                grouping = "days",
                timezone = "UTC",
                trafficUnit = "bytes"
            ),
            summary = OverviewSummary(
                totalTrafficInBytes = 123456,
                totalTrafficOutBytes = 654321,
                peakActiveClients = 5
            ),
            overviewSeriesRows = listOf(
                OverviewRow(
                    ts = "2025-12-10T00:00:00+00:00",
                    activeClients = 0,
                    trafficInBytes = 0,
                    trafficOutBytes = 0,
                    trafficTotalBytes = 0
                ),
                OverviewRow(
                    ts = "2025-12-17T00:00:00+00:00",
                    activeClients = 5,
                    trafficInBytes = 8866793,
                    trafficOutBytes = 457844577,
                    trafficTotalBytes = 466711370
                )
            )
        )

        return ApiResponse(
            success = true,
            message = "Success",
            data = data
        )
    }
}
