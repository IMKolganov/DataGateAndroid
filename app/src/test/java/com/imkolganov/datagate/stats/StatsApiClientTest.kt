package com.imkolganov.datagate.stats

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsApiClientTest {
    private val api = StatsApiClient(
        http = OkHttpClient(),
        baseUrl = "https://example.invalid",
        tokenProvider = { null }
    )

    @Test
    fun parseOverviewSeriesResponse_acceptsRowsKey_andComputesTrafficTotal() {
        val response = api.parseOverviewSeriesResponse(
            """
            {
              "success": true,
              "message": "Success",
              "data": {
                "meta": {
                  "from": "2026-03-01T00:00:00Z",
                  "to": "2026-03-31T23:59:59Z",
                  "grouping": "days",
                  "timezone": "UTC",
                  "trafficUnit": "bytes"
                },
                "summary": {
                  "peakActiveClients": 5
                },
                "rows": [
                  {
                    "ts": "2026-03-02T00:00:00Z",
                    "activeClients": 2,
                    "trafficInBytes": 10,
                    "trafficOutBytes": 20
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        val data = requireNotNull(response.data)
        val row = data.overviewSeriesRows.single()
        assertEquals(30L, row.trafficTotalBytes)
        assertEquals(2, row.activeClients)
        assertEquals("days", data.meta.grouping)
    }

    @Test
    fun parseOverviewSeriesResponse_acceptsLegacyOverviewSeriesRowsKey() {
        val response = api.parseOverviewSeriesResponse(
            """
            {
              "success": true,
              "message": "Success",
              "data": {
                "meta": {
                  "from": "2026-03-01T00:00:00Z",
                  "to": "2026-03-31T23:59:59Z",
                  "grouping": "days",
                  "timezone": "UTC",
                  "trafficUnit": "bytes"
                },
                "summary": {
                  "totalTrafficInBytes": 10,
                  "totalTrafficOutBytes": 20,
                  "peakActiveClients": 1
                },
                "overviewSeriesRows": [
                  {
                    "ts": "2026-03-02T00:00:00Z",
                    "activeClients": 1,
                    "trafficInBytes": 10,
                    "trafficOutBytes": 20,
                    "trafficTotalBytes": 30
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        val data = requireNotNull(response.data)
        assertEquals(1, data.overviewSeriesRows.size)
        assertEquals(30L, data.overviewSeriesRows.first().trafficTotalBytes)
        assertEquals(1, data.summary.peakActiveClients)
    }
}
