package com.imkolganov.datagate.ui

import com.imkolganov.datagate.vpn.VpnLifecyclePolicy
import com.imkolganov.datagate.vpn.VpnStatusUiState

/**
 * Shared logout flow for every entry point ([AppRoot]'s main Settings screen and the forced
 * admin-TOTP-setup gate). A single implementation avoids the two call sites drifting apart — one
 * previously disconnected the VPN on logout and the other didn't, leaving a stale session routing
 * traffic after the main-screen "Log out" button. See [VpnLifecyclePolicy.shouldDisconnectVpnOnLogout].
 */
internal fun performLogout(
    vpnState: VpnStatusUiState,
    onRequestDisconnect: () -> Unit,
    logout: () -> Unit,
    onAuthChanged: () -> Unit,
    clearServerSelection: () -> Unit = {},
) {
    if (VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
            isVpnConnected = vpnState.isVpnConnected,
            isConnectRequested = vpnState.isConnectRequested,
            isVpnPaused = vpnState.isVpnPaused,
            pendingUserCommand = vpnState.pendingUserCommand,
        )
    ) {
        onRequestDisconnect()
    }
    clearServerSelection()
    logout()
    onAuthChanged()
}
