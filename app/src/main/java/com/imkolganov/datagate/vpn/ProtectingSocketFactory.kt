package com.imkolganov.datagate.vpn

class ProtectingSocketFactory(
    private val base: javax.net.SocketFactory,
    private val protectSocket: (java.net.Socket) -> Unit
) : javax.net.SocketFactory() {

    override fun createSocket(): java.net.Socket =
        base.createSocket().also { protectSocket(it) }

    override fun createSocket(host: String, port: Int): java.net.Socket =
        base.createSocket(host, port).also { protectSocket(it) }

    override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): java.net.Socket =
        base.createSocket(host, port, localHost, localPort).also { protectSocket(it) }

    override fun createSocket(host: java.net.InetAddress, port: Int): java.net.Socket =
        base.createSocket(host, port).also { protectSocket(it) }

    override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int): java.net.Socket =
        base.createSocket(address, port, localAddress, localPort).also { protectSocket(it) }
}
