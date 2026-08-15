package com.imkolganov.datagate.ui.screens.access

/**
 * Pure rules for which Access card is the live VPN session (highlight + Disconnect).
 * Uses only the VPN session server id — never Access list selection (that made every
 * tapped card look like the session, and wrongly highlighted catalog cards during
 * local profile tunnels that clear [vpnSelectedServerId]).
 */
object AccessVpnSessionPolicy {

    fun isConnectBusy(
        isConnectRequested: Boolean,
        isVpnConnected: Boolean,
        isVpnPaused: Boolean,
    ): Boolean = isConnectRequested && !isVpnConnected && !isVpnPaused

    /**
     * Server id of the in-flight or active catalog tunnel, or null when idle / local profile.
     */
    fun activeSessionServerId(
        isVpnConnected: Boolean,
        isVpnPaused: Boolean,
        isConnectRequested: Boolean,
        vpnSelectedServerId: Int?,
    ): Int? {
        val connectBusy = isConnectBusy(isConnectRequested, isVpnConnected, isVpnPaused)
        if (!(isVpnConnected || isVpnPaused || connectBusy)) return null
        return vpnSelectedServerId
    }

    fun isSessionCard(
        activeSessionServerId: Int?,
        serverId: Int,
        isVpnConnected: Boolean,
        isVpnPaused: Boolean,
    ): Boolean =
        (isVpnConnected || isVpnPaused) &&
            activeSessionServerId != null &&
            activeSessionServerId == serverId

    fun isConnectingToServer(
        activeSessionServerId: Int?,
        serverId: Int,
        connectBusy: Boolean,
    ): Boolean =
        connectBusy && activeSessionServerId != null && activeSessionServerId == serverId
}
