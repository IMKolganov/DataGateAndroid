package com.imkolganov.datagate.auth.tv

import com.imkolganov.datagate.model.auth.CreateTvLoginSessionResponse
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvLoginApiTest {
    private val api = TvLoginApi(
        http = OkHttpClient(),
        baseUrl = "https://example.invalid",
    )

    @Test
    fun parseCreateResponse_readsCamelCaseFields() {
        val data = JSONObject(
            """
            {
              "sessionId": "11111111-2222-3333-4444-555555555555",
              "userCode": "482913",
              "verificationUrl": "https://dash.example/tv/link",
              "qrPayload": "https://dash.example/tv/link?code=482913",
              "expiresAt": "2026-07-22T12:00:00Z",
              "pollIntervalSeconds": 2,
              "signalRHubPath": "/api/hubs/tv-login"
            }
            """.trimIndent()
        )

        val parsed: CreateTvLoginSessionResponse = api.parseCreateResponse(data)
        assertEquals("11111111-2222-3333-4444-555555555555", parsed.sessionId)
        assertEquals("482913", parsed.userCode)
        assertEquals("https://dash.example/tv/link", parsed.verificationUrl)
        assertEquals("https://dash.example/tv/link?code=482913", parsed.qrPayload)
        assertEquals("2026-07-22T12:00:00Z", parsed.expiresAt)
        assertEquals(2, parsed.pollIntervalSeconds)
        assertEquals("/api/hubs/tv-login", parsed.signalRHubPath)
    }

    @Test
    fun parseCreateResponse_readsPascalCaseAndDefaults() {
        val data = JSONObject(
            """
            {
              "SessionId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "UserCode": "100200",
              "QrPayload": "https://x.test/tv/link?code=100200",
              "ExpiresAt": "2026-07-22T13:00:00+00:00"
            }
            """.trimIndent()
        )

        val parsed = api.parseCreateResponse(data)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", parsed.sessionId)
        assertEquals("100200", parsed.userCode)
        assertNull(parsed.verificationUrl)
        assertEquals(2, parsed.pollIntervalSeconds)
        assertEquals("/api/hubs/tv-login", parsed.signalRHubPath)
    }

    @Test
    fun parsePollResponse_readsApprovedTokens() {
        val data = JSONObject(
            """
            {
              "status": "approved",
              "expiresAt": "2026-07-22T12:00:00Z",
              "userId": 42,
              "displayName": "Ada",
              "email": "ada@example.com",
              "token": "access.jwt",
              "expiration": "2026-07-22T14:00:00Z",
              "refreshToken": "refresh.token",
              "refreshExpiration": "2026-08-22T14:00:00Z",
              "requiresTotp": false,
              "requiresTotpSetup": false
            }
            """.trimIndent()
        )

        val parsed = api.parsePollResponse(data)
        assertEquals("approved", parsed.status)
        assertEquals(42, parsed.userId)
        assertEquals("Ada", parsed.displayName)
        assertEquals("access.jwt", parsed.token)
        assertEquals("refresh.token", parsed.refreshToken)
        assertEquals(false, parsed.requiresTotp)
    }
}
