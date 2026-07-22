package com.imkolganov.datagate.vpn

/**
 * Decides whether a failed UDP↔WSS handshake await should fire transport lost.
 */
internal object UdpWssHandshakePolicy {

    const val HANDSHAKE_TIMEOUT_REASON = "UDP WebSocket handshake timed out"

    /**
     * [awaitSucceeded] false means the latch timed out (or was interrupted before countDown).
     * Only notify when the bridge is still intended to run and the local socket is still open.
     */
    fun shouldNotifyTransportLostOnHandshakeAwaitEnd(
        awaitSucceeded: Boolean,
        running: Boolean,
        datagramSocketClosed: Boolean,
    ): Boolean = !awaitSucceeded && running && !datagramSocketClosed
}
