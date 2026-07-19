package com.imkolganov.datagate.vpn

/**
 * Guards [OpenVpn3Service] vpnJob [finally] teardown so a stale session cannot call
 * [OpenVpn3Service.stopVpnInternal] after a newer [OpenVpn3Service.startVpn] has already
 * claimed the global client/bridge/job pointers.
 */
internal object OpenVpnSessionTeardownPolicy {
    fun shouldRunVpnJobFinally(sessionGeneration: Int, currentGeneration: Int): Boolean =
        sessionGeneration == currentGeneration

    /**
     * When bridge-loss already armed [reconnectPendingAfterJob], core DISCONNECTED must not
     * also call [OpenVpn3Service.startPendingConnectIfPossible] — the dying job's finally owns
     * that reconnect. Double-start is what races the stale finally into the new session.
     */
    fun shouldDeferReconnectToBridgeLossFinally(reconnectPendingAfterJob: Boolean): Boolean =
        reconnectPendingAfterJob
}
