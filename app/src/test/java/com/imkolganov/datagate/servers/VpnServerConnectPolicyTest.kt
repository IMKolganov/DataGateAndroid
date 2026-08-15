package com.imkolganov.datagate.servers

import com.imkolganov.datagate.model.servers.OpenVpnServerStatusLogResponse
import com.imkolganov.datagate.model.servers.OpenVpnServerV2Dto
import com.imkolganov.datagate.model.servers.OpenVpnServerWithStatusV2Item
import com.imkolganov.datagate.model.servers.VpnServerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Client must never auto-pick or hand off a server outside the user's quota plan —
 * backend HTTP 400 on create is only a safety net.
 */
class VpnServerConnectPolicyTest {

    @Test
    fun pickBestServer_skipsQuotaBlockedEvenIfLeastLoaded() {
        val items = listOf(
            row(
                id = 75,
                name = "pro-only",
                clients = 0,
                accessible = false,
            ),
            row(
                id = 69,
                name = "free-ok",
                clients = 5,
                accessible = true,
            ),
        )

        val best = VpnServerConnectPolicy.pickBestServer(items, ignoreQuotaPlanChecks = false)

        assertEquals(69, best.serverId)
        assertEquals("free-ok", best.name)
    }

    @Test
    fun pickBestServer_amongAccessible_picksLowestClientCountThenLowestId() {
        val items = listOf(
            row(id = 10, name = "a", clients = 2, accessible = true),
            row(id = 3, name = "b", clients = 2, accessible = true),
            row(id = 7, name = "c", clients = 1, accessible = true),
            row(id = 99, name = "blocked", clients = 0, accessible = false),
        )

        val best = VpnServerConnectPolicy.pickBestServer(items, ignoreQuotaPlanChecks = false)

        assertEquals(7, best.serverId)
    }

    @Test
    fun pickBestServer_skipsOfflineXrayAndNonWss() {
        val items = listOf(
            row(id = 1, name = "offline", clients = 0, accessible = true, online = false),
            row(id = 2, name = "xray", clients = 0, accessible = true, type = VpnServerType.Xray),
            row(id = 3, name = "no-wss", clients = 0, accessible = true, wss = false),
            row(id = 4, name = "ok", clients = 3, accessible = true),
        )

        assertEquals(4, VpnServerConnectPolicy.pickBestServer(items).serverId)
    }

    @Test
    fun pickBestServer_onlyQuotaBlocked_throws() {
        val items = listOf(
            row(id = 75, name = "pro", clients = 0, accessible = false),
        )
        try {
            VpnServerConnectPolicy.pickBestServer(items, ignoreQuotaPlanChecks = false)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("No online WSS"))
        }
    }

    @Test
    fun pickBestServer_ignoreQuotaFlag_allowsBlockedForTempBackendTesting() {
        val items = listOf(
            row(id = 75, name = "pro", clients = 0, accessible = false),
        )
        assertEquals(
            75,
            VpnServerConnectPolicy.pickBestServer(items, ignoreQuotaPlanChecks = true).serverId,
        )
    }

    @Test
    fun resolveManual_quotaBlocked_doesNotReturnOk() {
        val items = listOf(
            row(id = 75, name = "cyprus-pro", clients = 1, accessible = false),
        )

        val resolved = VpnServerConnectPolicy.resolveManualConnection(
            items = items,
            serverId = 75,
            ignoreQuotaPlanChecks = false,
        )

        assertTrue(resolved is ManualServerResolve.QuotaPlanBlocked)
        assertEquals("cyprus-pro", (resolved as ManualServerResolve.QuotaPlanBlocked).serverName)
    }

    @Test
    fun resolveManual_accessibleOnlineWithoutWss_returnsOkDirect() {
        val items = listOf(
            row(id = 42, name = "direct-only", clients = 1, accessible = true, wss = false),
        )

        val resolved = VpnServerConnectPolicy.resolveManualConnection(items, 42)

        assertTrue(resolved is ManualServerResolve.Ok)
        val ok = resolved as ManualServerResolve.Ok
        assertEquals(42, ok.result.serverId)
        assertEquals(false, ok.result.useWss)
    }

    @Test
    fun resolveManual_accessibleOnlineWss_returnsOk() {
        val items = listOf(
            row(id = 69, name = "helsinki", clients = 2, accessible = true),
        )

        val resolved = VpnServerConnectPolicy.resolveManualConnection(items, 69)

        assertTrue(resolved is ManualServerResolve.Ok)
        assertEquals(69, (resolved as ManualServerResolve.Ok).result.serverId)
    }

    @Test
    fun resolveManual_stalePremiumIdAfterPlanDowngrade_isQuotaBlocked() {
        // Pro → free: persisted MANUAL id 75 must not proceed to ensureAndDownloadDeviceFile.
        val items = listOf(
            row(id = 75, name = "pro-server", clients = 0, accessible = false),
            row(id = 69, name = "free-server", clients = 1, accessible = true),
        )

        val manual = VpnServerConnectPolicy.resolveManualConnection(items, 75, ignoreQuotaPlanChecks = false)
        val auto = VpnServerConnectPolicy.pickBestServer(items, ignoreQuotaPlanChecks = false)

        assertTrue(manual is ManualServerResolve.QuotaPlanBlocked)
        assertEquals(69, auto.serverId)
    }

    @Test
    fun resolveManual_accessibleOnlineXray_returnsOk() {
        val items = listOf(
            row(id = 88, name = "xray-node", clients = 1, accessible = true, type = VpnServerType.Xray, wss = false),
        )

        val resolved = VpnServerConnectPolicy.resolveManualConnection(items, 88)

        assertTrue(resolved is ManualServerResolve.Ok)
        val ok = resolved as ManualServerResolve.Ok
        assertEquals(88, ok.result.serverId)
        assertEquals(VpnServerType.Xray, ok.result.serverType)
        assertEquals(false, ok.result.useWss)
    }

    private fun row(
        id: Int,
        name: String,
        clients: Int,
        accessible: Boolean,
        online: Boolean = true,
        wss: Boolean = true,
        type: VpnServerType = VpnServerType.OpenVpn,
        deleted: Boolean = false,
    ): OpenVpnServerWithStatusV2Item {
        val server = OpenVpnServerV2Dto(
            id = id,
            serverType = type,
            serverName = name,
            isOnline = online,
            isDefault = false,
            apiUrl = "https://api.example/$id",
            latitude = null,
            longitude = null,
            isEnableWss = wss,
            createDate = null,
            lastUpdate = null,
            isDeleted = deleted,
            dcoIsEnabled = null,
            tags = emptyList(),
            quotaPlanGroups = emptyList(),
            isAccessibleForUserQuotaPlan = accessible,
        )
        return OpenVpnServerWithStatusV2Item(
            server = server,
            openVpnServerStatusLogResponse = OpenVpnServerStatusLogResponse(
                vpnServerId = id,
                sessionId = null,
                upSince = null,
                serverLocalIp = null,
                serverRemoteIp = null,
                bytesIn = null,
                bytesOut = null,
                version = null,
            ),
            countConnectedClients = clients,
            countSessions = null,
            totalBytesIn = null,
            totalBytesOut = null,
        )
    }
}
