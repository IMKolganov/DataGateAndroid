package com.imkolganov.datagate.ui.screens.access

import com.imkolganov.datagate.model.servers.VpnServerType
import com.imkolganov.datagate.vpn.ServerSelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessServerSelectionPolicyTest {

    @Test
    fun refresh_keepsPreviousWhenStillAllowed() {
        val servers = listOf(server(69, accessible = true), server(75, accessible = false))

        val id = AccessServerSelectionPolicy.resolveSelectedServerId(
            mode = ServerSelectionMode.MANUAL,
            previousSelectedId = 69,
            servers = servers,
        )

        assertEquals(69, id)
    }

    @Test
    fun refresh_afterProToFree_dropsBlockedSelectionAndPicksAllowed() {
        val servers = listOf(
            server(75, accessible = false, online = true),
            server(69, accessible = true, online = true),
            server(3, accessible = true, online = false),
        )

        val id = AccessServerSelectionPolicy.resolveSelectedServerId(
            mode = ServerSelectionMode.MANUAL,
            previousSelectedId = 75,
            servers = servers,
        )

        assertEquals(69, id)
    }

    @Test
    fun autoMode_clearsSelectionWhenPreviousBecomesBlocked() {
        val servers = listOf(server(75, accessible = false), server(69, accessible = true))

        val id = AccessServerSelectionPolicy.resolveSelectedServerId(
            mode = ServerSelectionMode.AUTO,
            previousSelectedId = 75,
            servers = servers,
        )

        assertNull(id)
    }

    @Test
    fun manualDefault_prefersOnlineAccessibleOverInaccessibleOnline() {
        val servers = listOf(
            server(75, accessible = false, online = true),
            server(3, accessible = true, online = false),
            server(69, accessible = true, online = true),
        )

        assertEquals(69, AccessServerSelectionPolicy.preferredManualServerId(servers))
    }

    @Test
    fun manualDefault_noAccessibleServers_returnsNull() {
        val servers = listOf(server(75, accessible = false), server(76, accessible = false))

        assertNull(AccessServerSelectionPolicy.preferredManualServerId(servers))
    }

    private fun server(
        id: Int,
        accessible: Boolean,
        online: Boolean = true,
    ): AccessContract.ServerItem = AccessContract.ServerItem(
        id = id,
        name = "Server $id",
        protocol = "UDP",
        isOnline = online,
        isEnableWss = true,
        serverType = VpnServerType.OpenVpn,
        uptimeText = null,
        openVpnVersionText = null,
        totalInText = null,
        totalOutText = null,
        isAccessibleForQuotaPlan = accessible,
    )
}
