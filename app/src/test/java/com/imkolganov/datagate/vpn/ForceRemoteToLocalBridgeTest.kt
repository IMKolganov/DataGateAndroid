package com.imkolganov.datagate.vpn

import org.junit.Assert.assertTrue
import org.junit.Test

class ForceRemoteToLocalBridgeTest {
    @Test
    fun forceRemoteToLocalBridge_replacesRemoteAndProtoForTcp() {
        val input = """
            client
            remote 8.8.8.8 1194
            proto udp
            remote 9.9.9.9 443
        """.trimIndent()

        val result = forceRemoteToLocalBridge(
            original = input,
            port = 54321,
            linkProtocol = VpnLinkProtocol.TCP
        )

        assertTrue(result.contains("remote 127.0.0.1 54321"))
        assertTrue(result.contains("proto tcp-client"))
        assertTrue(!result.contains("remote 8.8.8.8 1194"))
        assertTrue(!result.contains("remote 9.9.9.9 443"))
    }

    @Test
    fun forceRemoteToLocalBridge_addsMissingRemoteAndProtoForUdp() {
        val input = "client\nverb 3\n"
        val result = forceRemoteToLocalBridge(
            original = input,
            port = 23456,
            linkProtocol = VpnLinkProtocol.UDP
        )

        assertTrue(result.startsWith("remote 127.0.0.1 23456\nproto udp\n"))
    }
}
