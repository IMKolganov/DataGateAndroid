package com.imkolganov.datagate.vpn

import android.net.VpnService
import okio.ByteString.Companion.toByteString

class TcpToWssBridge(
    private val service: VpnService,
    private val port: Int,
    private val wssUrl: String,
    private val http: okhttp3.OkHttpClient
) {
    @Volatile private var running = false
    private var server: java.net.ServerSocket? = null

    fun start() {
        if (running) return
        running = true

        val ss = java.net.ServerSocket()
        ss.reuseAddress = true
        ss.bind(java.net.InetSocketAddress("127.0.0.1", port))
        server = ss

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
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
        server = null
    }

    private fun handle(tcp: java.net.Socket) {
        tcp.tcpNoDelay = true

        try {
            service.protect(tcp)
        } catch (_: Throwable) {
        }

        val queue = java.util.concurrent.LinkedBlockingQueue<okio.ByteString>()

        val req = okhttp3.Request.Builder().url(wssUrl).build()
        val ws = http.newWebSocket(req, object : okhttp3.WebSocketListener() {
            override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
                queue.offer(bytes)
            }
            override fun onFailure(
                webSocket: okhttp3.WebSocket,
                t: Throwable,
                response: okhttp3.Response?
            ) {
                queue.offer(okio.ByteString.EMPTY)
                try { tcp.close() } catch (_: Throwable) {}
            }
            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                queue.offer(okio.ByteString.EMPTY)
                try { tcp.close() } catch (_: Throwable) {}
            }
        })

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
                    ws.send(buf.toByteString(0, n))
                }
            } catch (_: Throwable) {
            } finally {
                try { ws.close(1000, "closing") } catch (_: Throwable) {}
            }
        }

        val t2 = Thread {
            try {
                while (!tcp.isClosed) {
                    val bytes = queue.poll(5, java.util.concurrent.TimeUnit.SECONDS)
                        ?: continue

                    // optional: poison-pill
                    if (bytes.size == 0) break

                    tcpOut.write(bytes.toByteArray())
                    tcpOut.flush()
                }
            } catch (_: Throwable) {
            } finally {
                try { tcp.close() } catch (_: Throwable) {}
            }
        }

        t1.start()
        t2.start()
    }
}
