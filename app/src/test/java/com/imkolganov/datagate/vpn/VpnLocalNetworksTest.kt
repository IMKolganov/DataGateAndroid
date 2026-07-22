package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class VpnLocalNetworksTest {
    @Test
    fun networkPrefixCidr_masksIpv4() {
        val host = InetAddress.getByName("10.0.2.15")
        assertEquals("10.0.2.0/24", VpnLocalNetworks.networkPrefixCidr(host, 24))
        assertEquals("10.0.0.0/16", VpnLocalNetworks.networkPrefixCidr(host, 16))
    }

    @Test
    fun networkPrefixCidr_rejectsBadPrefix() {
        val host = InetAddress.getByName("192.168.1.10")
        assertNull(VpnLocalNetworks.networkPrefixCidr(host, 33))
        assertNull(VpnLocalNetworks.networkPrefixCidr(host, -1))
    }
}
