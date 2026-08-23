package com.imkolganov.datagate.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnEngineStatusPolicyTest {

    @Test
    fun noActiveEngine_acceptsAnyEvent() {
        assertTrue(VpnEngineStatusPolicy.shouldApplyStatusBroadcast(null, OpenVpn3Service.ENGINE_OPENVPN))
        assertTrue(VpnEngineStatusPolicy.shouldApplyStatusBroadcast(null, OpenVpn3Service.ENGINE_XRAY))
        assertTrue(VpnEngineStatusPolicy.shouldApplyStatusBroadcast("", OpenVpn3Service.ENGINE_XRAY))
    }

    @Test
    fun untaggedEvent_acceptedForCompat() {
        assertTrue(
            VpnEngineStatusPolicy.shouldApplyStatusBroadcast(OpenVpn3Service.ENGINE_XRAY, null),
        )
        assertTrue(
            VpnEngineStatusPolicy.shouldApplyStatusBroadcast(OpenVpn3Service.ENGINE_OPENVPN, ""),
        )
    }

    @Test
    fun activeXray_ignoresOpenVpnPeerDisconnect() {
        assertFalse(
            VpnEngineStatusPolicy.shouldApplyStatusBroadcast(
                OpenVpn3Service.ENGINE_XRAY,
                OpenVpn3Service.ENGINE_OPENVPN,
            ),
        )
    }

    @Test
    fun activeOpenVpn_ignoresXrayPeerDisconnect() {
        assertFalse(
            VpnEngineStatusPolicy.shouldApplyStatusBroadcast(
                OpenVpn3Service.ENGINE_OPENVPN,
                OpenVpn3Service.ENGINE_XRAY,
            ),
        )
    }

    @Test
    fun matchingEngine_accepted() {
        assertTrue(
            VpnEngineStatusPolicy.shouldApplyStatusBroadcast(
                OpenVpn3Service.ENGINE_XRAY,
                OpenVpn3Service.ENGINE_XRAY,
            ),
        )
        assertTrue(
            VpnEngineStatusPolicy.shouldApplyStatusBroadcast(
                OpenVpn3Service.ENGINE_OPENVPN,
                "openvpn",
            ),
        )
    }
}
