package com.imkolganov.datagate.vpn

import com.imkolganov.datagate.logger.VpnDebugLogger
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic-only OkHttp [EventListener] for WSS egress.
 * Correlates events with per-call [callId] and per-attempt [connectAttempt].
 * Does **not** correlate with [ProtectingSocketFactory] socket.id (OkHttp offers no reliable link).
 */
internal class WssEgressOkHttpEventListener(
    private val callId: Long,
    private val log: (String) -> Unit = { VpnDebugLogger.d(TAG, it) },
) : EventListener() {

    private var connectAttempt = 0

    override fun callStart(call: Call) {
        log("call.id=$callId event=call_start")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectAttempt += 1
        log(
            "call.id=$callId attempt=$connectAttempt event=connect_start " +
                "address=$inetSocketAddress proxy=${proxy.type()}"
        )
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        log(
            "call.id=$callId attempt=$connectAttempt event=connect_end " +
                "address=$inetSocketAddress protocol=$protocol"
        )
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        log(
            "call.id=$callId attempt=$connectAttempt event=connect_failed " +
                "address=$inetSocketAddress error=${ioe.message ?: ioe.javaClass.simpleName}"
        )
    }

    override fun secureConnectStart(call: Call) {
        log("call.id=$callId attempt=$connectAttempt event=secure_connect_start")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        log(
            "call.id=$callId attempt=$connectAttempt event=secure_connect_end " +
                "tls=${handshake?.tlsVersion} cipher=${handshake?.cipherSuite}"
        )
    }

    override fun callFailed(call: Call, ioe: IOException) {
        log(
            "call.id=$callId event=call_failed " +
                "error=${ioe.message ?: ioe.javaClass.simpleName}"
        )
    }

    companion object {
        private const val TAG = "WssEgressHttp"
        private val nextCallId = AtomicLong(0)

        /** Unique [callId] per Call; each Call gets its own listener instance. */
        val FACTORY = EventListener.Factory {
            WssEgressOkHttpEventListener(callId = nextCallId.incrementAndGet())
        }

        /** Test hook: allocate a listener with an explicit id (does not advance [nextCallId]). */
        internal fun forTest(
            callId: Long,
            log: (String) -> Unit,
        ): WssEgressOkHttpEventListener = WssEgressOkHttpEventListener(callId, log)

        internal fun allocateCallIdForTest(): Long = nextCallId.incrementAndGet()
    }
}
