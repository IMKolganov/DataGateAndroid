package com.imkolganov.datagate.vpn

import android.net.VpnService
import com.imkolganov.datagate.logger.VpnDebugLogger
import okio.ByteString.Companion.toByteString
import java.util.concurrent.atomic.AtomicLong

class TcpToWssBridge(
    private val service: VpnService,
    private val port: Int,
    private val wssUrl: String,
    private val http: okhttp3.OkHttpClient,
    private val onTransportLost: ((reason: String) -> Unit)? = null
) {
    @Volatile private var running = false
    private var server: java.net.ServerSocket? = null

    fun start(): Int {
        if (running) return server?.localPort ?: port

        val ss = java.net.ServerSocket()
        ss.reuseAddress = true
        ss.bind(java.net.InetSocketAddress("127.0.0.1", port))
        running = true
        server = ss
        val actualPort = ss.localPort

        Thread {
            while (running) {
                val socket = try { ss.accept() } catch (_: Throwable) { break }
                Thread {
                    try {
                        handle(socket)
                    } catch (_: Throwable) {
                        // Socket can be closed concurrently during shutdown/reconnect.
                    }
                }.start()
            }
        }.start()

        return actualPort
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
        server = null
    }

    private fun handle(tcp: java.net.Socket) {
        if (tcp.isClosed) return
        try {
            tcp.tcpNoDelay = true
        } catch (_: Throwable) {
            try { tcp.close() } catch (_: Throwable) {}
            return
        }

        try {
            val protectedOk = service.protect(tcp)
            VpnDebugLogger.d(
                TAG,
                "bridge.proto=tcp socket.role=local_bridge event=protect " +
                    "result=$protectedOk bound=${tcp.isBound} connected=${tcp.isConnected} " +
                    "closed=${tcp.isClosed}",
            )
        } catch (e: Exception) {
            VpnDebugLogger.w(
                TAG,
                "bridge.proto=tcp socket.role=local_bridge event=protect " +
                    "result=false error.type=${e.javaClass.name} " +
                    "error.message=${BridgeLogSanitizer.line(e.message)}",
                e,
            )
        }

        val queue = java.util.concurrent.LinkedBlockingQueue<okio.ByteString>()
        val transportLostNotified = java.util.concurrent.atomic.AtomicBoolean(false)
        val lastOutboundMs = AtomicLong(0L)
        val lastInboundMs = AtomicLong(0L)
        fun notifyTransportLost(reason: String) {
            BridgeTransportLoss.notifyOnce(transportLostNotified, { lostReason ->
                VpnDebugLogger.w(TAG, "bridge.proto=tcp transport_lost: $lostReason")
                onTransportLost?.invoke(lostReason)
            }, reason)
        }

        val req = okhttp3.Request.Builder().url(wssUrl).build()
        val bridgeSessionId = nextBridgeSessionId.incrementAndGet()
        VpnDebugLogger.d(
            TAG,
            "bridge.proto=tcp bridge.session.id=$bridgeSessionId event=websocket_create",
        )
        val ws = http.newWebSocket(req, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                VpnDebugLogger.d(
                    TAG,
                    "bridge.proto=tcp bridge.session.id=$bridgeSessionId " +
                        "event=websocket_open code=${response.code}",
                )
            }
            override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
                lastInboundMs.set(System.currentTimeMillis())
                queue.offer(bytes)
            }
            override fun onFailure(
                webSocket: okhttp3.WebSocket,
                t: Throwable,
                response: okhttp3.Response?
            ) {
                VpnDebugLogger.w(
                    TAG,
                    "bridge.proto=tcp bridge.session.id=$bridgeSessionId " +
                        "event=websocket_failure " +
                        "error.type=${t.javaClass.name} " +
                        "error.message=${BridgeLogSanitizer.line(t.message)} " +
                        "response.code=${response?.code}",
                    t,
                )
                notifyTransportLost(BridgeTransportLoss.formatFailureReason(t))
                queue.offer(okio.ByteString.EMPTY)
                try { tcp.close() } catch (_: Throwable) {}
            }
            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                VpnDebugLogger.d(
                    TAG,
                    "bridge.proto=tcp bridge.session.id=$bridgeSessionId " +
                        "event=websocket_closed code=$code " +
                        "reason=${BridgeLogSanitizer.line(reason)}",
                )
                notifyTransportLost(BridgeTransportLoss.formatClosedReason(code, reason))
                queue.offer(okio.ByteString.EMPTY)
                try { tcp.close() } catch (_: Throwable) {}
            }
        })

        if (tcp.isClosed || tcp.isInputShutdown || tcp.isOutputShutdown) {
            try { ws.cancel() } catch (_: Throwable) {}
            try { tcp.close() } catch (_: Throwable) {}
            return
        }

        val tcpIn = try {
            tcp.getInputStream()
        } catch (_: Throwable) {
            try { ws.cancel() } catch (_: Throwable) {}
            try { tcp.close() } catch (_: Throwable) {}
            return
        }

        val tcpOut = try {
            tcp.getOutputStream()
        } catch (_: Throwable) {
            try { ws.cancel() } catch (_: Throwable) {}
            try { tcp.close() } catch (_: Throwable) {}
            return
        }

        val t1 = Thread {
            val buf = ByteArray(16 * 1024)
            try {
                while (true) {
                    val n = tcpIn.read(buf)
                    if (n <= 0) break
                    val accepted = ws.send(buf.toByteString(0, n))
                    if (BridgeTransportLoss.shouldTreatSendRejectedAsTransportLost(accepted)) {
                        notifyTransportLost(BridgeTransportLoss.formatSendRejectedReason())
                        break
                    }
                    lastOutboundMs.set(System.currentTimeMillis())
                }
            } catch (_: Throwable) {
            } finally {
                try { ws.close(1000, "closing") } catch (_: Throwable) {}
            }
        }

        val t2 = Thread {
            try {
                val pollMs = 5_000L
                while (!tcp.isClosed) {
                    val bytes = queue.poll(pollMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (bytes != null) {
                        // optional: poison-pill
                        if (bytes.size == 0) break
                        tcpOut.write(bytes.toByteArray())
                        tcpOut.flush()
                        continue
                    }

                    val now = System.currentTimeMillis()
                    val outbound = lastOutboundMs.get()
                    val inbound = lastInboundMs.get()
                    if (BridgeIdleProbePolicy.shouldDeclareStall(outbound, inbound, now)) {
                        notifyTransportLost(BridgeIdleProbePolicy.formatIdleReason(now - outbound))
                        break
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { tcp.close() } catch (_: Throwable) {}
                try { ws.cancel() } catch (_: Throwable) {}
            }
        }

        t1.start()
        t2.start()
    }

    companion object {
        private const val TAG = "TcpWssBridge"
        private val nextBridgeSessionId = AtomicLong(0)
    }
}
