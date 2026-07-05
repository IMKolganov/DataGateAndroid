package com.imkolganov.datagate.freetier

import com.imkolganov.datagate.model.freetier.FreeTierAccessStatusResponse
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTierApiTest {
    private val api = FreeTierApi(
        http = OkHttpClient(),
        baseUrl = "https://example.invalid"
    )

    @Test
    fun parseAccessStatusResponse_readsCamelCaseFields() {
        val response = api.parseAccessStatusResponse(
            """
            {
              "success": true,
              "message": "OK",
              "data": {
                "isApplicable": true,
                "isCompliant": false,
                "isMergedAccount": false,
                "isChannelSubscribed": false,
                "isGracePeriod": false,
                "isLinkedToTelegram": false,
                "canRequestAccountLinkCode": true,
                "activePlanName": "Free",
                "requiredChannel": "@DataGateVPNBot"
              }
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        val status = requireNotNull(response.data)
        assertEquals(
            FreeTierAccessStatusResponse(
                isApplicable = true,
                isCompliant = false,
                isMergedAccount = false,
                isChannelSubscribed = false,
                isGracePeriod = false,
                isLinkedToTelegram = false,
                canRequestAccountLinkCode = true,
                activePlanName = "Free",
                requiredChannel = "@DataGateVPNBot"
            ),
            status
        )
    }

    @Test
    fun parseAccessStatusResponse_readsPascalCaseFields() {
        val response = api.parseAccessStatusResponse(
            """
            {
              "Success": true,
              "Message": "OK",
              "Data": {
                "IsApplicable": true,
                "IsCompliant": true,
                "IsMergedAccount": true,
                "IsChannelSubscribed": true,
                "IsGracePeriod": false,
                "IsLinkedToTelegram": true,
                "CanRequestAccountLinkCode": false,
                "ActivePlanName": "Default",
                "RequiredChannel": "@ExampleChannel"
              }
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        val status = requireNotNull(response.data)
        assertTrue(status.isCompliant)
        assertEquals("Default", status.activePlanName)
        assertEquals("@ExampleChannel", status.requiredChannel)
    }

    @Test
    fun parseAccessStatusResponse_returnsFailureWithoutData() {
        val response = api.parseAccessStatusResponse(
            """
            {
              "success": false,
              "message": "Unauthorized"
            }
            """.trimIndent()
        )

        assertFalse(response.success)
        assertEquals("Unauthorized", response.message)
        assertEquals(null, response.data)
    }

    @Test
    fun parseAccessStatusResponse_successTrueWithMissingData_returnsNullData() {
        val response = api.parseAccessStatusResponse(
            """
            {
              "success": true,
              "message": "OK"
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        assertEquals(null, response.data)
    }

    @Test
    fun parseAccountLinkCodeResponse_readsPascalCaseFields() {
        val response = api.parseAccountLinkCodeResponse(
            """
            {
              "Success": true,
              "Message": "OK",
              "Data": {
                "Code": "WXYZ5678",
                "ExpiresInSeconds": 600
              }
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        val data = requireNotNull(response.data)
        assertEquals("WXYZ5678", data.code)
        assertEquals(600, data.expiresInSeconds)
    }

    @Test
    fun parseAccountLinkCodeResponse_readsCodeAndExpiry() {
        val response = api.parseAccountLinkCodeResponse(
            """
            {
              "success": true,
              "message": "OK",
              "data": {
                "code": "ABCD2345",
                "expiresInSeconds": 900
              }
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        val data = requireNotNull(response.data)
        assertEquals("ABCD2345", data.code)
        assertEquals(900, data.expiresInSeconds)
    }

    @Test
    fun buildAccountLinkCodeRequestBody_includesTelegramId() {
        assertEquals(
            """{"telegramId":123456789}""",
            api.buildAccountLinkCodeRequestBody(123456789L)
        )
    }

    @Test(expected = java.io.IOException::class)
    fun parseAccountLinkCodeResponse_throwsWhenSuccessFalse() {
        api.parseAccountLinkCodeResponse(
            """
            {
              "success": false,
              "message": "Already linked to Telegram"
            }
            """.trimIndent()
        )
    }
}
