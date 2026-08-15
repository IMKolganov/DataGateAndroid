package com.imkolganov.datagate.ui.screens.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessVpnSessionPolicyTest {

    @Test
    fun activeSessionServerId_idle_isNullEvenIfStoreHasSelection() {
        assertNull(
            AccessVpnSessionPolicy.activeSessionServerId(
                isVpnConnected = false,
                isVpnPaused = false,
                isConnectRequested = false,
                vpnSelectedServerId = null,
                storeSelectedServerId = 99,
            ),
        )
    }

    @Test
    fun activeSessionServerId_connected_prefersVpnStateOverStore() {
        assertEquals(
            42,
            AccessVpnSessionPolicy.activeSessionServerId(
                isVpnConnected = true,
                isVpnPaused = false,
                isConnectRequested = true,
                vpnSelectedServerId = 42,
                storeSelectedServerId = 99,
            ),
        )
    }

    @Test
    fun activeSessionServerId_connected_fallsBackToStoreWhenVpnIdMissing() {
        assertEquals(
            99,
            AccessVpnSessionPolicy.activeSessionServerId(
                isVpnConnected = true,
                isVpnPaused = false,
                isConnectRequested = true,
                vpnSelectedServerId = null,
                storeSelectedServerId = 99,
            ),
        )
    }

    @Test
    fun activeSessionServerId_connectBusy_usesVpnOrStore() {
        assertEquals(
            7,
            AccessVpnSessionPolicy.activeSessionServerId(
                isVpnConnected = false,
                isVpnPaused = false,
                isConnectRequested = true,
                vpnSelectedServerId = 7,
                storeSelectedServerId = null,
            ),
        )
    }

    @Test
    fun isSessionCard_onlyOnConnectedOrPausedMatchingServer() {
        assertTrue(
            AccessVpnSessionPolicy.isSessionCard(
                activeSessionServerId = 5,
                serverId = 5,
                isVpnConnected = true,
                isVpnPaused = false,
            ),
        )
        assertFalse(
            AccessVpnSessionPolicy.isSessionCard(
                activeSessionServerId = 5,
                serverId = 6,
                isVpnConnected = true,
                isVpnPaused = false,
            ),
        )
        // Connecting: highlight via connecting flag, not Disconnect session card.
        assertFalse(
            AccessVpnSessionPolicy.isSessionCard(
                activeSessionServerId = 5,
                serverId = 5,
                isVpnConnected = false,
                isVpnPaused = false,
            ),
        )
    }

    @Test
    fun isConnectingToServer_onlyWhileBusyOnThatServer() {
        assertTrue(
            AccessVpnSessionPolicy.isConnectingToServer(
                activeSessionServerId = 3,
                serverId = 3,
                connectBusy = true,
            ),
        )
        assertFalse(
            AccessVpnSessionPolicy.isConnectingToServer(
                activeSessionServerId = 3,
                serverId = 4,
                connectBusy = true,
            ),
        )
        assertFalse(
            AccessVpnSessionPolicy.isConnectingToServer(
                activeSessionServerId = 3,
                serverId = 3,
                connectBusy = false,
            ),
        )
    }

    @Test
    fun disconnectButton_onlyOnSessionCard_notOnOtherSelectedCards() {
        val sessionId = 10
        val servers = listOf(10, 20, 30)
        val disconnectVisible = servers.filter { id ->
            AccessVpnSessionPolicy.isSessionCard(
                activeSessionServerId = sessionId,
                serverId = id,
                isVpnConnected = true,
                isVpnPaused = false,
            )
        }
        assertEquals(listOf(10), disconnectVisible)
    }
}
