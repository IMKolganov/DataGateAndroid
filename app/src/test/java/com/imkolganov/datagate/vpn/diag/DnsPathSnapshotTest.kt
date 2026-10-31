package com.imkolganov.datagate.vpn.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPathSnapshotTest {

    @Test
    fun eventDetails_skipsEmptyDnsListAsNull() {
        val empty = DnsPathSnapshot(
            vpnIp = "10.8.0.2",
            vpnDns = emptyList(),
            privateDnsMode = "off",
            privateDnsName = null,
            iface = "tun0",
            hasVpnTransport = true,
            activeTransport = "vpn",
        ).eventDetails()
        assertEquals("10.8.0.2", empty["vpnIp"])
        assertNull(empty["vpnDns"])
        assertEquals("off", empty["privateDnsMode"])
        assertEquals("tun0", empty["iface"])

        val withDns = DnsPathSnapshot(
            vpnIp = null,
            vpnDns = listOf("8.8.8.8", "1.1.1.1"),
            privateDnsMode = null,
            privateDnsName = null,
            iface = null,
            hasVpnTransport = false,
            activeTransport = "wifi",
        ).eventDetails()
        assertEquals("8.8.8.8,1.1.1.1", withDns["vpnDns"])
    }
}