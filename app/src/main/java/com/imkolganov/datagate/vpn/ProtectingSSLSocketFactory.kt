package com.imkolganov.datagate.vpn

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class ProtectingSSLSocketFactory(
    private val delegate: SSLSocketFactory,
    private val protect: (Socket) -> Unit
) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        val sock = delegate.createSocket(s, host, port, autoClose)
        try { protect(sock) } catch (_: Throwable) {}
        return sock
    }

    override fun createSocket(host: String, port: Int): Socket {
        val sock = delegate.createSocket(host, port)
        try { protect(sock) } catch (_: Throwable) {}
        return sock
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val sock = delegate.createSocket(host, port, localHost, localPort)
        try { protect(sock) } catch (_: Throwable) {}
        return sock
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        val sock = delegate.createSocket(host, port)
        try { protect(sock) } catch (_: Throwable) {}
        return sock
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val sock = delegate.createSocket(address, port, localAddress, localPort)
        try { protect(sock) } catch (_: Throwable) {}
        return sock
    }
}
