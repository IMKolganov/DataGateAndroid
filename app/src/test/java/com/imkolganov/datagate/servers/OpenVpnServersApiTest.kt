package com.imkolganov.datagate.servers

import com.imkolganov.datagate.model.servers.VpnServerType
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
        assertEquals(VpnServerType.OpenVpn, item.server.serverType)
    }

    @Test
    fun parseWithStatusResponse_readsServerRemoteIpFromStatusLog() {
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
                        "id": 7,
                        "serverName": "de-1",
                        "isOnline": true,
                        "isDefault": false,
                        "apiUrl": "https://api.example",
                        "isEnableWss": true,
                        "isDeleted": false,
                        "tags": [],
                        "quotaPlanGroups": [],
                        "isAccessibleForUserQuotaPlan": true
                      }
                    },
                    "vpnServerStatusLogResponse": {
                      "vpnServerId": 7,
                      "sessionId": "s1",
                      "bytesIn": 0,
                      "bytesOut": 0,
                      "serverRemoteIp": "198.51.100.22"
                    },
                    "countConnectedClients": 0,
                    "countSessions": 0,
                    "totalBytesIn": 0,
                    "totalBytesOut": 0
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val status = response.data!!.openVpnServerWithStatuses.single().openVpnServerStatusLogResponse
        assertEquals("198.51.100.22", status?.serverRemoteIp)
    }

    @Test
    fun parseWithStatusResponse_readsServerTypeFromBackend() {
        val xray = api.parseWithStatusResponse(
            """
            {
              "success": true,
              "data": {
                "vpnServerWithStatuses": [{
                  "vpnServerResponses": {
                    "vpnServer": {
                      "id": 2,
                      "serverType": 1,
                      "serverName": "xray-1",
                      "isOnline": true,
                      "isDefault": false,
                      "apiUrl": "https://xray.example",
                      "isEnableWss": true,
                      "isDeleted": false,
                      "tags": [],
                      "quotaPlanGroups": [],
                      "isAccessibleForUserQuotaPlan": true
                    }
                  }
                }]
              }
            }
            """.trimIndent()
        ).data!!.openVpnServerWithStatuses.single().server

        assertEquals(VpnServerType.Xray, xray.serverType)
    }
}
