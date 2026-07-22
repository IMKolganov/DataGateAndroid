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

    /**
     * While bridge-loss finally owns reconnect, other entry points (network change, core
     * DISCONNECTED, etc.) must not call [OpenVpn3Service.startPendingConnectIfPossible] —
     * except the finally's own [RetryConnect] with reason `bridge_transport_lost`.
     */
    fun shouldDeferPendingConnectWhileBridgeLossOwnsReconnect(
        reconnectPendingAfterJob: Boolean,
        reason: String,
    ): Boolean =
        reconnectPendingAfterJob && reason != BRIDGE_TRANSPORT_LOST_RETRY_REASON

    const val BRIDGE_TRANSPORT_LOST_RETRY_REASON = "bridge_transport_lost"

    /**
     * A skipped (stale) finally must release [reconnectPendingAfterJob]. Leaving the flag set
     * after a newer session already started makes that live session's DISCONNECTED defer forever
     * to a finally that will never run.
     */
    fun shouldClearReconnectPendingOnStaleFinally(): Boolean = true
}
