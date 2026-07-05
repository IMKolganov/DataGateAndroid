package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnLinkProtocolTest {

    @Test
    fun configProtoLine_matchesOpenVpnTransport() {
        assertEquals("proto tcp-client", VpnLinkProtocol.TCP.configProtoLine())
        assertEquals("proto udp", VpnLinkProtocol.UDP.configProtoLine())
    }

    @Test
    fun fromOvpnConfigContent_detectsUdpVariants() {
        val config = """
            client
            # proto tcp-client
            proto udp4
            remote example.com 1194
        """.trimIndent()

        assertEquals(VpnLinkProtocol.UDP, VpnLinkProtocol.fromOvpnConfigContent(config))
    }

    @Test
    fun fromOvpnConfigContent_detectsTcpVariants() {
        val config = """
            client
            proto tcp-client
        """.trimIndent()

        assertEquals(VpnLinkProtocol.TCP, VpnLinkProtocol.fromOvpnConfigContent(config))
    }

    @Test
    fun fromOvpnConfigContent_ignoresCommentedProtoLine() {
        val config = """
            client
            ; proto udp
            proto tcp
        """.trimIndent()

        assertEquals(VpnLinkProtocol.TCP, VpnLinkProtocol.fromOvpnConfigContent(config))
    }

    @Test
    fun fromOvpnConfigContent_defaultsToTcpWhenMissing() {
        val config = "client\nremote 127.0.0.1 1194\n"
        assertEquals(VpnLinkProtocol.TCP, VpnLinkProtocol.fromOvpnConfigContent(config))
    }

    @Test
    fun fromIntentExtra_parsesUdpAndDefaultsToTcp() {
        assertEquals(VpnLinkProtocol.UDP, VpnLinkProtocol.fromIntentExtra("udp"))
        assertEquals(VpnLinkProtocol.UDP, VpnLinkProtocol.fromIntentExtra("UDP"))
        assertEquals(VpnLinkProtocol.TCP, VpnLinkProtocol.fromIntentExtra("tcp"))
        assertEquals(VpnLinkProtocol.TCP, VpnLinkProtocol.fromIntentExtra(null))
        assertEquals(VpnLinkProtocol.TCP, VpnLinkProtocol.fromIntentExtra("unknown"))
    }

    @Test
    fun intentValue_isLowercaseName() {
        assertEquals("tcp", VpnLinkProtocol.TCP.intentValue())
        assertEquals("udp", VpnLinkProtocol.UDP.intentValue())
    }
}
