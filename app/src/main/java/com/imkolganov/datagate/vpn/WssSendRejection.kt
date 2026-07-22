package com.imkolganov.datagate.vpn

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared handling for OkHttp [okhttp3.WebSocket.send] returning false
 * (outbound queue saturated or socket closing).
 */
internal object WssSendRejection {

    /**
     * @return true if [sendAccepted] and the caller may continue sending.
     */
    fun acceptOrHandleRejection(
        sendAccepted: Boolean,
        phase: String,
        proto: String,
        sessionId: Long,
        notified: AtomicBoolean,
        onTransportLost: ((String) -> Unit)?,
        closeLocal: () -> Unit,
        log: (String) -> Unit,
    ): Boolean {
        if (sendAccepted) return true
        log(
            "bridge.proto=$proto bridge.session.id=$sessionId " +
                "event=websocket_send_rejected phase=$phase"
        )
        BridgeTransportLoss.notifyOnce(
            notified,
            onTransportLost,
            BridgeTransportLoss.formatSendRejectedReason(),
        )
        closeLocal()
        return false
    }
}
