package com.imkolganov.datagate.vpn

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory

class ProtectingSocketFactoryTest {

    @Test
    fun createSocket_returnsUnconnectedBoundSocket_andProtectsOnce() {
        val protectCalls = AtomicInteger(0)
        var connectedAtProtect = true
        val factory = ProtectingSocketFactory(
            delegate = SocketFactory.getDefault(),
            protect = { socket ->
                protectCalls.incrementAndGet()
                connectedAtProtect = socket.isConnected
                true
            },
        )

        val socket = factory.createSocket()
        try {
            assertFalse(socket.isConnected)
            assertTrue(socket.isBound)
            assertFalse(socket.isClosed)
            assertEquals(1, protectCalls.get())
            assertFalse(connectedAtProtect)
        } finally {
            socket.close()
        }
    }

    @Test
    fun createSocket_thenConnect_recordsBindProtectConnectOrder() {
        val events = CopyOnWriteArrayList<String>()
        val factory = ProtectingSocketFactory(
            delegate = RecordingSocketFactory(events),
            protect = {
                events += "protect"
                true
            },
        )

        val socket = factory.createSocket()
        try {
            assertEquals(listOf("create", "bind", "protect"), events.toList())
            assertFalse(socket.isConnected)

            ServerSocket(0).use { server ->
                socket.connect(InetSocketAddress("127.0.0.1", server.localPort), 2_000)
                server.accept().close()
            }

            assertEquals(listOf("create", "bind", "protect", "connect"), events.toList())
            assertTrue(socket.isConnected)
        } finally {
            socket.close()
        }
    }

    @Test
    fun createSocket_protectFalse_closesOnce_andThrowsExactMessage() {
        val protectCalls = AtomicInteger(0)
        var lastSocket: Socket? = null
        val factory = ProtectingSocketFactory(
            delegate = SocketFactory.getDefault(),
            protect = { socket ->
                protectCalls.incrementAndGet()
                lastSocket = socket
                false
            },
        )

        try {
            factory.createSocket()
            fail("expected IOException when protect returns false")
        } catch (e: IOException) {
            assertEquals("VpnService.protect failed for OkHttp plain socket", e.message)
        }

        val closed = lastSocket ?: error("protect was not called")
        assertEquals(1, protectCalls.get())
        assertTrue(closed.isClosed)
        assertFalse(closed.isConnected)
    }

    @Test
    fun createSocket_protectThrowsRuntimeException_preservesType_andCloses() {
        var lastSocket: Socket? = null
        val factory = ProtectingSocketFactory(
            delegate = SocketFactory.getDefault(),
            protect = { socket ->
                lastSocket = socket
                throw IllegalStateException("protect crashed")
            },
        )

        try {
            factory.createSocket()
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("protect crashed", e.message)
        }

        val closed = lastSocket ?: error("protect was not called")
        assertTrue(closed.isClosed)
    }

    @Test
    fun createSocket_logFailure_doesNotBreakSuccessfulProtect() {
        val factory = ProtectingSocketFactory(
            delegate = SocketFactory.getDefault(),
            protect = { true },
            log = { error("log must not abort socket creation") },
        )

        val socket = factory.createSocket()
        try {
            assertFalse(socket.isConnected)
            assertFalse(socket.isClosed)
        } finally {
            socket.close()
        }
    }

    @Test
    fun connectedOverloads_areDisabled() {
        val factory = ProtectingSocketFactory(
            delegate = SocketFactory.getDefault(),
            protect = { true },
        )
        try {
            factory.createSocket("127.0.0.1", 1)
            fail("expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
        }
    }

    @Test
    fun okHttp_callsNoArgCreateSocket_andProtectsBeforeTcpConnect() {
        val events = CopyOnWriteArrayList<String>()
        ServerSocket(0).use { server ->
            val port = server.localPort
            val acceptor = Thread {
                try {
                    server.accept().use { }
                } catch (_: IOException) {
                }
            }.also { it.isDaemon = true; it.start() }

            val factory = ProtectingSocketFactory(
                delegate = RecordingSocketFactory(events),
                protect = {
                    events += "protect"
                    true
                },
            )
            val client = OkHttpClient.Builder()
                .socketFactory(factory)
                .connectTimeout(2, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build()

            try {
                client.newCall(
                    Request.Builder().url("http://127.0.0.1:$port/").build()
                ).execute().close()
            } catch (_: IOException) {
                // TCP connect/order is under test; HTTP framing may fail after accept+close.
            }

            assertTrue(
                "OkHttp must use no-arg createSocket then connect after protect; events=$events",
                events.indexOf("protect") >= 0 &&
                    events.indexOf("connect") > events.indexOf("protect") &&
                    events.indexOf("create") < events.indexOf("protect"),
            )
            assertFalse("connected overload must not be used", "create_connected" in events)

            acceptor.join(2_000)
        }
    }

    /**
     * Records create/bind/connect without changing Socket semantics needed by OkHttp.
     */
    private class RecordingSocketFactory(
        private val events: MutableList<String>,
    ) : SocketFactory() {
        override fun createSocket(): Socket {
            events += "create"
            return RecordingSocket(events)
        }

        override fun createSocket(host: String, port: Int): Socket {
            events += "create_connected"
            return Socket(host, port)
        }

        override fun createSocket(
            host: String,
            port: Int,
            localHost: InetAddress,
            localPort: Int,
        ): Socket {
            events += "create_connected"
            return Socket(host, port, localHost, localPort)
        }

        override fun createSocket(host: InetAddress, port: Int): Socket {
            events += "create_connected"
            return Socket(host, port)
        }

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int,
        ): Socket {
            events += "create_connected"
            return Socket(address, port, localAddress, localPort)
        }
    }

    private class RecordingSocket(
        private val events: MutableList<String>,
    ) : Socket() {
        override fun bind(bindpoint: SocketAddress?) {
            events += "bind"
            super.bind(bindpoint)
        }

        override fun connect(endpoint: SocketAddress?) {
            events += "connect"
            super.connect(endpoint)
        }

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            events += "connect"
            super.connect(endpoint, timeout)
        }
    }
}
