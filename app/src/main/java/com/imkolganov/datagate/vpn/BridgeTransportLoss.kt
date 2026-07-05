package com.imkolganov.datagate.vpn

import java.util.concurrent.atomic.AtomicBoolean

internal object BridgeTransportLoss {

    fun formatFailureReason(error: Throwable): String =
        "wss_failure:${error.message ?: error.javaClass.simpleName}"

    fun formatClosedReason(code: Int, reason: String): String =
        "wss_closed:$code:${reason.ifBlank { "closed" }}"

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
