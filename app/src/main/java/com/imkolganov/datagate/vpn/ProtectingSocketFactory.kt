package com.imkolganov.datagate.vpn

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import javax.net.SocketFactory

/**
 * OkHttp plain [SocketFactory]: [android.net.VpnService.protect] runs in [createSocket]
 * **before** OkHttp calls [Socket.connect].
 *
 * Only the no-argument [createSocket] overload is supported. OkHttp 5.x
 * [okhttp3.internal.connection.ConnectPlan] uses that path, then connects itself.
 * Connected overloads are disabled so a socket cannot be protected after connect.
 *
 * Local [Socket.bind] to an ephemeral port is a **compatibility workaround** so
 * [android.net.VpnService.protect] has a valid FD (historical Android/OkHttp reports).
 * It is not claimed to be required on every device/API; it does not select a physical
 * network interface (wildcard local address is expected).
 *
 * [socketId] is local to this factory's createSocket() only — not correlated with OkHttp
 * EventListener call.id (OkHttp does not expose a reliable link).
 */
class ProtectingSocketFactory(
    private val delegate: SocketFactory,
    private val protect: (Socket) -> Boolean,
    private val log: (String) -> Unit = {},
) : SocketFactory() {

    private val nextSocketId = AtomicLong(0)

    override fun createSocket(): Socket {
        val socketId = nextSocketId.incrementAndGet()
        val socket = delegate.createSocket()
        try {
            if (socket.isConnected) {
                throw IOException("SocketFactory returned an already connected socket")
            }

            // Compatibility workaround for protect() needing a bound FD; not a proven
            // universal requirement. Does not bind to a specific underlying Network.
            if (!socket.isBound) {
                socket.bind(InetSocketAddress(0))
            }

            val protectedOk = protect(socket)

            runCatching {
                log(
                    "socket.id=$socketId event=protect " +
                        "socket.role=wss_egress protect.layer=plain phase=before_connect " +
                        "result=$protectedOk " +
                        "bound=${socket.isBound} " +
                        "connected=${socket.isConnected} " +
                        "closed=${socket.isClosed} " +
                        "thread=${Thread.currentThread().name}"
                )
            }

            if (!protectedOk) {
                throw IOException("VpnService.protect failed for OkHttp plain socket")
            }

            return socket
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: IOException) {
                // Preserve the original exception.
            }
            throw e
        }
    }

    override fun createSocket(host: String, port: Int): Socket = unsupportedConnectedOverload()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = unsupportedConnectedOverload()

    override fun createSocket(host: InetAddress, port: Int): Socket = unsupportedConnectedOverload()

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = unsupportedConnectedOverload()

    private fun unsupportedConnectedOverload(): Nothing {
        throw UnsupportedOperationException(
            "Connected createSocket overloads are disabled because " +
                "the socket must be protected before connect()"
        )
    }
}
