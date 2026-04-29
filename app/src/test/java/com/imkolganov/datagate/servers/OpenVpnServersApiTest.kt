package com.imkolganov.datagate.servers

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnServersApiTest {
    private val api = OpenVpnServersApi(http = OkHttpClient())

    @Test
    fun parseWithStatusResponse_acceptsBackendVpnServerV2Names() {
        val response = api.parseWithStatusResponse(
            """
            {
              "success": true,
              "message": "Success",
              "data": {
                "vpnServerWithStatuses": [
                  {
                    "vpnServerResponses": {
                      "vpnServer": {
                        "id": 42,
                        "serverName": "ru-1",
                        "isOnline": true,
                        "isDefault": false,
                        "apiUrl": "https://api.example",
                        "isEnableWss": true,
                        "isDeleted": false,
                        "tags": ["ru"],
                        "quotaPlanGroups": [{"id": 7, "name": "basic"}],
                        "isAccessibleForUserQuotaPlan": true
                      }
                    },
                    "vpnServerStatusLogResponse": {
                      "vpnServerId": 42,
                      "sessionId": "s1",
                      "bytesIn": 10,
                      "bytesOut": 20
                    },
                    "countConnectedClients": 3,
                    "countSessions": 4,
                    "totalBytesIn": 100,
                    "totalBytesOut": 200
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val item = response.data!!.openVpnServerWithStatuses.single()
        assertTrue(response.success)
        assertEquals("ru-1", item.server.serverName)
        assertEquals(42, item.server.id)
        assertEquals(listOf("ru"), item.server.tags)
        assertEquals("basic", item.server.quotaPlanGroups.single().name)
        assertEquals(3, item.countConnectedClients)
        assertEquals(100L, item.totalBytesIn)
        assertEquals(42, item.openVpnServerStatusLogResponse?.vpnServerId)
    }
}
