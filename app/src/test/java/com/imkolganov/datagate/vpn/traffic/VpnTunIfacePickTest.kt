package com.imkolganov.datagate.vpn.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnTunIfacePickTest {

    @Test
    fun emptyOrNonVpn_returnsNull() {
        assertNull(VpnTunIfacePick.pickName(emptyList()))
        assertNull(
            VpnTunIfacePick.pickName(
                listOf(
                    TunIfaceCandidate(isVpn = false, interfaceName = "wlan0", hasTunnelIpv4 = true),
                    TunIfaceCandidate(isVpn = true, interfaceName = null, hasTunnelIpv4 = true),
                    TunIfaceCandidate(isVpn = true, interfaceName = "  ", hasTunnelIpv4 = true),
                ),
            ),
        )
    }

    @Test
    fun prefersVpnIfaceWithTunnelIpv4() {
        val name = VpnTunIfacePick.pickName(
            listOf(
                TunIfaceCandidate(isVpn = false, interfaceName = "wlan0", hasTunnelIpv4 = true),
                TunIfaceCandidate(isVpn = true, interfaceName = "tun0", hasTunnelIpv4 = false),
                TunIfaceCandidate(isVpn = true, interfaceName = " tun1 ", hasTunnelIpv4 = true),
            ),
        )
        assertEquals("tun1", name)
    }

    @Test
    fun vpnWithoutIpv4_isFallback() {
        assertEquals(
            "tun0",
            VpnTunIfacePick.pickName(
                listOf(
                    TunIfaceCandidate(isVpn = true, interfaceName = "tun0", hasTunnelIpv4 = false),
                    TunIfaceCandidate(isVpn = true, interfaceName = "", hasTunnelIpv4 = true),
                ),
            ),
        )
    }

    @Test
    fun laterVpnWithoutIpv4_doesNotOverrideEarlierIpv4() {
        assertEquals(
            "tun0",
            VpnTunIfacePick.pickName(
                listOf(
                    TunIfaceCandidate(isVpn = true, interfaceName = "tun0", hasTunnelIpv4 = true),
                    TunIfaceCandidate(isVpn = true, interfaceName = "tun1", hasTunnelIpv4 = false),
                ),
            ),
        )
    }
}
