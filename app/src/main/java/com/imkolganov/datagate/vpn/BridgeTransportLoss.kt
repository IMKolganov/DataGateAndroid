package com.imkolganov.datagate.vpn

import java.util.concurrent.atomic.AtomicBoolean

internal object BridgeTransportLoss {

    fun formatFailureReason(error: Throwable): String =
        "wss_failure:${error.message ?: error.javaClass.simpleName}"

    fun formatClosedReason(code: Int, reason: String): String =
        "wss_closed:$code:${reason.ifBlank { "closed" }}"

    fun formatSendRejectedReason(): String = "wss_send_rejected"

    fun formatIdleReason(idleForMs: Long): String =
        BridgeIdleProbePolicy.formatIdleReason(idleForMs)

    /**
     * OkHttp [okhttp3.WebSocket.send] returns false when the outbound queue is saturated or the
     * socket is already closing. Ignoring that leaves OpenVPN's local TCP to the bridge open
     * while tunneled frames are dropped — CONNECTED UI, dead YouTube until manual reconnect.
     */
    fun shouldTreatSendRejectedAsTransportLost(sendAccepted: Boolean): Boolean = !sendAccepted

    fun notifyOnce(
        notified: AtomicBoolean,
        callback: ((String) -> Unit)?,
        reason: String
    ) {
        if (notified.compareAndSet(false, true)) {
            callback?.invoke(reason)
        }
    }
}
