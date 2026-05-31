package com.imkolganov.datagate.vpn

import android.net.VpnService
import android.os.Build
import okio.ByteString.Companion.toByteString
import java.io.FileDescriptor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Forwards OpenVPN UDP (127.0.0.1:[port]) to [wssUrl].
 *
 * Must match the ASP.NET UDP proxy: each **outbound** WS binary message is
 * `[u16_be length][payload]` (one datagram per frame). **Inbound** WS messages may contain
 * one or more such chunks concatenated; each chunk is forwarded as one UDP datagram.
 */
class UdpToWssBridge(
    private val service: VpnService,
    private val port: Int,
    private val wssUrl: String,
    private val http: okhttp3.OkHttpClient
) {
    @Volatile
    private var running = false

    private var socket: DatagramSocket? = null

    fun start(): Int {
        if (running) return socket?.localPort ?: port
        running = true

        val ds = DatagramSocket(null)
        ds.reuseAddress = true
        ds.bind(InetSocketAddress("127.0.0.1", port))
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
        fun releaseHandshake() {
            if (handshakeReleased.compareAndSet(false, true)) {
                wsHandshakeDone.countDown()
            }
        }

        val req = okhttp3.Request.Builder().url(wssUrl).build()
        val ws = http.newWebSocket(
            req,
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    // Matches DataGate proxy optional drain; must be before first binary UDP frame.
                    webSocket.send("""{"type":"connect","proto":"udp"}""")
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
                    releaseHandshake()
                    queue.offer(okio.ByteString.EMPTY)
                    try {
                        ds.close()
                    } catch (_: Throwable) {
                    }
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    releaseHandshake()
                    queue.offer(okio.ByteString.EMPTY)
                    try {
                        ds.close()
                    } catch (_: Throwable) {
                    }
                }
            }
        )

        val udpToWs = Thread(
            {
                try {
                    if (!wsHandshakeDone.await(60, TimeUnit.SECONDS) || !running || ds.isClosed) {
                        return@Thread
                    }
                    val firstBytes = first.data.copyOf(first.length)
                    ws.send(frameDatagramForWs(firstBytes, first.length).toByteString())
                    while (running && !ds.isClosed) {
                        val p = DatagramPacket(buf, buf.size)
                        ds.receive(p)
                        if (p.socketAddress != peer) {
                            continue
                        }
                        val chunk = p.data.copyOf(p.length)
                        ws.send(frameDatagramForWs(chunk, chunk.size).toByteString())
                    }
                } catch (_: Throwable) {
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
                    try {
                        ds.close()
                    } catch (_: Throwable) {
                    }
                }
            },
            "dg-udp-wss-in"
        )

        udpToWs.start()
        wsToUdp.start()
    }

    companion object {
        /** One datagram as `[u16_be len][payload]` for the ASP.NET proxy. */
        internal fun frameDatagramForWs(payload: ByteArray, length: Int): ByteArray {
            val n = length.coerceIn(0, payload.size)
            val out = ByteArray(2 + n)
            out[0] = ((n shr 8) and 0xFF).toByte()
            out[1] = (n and 0xFF).toByte()
            System.arraycopy(payload, 0, out, 2, n)
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
         * Exempt the local UDP socket from VPN routing when possible (outbound WSS is protected via OkHttp).
         */
        internal fun protectDatagramSocket(service: VpnService, socket: DatagramSocket) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
                val implField = DatagramSocket::class.java.getDeclaredField("impl").apply {
                    isAccessible = true
                }
                val impl = implField.get(socket) ?: return
                val fdField = impl.javaClass.getDeclaredField("fd").apply { isAccessible = true }
                val fd = fdField.get(impl) as? FileDescriptor ?: return
                val fdInt = getFdInt(fd) ?: return
                service.protect(fdInt)
            } catch (_: Throwable) {
            }
        }

        private fun getFdInt(fd: FileDescriptor): Int? {
            val hiddenName = StringBuilder("getInt").append('$').toString()
            return try {
                val m = FileDescriptor::class.java.getDeclaredMethod(hiddenName)
                m.isAccessible = true
                m.invoke(fd) as Int
            } catch (_: Throwable) {
                null
            }
        }
    }
}
