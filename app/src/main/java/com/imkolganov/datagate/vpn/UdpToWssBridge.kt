package com.imkolganov.datagate.vpn

import android.net.VpnService
import com.imkolganov.datagate.logger.VpnDebugLogger
import okio.ByteString.Companion.toByteString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Forwards OpenVPN UDP (127.0.0.1:[port]) to [wssUrl].
 *
 * Must match the ASP.NET UDP proxy: each **outbound** WS binary message is
 * `[u16_be length][payload]` (one datagram per frame). **Inbound** WS messages may contain
 * one or more such chunks concatenated; each chunk is forwarded as one UDP datagram.
 *
 * Frame length is a u16 (0..65535). Larger datagrams are rejected (not silently truncated).
 * Note: a valid IP/UDP datagram on the wire is usually smaller than 65535 on real networks.
 */
class UdpToWssBridge(
    private val service: VpnService,
    private val port: Int,
    private val wssUrl: String,
    private val http: okhttp3.OkHttpClient,
    private val onTransportLost: ((reason: String) -> Unit)? = null
) {
    @Volatile
    private var running = false

    private var socket: DatagramSocket? = null

    fun start(): Int {
        if (running) return socket?.localPort ?: port

        val ds = DatagramSocket(null)
        ds.reuseAddress = true
        ds.bind(InetSocketAddress("127.0.0.1", port))
        running = true
        socket = ds
        protectDatagramSocket(service, ds)
        val actualPort = ds.localPort

        Thread(
            {
                val buf = ByteArray(65536)
                val first = DatagramPacket(buf, buf.size)
                try {
                    ds.receive(first)
                } catch (_: Throwable) {
                    if (running) {
                        try {
                            ds.close()
                        } catch (_: Throwable) {
                        }
                    }
                    return@Thread
                }
                if (!running) {
                    try {
                        ds.close()
                    } catch (_: Throwable) {
                    }
                    return@Thread
                }
                handleSession(ds, first, buf)
            },
            "dg-udp-wss-first"
        ).start()
        return actualPort
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        socket = null
    }

    private fun handleSession(ds: DatagramSocket, first: DatagramPacket, buf: ByteArray) {
        val peer = first.socketAddress as InetSocketAddress
        val queue = java.util.concurrent.LinkedBlockingQueue<okio.ByteString>()
        val wsHandshakeDone = CountDownLatch(1)
        val handshakeReleased = AtomicBoolean(false)
        val transportLostNotified = AtomicBoolean(false)
        fun notifyTransportLost(reason: String) {
            BridgeTransportLoss.notifyOnce(transportLostNotified, onTransportLost, reason)
        }
        fun releaseHandshake() {
            if (handshakeReleased.compareAndSet(false, true)) {
                wsHandshakeDone.countDown()
            }
        }
        fun closeLocalQuietly() {
            try {
                ds.close()
            } catch (_: Throwable) {
            }
        }

        val req = okhttp3.Request.Builder().url(wssUrl).build()
        val bridgeSessionId = nextBridgeSessionId.incrementAndGet()
        fun acceptSend(accepted: Boolean, phase: String): Boolean =
            WssSendRejection.acceptOrHandleRejection(
                sendAccepted = accepted,
                phase = phase,
                proto = "udp",
                sessionId = bridgeSessionId,
                notified = transportLostNotified,
                onTransportLost = onTransportLost,
                closeLocal = { closeLocalQuietly() },
                log = { VpnDebugLogger.d(TAG, it) },
            )

        VpnDebugLogger.d(
            TAG,
            "bridge.proto=udp bridge.session.id=$bridgeSessionId event=websocket_create",
        )
        val ws = http.newWebSocket(
            req,
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    VpnDebugLogger.d(
                        TAG,
                        "bridge.proto=udp bridge.session.id=$bridgeSessionId " +
                            "event=websocket_open code=${response.code}",
                    )
                    // Matches DataGate proxy optional drain; must be before first binary UDP frame.
                    val accepted = webSocket.send("""{"type":"connect","proto":"udp"}""")
                    if (!acceptSend(accepted, phase = "connect_control")) {
                        releaseHandshake()
                        return
                    }
                    releaseHandshake()
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
                    queue.offer(bytes)
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    VpnDebugLogger.w(
                        TAG,
                        "bridge.proto=udp bridge.session.id=$bridgeSessionId " +
                            "event=websocket_failure " +
                            "error.type=${t.javaClass.name} " +
                            "error.message=${BridgeLogSanitizer.line(t.message)} " +
                            "response.code=${response?.code}",
                        t,
                    )
                    releaseHandshake()
                    notifyTransportLost(BridgeTransportLoss.formatFailureReason(t))
                    queue.offer(okio.ByteString.EMPTY)
                    closeLocalQuietly()
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    VpnDebugLogger.d(
                        TAG,
                        "bridge.proto=udp bridge.session.id=$bridgeSessionId " +
                            "event=websocket_closed code=$code " +
                            "reason=${BridgeLogSanitizer.line(reason)}",
                    )
                    releaseHandshake()
                    notifyTransportLost(BridgeTransportLoss.formatClosedReason(code, reason))
                    queue.offer(okio.ByteString.EMPTY)
                    closeLocalQuietly()
                }
            }
        )

        val udpToWs = Thread(
            {
                try {
                    val handshakeOk = wsHandshakeDone.await(60, TimeUnit.SECONDS)
                    if (
                        UdpWssHandshakePolicy.shouldNotifyTransportLostOnHandshakeAwaitEnd(
                            awaitSucceeded = handshakeOk,
                            running = running,
                            datagramSocketClosed = ds.isClosed,
                        )
                    ) {
                        VpnDebugLogger.w(
                            TAG,
                            "bridge.proto=udp bridge.session.id=$bridgeSessionId " +
                                "event=websocket_handshake_timeout",
                        )
                        notifyTransportLost(UdpWssHandshakePolicy.HANDSHAKE_TIMEOUT_REASON)
                        closeLocalQuietly()
                        return@Thread
                    }
                    if (!handshakeOk || !running || ds.isClosed) {
                        return@Thread
                    }

                    if (first.length > MAX_U16_PAYLOAD) {
                        VpnDebugLogger.w(
                            TAG,
                            "bridge.proto=udp bridge.session.id=$bridgeSessionId " +
                                "event=datagram_oversized phase=first_datagram length=${first.length}",
                        )
                        notifyTransportLost(formatOversizedDatagramReason(first.length))
                        closeLocalQuietly()
                        return@Thread
                    }

                    val firstBytes = first.data.copyOf(first.length)
                    if (
                        !acceptSend(
                            ws.send(frameDatagramForWs(firstBytes, first.length).toByteString()),
                            phase = "first_datagram",
                        )
                    ) {
                        return@Thread
                    }

                    while (running && !ds.isClosed) {
                        val p = DatagramPacket(buf, buf.size)
                        ds.receive(p)
                        if (p.socketAddress != peer) {
                            continue
                        }
                        if (p.length > MAX_U16_PAYLOAD) {
                            VpnDebugLogger.w(
                                TAG,
                                "bridge.proto=udp bridge.session.id=$bridgeSessionId " +
                                    "event=datagram_oversized phase=datagram length=${p.length}",
                            )
                            notifyTransportLost(formatOversizedDatagramReason(p.length))
                            closeLocalQuietly()
                            break
                        }
                        val chunk = p.data.copyOf(p.length)
                        if (
                            !acceptSend(
                                ws.send(frameDatagramForWs(chunk, chunk.size).toByteString()),
                                phase = "datagram",
                            )
                        ) {
                            break
                        }
                    }
                } catch (_: Throwable) {
                    // receive/send interrupted by close during stop/reconnect
                } finally {
                    try {
                        ws.close(1000, "closing")
                    } catch (_: Throwable) {
                    }
                }
            },
            "dg-udp-wss-out"
        )

        val wsToUdp = Thread(
            {
                try {
                    while (running && !ds.isClosed) {
                        val bytes = queue.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                            ?: continue
                        if (bytes.size == 0) break
                        val raw = bytes.toByteArray()
                        for (payload in unframeWsPayloadsToDatagrams(raw)) {
                            val pkt = DatagramPacket(payload, payload.size, peer)
                            ds.send(pkt)
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    closeLocalQuietly()
                }
            },
            "dg-udp-wss-in"
        )

        udpToWs.start()
        wsToUdp.start()
    }

    companion object {
        private const val TAG = "UdpWssBridge"
        const val MAX_U16_PAYLOAD = 65535

        private val nextBridgeSessionId = AtomicLong(0)

        internal fun formatOversizedDatagramReason(length: Int): String =
            "udp_datagram_oversized:$length"

        /**
         * One datagram as `[u16_be len][payload]` for the ASP.NET proxy.
         * @throws IllegalArgumentException if [length] is outside `0..payload.size` or `> 65535`
         */
        internal fun frameDatagramForWs(payload: ByteArray, length: Int): ByteArray {
            require(length in 0..payload.size) {
                "UDP WSS frame length $length out of payload bounds ${payload.size}"
            }
            require(length <= MAX_U16_PAYLOAD) {
                "UDP WSS frame length $length exceeds u16 max $MAX_U16_PAYLOAD"
            }
            val out = ByteArray(2 + length)
            out[0] = ((length shr 8) and 0xFF).toByte()
            out[1] = (length and 0xFF).toByte()
            System.arraycopy(payload, 0, out, 2, length)
            return out
        }

        internal fun unframeWsPayloadsToDatagrams(data: ByteArray): List<ByteArray> {
            val result = ArrayList<ByteArray>()
            var off = 0
            while (off + 2 <= data.size) {
                val len = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
                off += 2
                if (len <= 0 || off + len > data.size) break
                result.add(data.copyOfRange(off, off + len))
                off += len
            }
            return result
        }

        /**
         * Protect local UDP listen socket from VPN routing via public [VpnService.protect].
         */
        internal fun protectDatagramSocket(
            service: VpnService,
            socket: DatagramSocket,
        ): Boolean = protectDatagramSocket(
            socket = socket,
            protect = { service.protect(it) },
        )

        /** Testable protect wrapper (no reflection). */
        internal fun protectDatagramSocket(
            socket: DatagramSocket,
            protect: (DatagramSocket) -> Boolean,
            log: (String) -> Unit = { VpnDebugLogger.d(TAG, it) },
        ): Boolean {
            return try {
                val ok = protect(socket)
                log(
                    "bridge.proto=udp socket.role=local_bridge event=protect " +
                        "result=$ok bound=${socket.isBound} connected=${socket.isConnected} " +
                        "closed=${socket.isClosed}"
                )
                ok
            } catch (e: Exception) {
                log(
                    "bridge.proto=udp socket.role=local_bridge event=protect " +
                        "result=false error.type=${e.javaClass.name} " +
                        "error.message=${BridgeLogSanitizer.line(e.message)}"
                )
                false
            }
        }
    }
}
