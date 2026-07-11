package com.imkolganov.datagate.ui

import com.imkolganov.datagate.vpn.VpnCommandContract
import com.imkolganov.datagate.vpn.VpnStatusUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the logout flow shared by every entry point (main Settings screen and
 * the forced admin-TOTP-setup gate). Before this, only the admin-gate screen disconnected the VPN
 * on logout — the main Settings logout left a stale session routing traffic. See [performLogout].
 */
class AppRootLogoutTest {

    @Test
    fun connectedVpn_disconnectsBeforeLoggingOut() {
        val calls = mutableListOf<String>()
        performLogout(
            vpnState = VpnStatusUiState(isConnectRequested = true, isVpnConnected = true),
            onRequestDisconnect = { calls += "disconnect" },
            logout = { calls += "logout" },
            onAuthChanged = { calls += "authChanged" },
        )
        assertEquals(listOf("disconnect", "logout", "authChanged"), calls)
    }

    @Test
    fun pausedVpn_disconnectsBeforeLoggingOut() {
        val calls = mutableListOf<String>()
        performLogout(
            vpnState = VpnStatusUiState(isConnectRequested = true, isVpnPaused = true),
            onRequestDisconnect = { calls += "disconnect" },
            logout = { calls += "logout" },
            onAuthChanged = { calls += "authChanged" },
        )
        assertEquals(listOf("disconnect", "logout", "authChanged"), calls)
    }

    @Test
    fun pendingPauseCommand_disconnectsBeforeLoggingOut() {
        val calls = mutableListOf<String>()
        val statusWithPendingCommand = VpnCommandContract.beginPauseRequest(
            VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)
        )
        performLogout(
            vpnState = statusWithPendingCommand,
            onRequestDisconnect = { calls += "disconnect" },
            logout = { calls += "logout" },
            onAuthChanged = { calls += "authChanged" },
        )
        assertEquals(listOf("disconnect", "logout", "authChanged"), calls)
    }

    @Test
    fun connectInProgress_disconnectsBeforeLoggingOut() {
        val calls = mutableListOf<String>()
        performLogout(
            vpnState = VpnStatusUiState(isConnectRequested = true),
            onRequestDisconnect = { calls += "disconnect" },
            logout = { calls += "logout" },
            onAuthChanged = { calls += "authChanged" },
        )
        assertEquals(listOf("disconnect", "logout", "authChanged"), calls)
    }

    @Test
    fun alreadyDisconnected_doesNotCallDisconnect() {
        val calls = mutableListOf<String>()
        performLogout(
            vpnState = VpnStatusUiState(),
            onRequestDisconnect = { calls += "disconnect" },
            logout = { calls += "logout" },
            onAuthChanged = { calls += "authChanged" },
        )
        assertEquals(listOf("logout", "authChanged"), calls)
    }

    @Test
    fun logoutAndAuthChanged_alwaysRunRegardlessOfVpnState() {
        val calls = mutableListOf<String>()
        performLogout(
            vpnState = VpnStatusUiState(),
            onRequestDisconnect = { calls += "disconnect" },
            logout = { calls += "logout" },
            onAuthChanged = { calls += "authChanged" },
        )
        assertEquals(listOf("logout", "authChanged"), calls)
    }
}
