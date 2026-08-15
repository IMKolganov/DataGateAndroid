package com.imkolganov.datagate.ui.screens.access

/**
 * Pure rules for which Access card is the live VPN session (highlight + Disconnect).
 * Must not fall back to the Access list selection alone — that made every tapped card
 * look like the session.
 */
object AccessVpnSessionPolicy {

    fun isConnectBusy(
        isConnectRequested: Boolean,
        isVpnConnected: Boolean,
        isVpnPaused: Boolean,
    ): Boolean = isConnectRequested && !isVpnConnected && !isVpnPaused

    /**
     * Server id of the in-flight or active tunnel, or null when idle.
     * Prefers [vpnSelectedServerId]; [storeSelectedServerId] is only a fallback while a
     * session is active (e.g. after prefs restore).
     */
    fun activeSessionServerId(
        isVpnConnected: Boolean,
        isVpnPaused: Boolean,
        isConnectRequested: Boolean,
        vpnSelectedServerId: Int?,
        storeSelectedServerId: Int?,
    ): Int? {
        val connectBusy = isConnectBusy(isConnectRequested, isVpnConnected, isVpnPaused)
        if (!(isVpnConnected || isVpnPaused || connectBusy)) return null
        return vpnSelectedServerId ?: storeSelectedServerId
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
