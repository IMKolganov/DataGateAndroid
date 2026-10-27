package com.imkolganov.datagate.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnNetworkCapChangePolicyTest {

    @Test
    fun capabilityNoiseSources_includeDefaultAndUnderlyingCap() {
        assertTrue(VpnNetworkCapChangePolicy.isCapabilityNoiseSource("CAP_CHANGED"))
        assertTrue(VpnNetworkCapChangePolicy.isCapabilityNoiseSource("UNDERLYING_CAP"))
        assertFalse(VpnNetworkCapChangePolicy.isCapabilityNoiseSource("AVAILABLE"))
        assertFalse(VpnNetworkCapChangePolicy.isCapabilityNoiseSource("UNDERLYING_AVAILABLE"))
        assertFalse(VpnNetworkCapChangePolicy.isCapabilityNoiseSource("SETTLE_CHECK"))
    }

    @Test
    fun availableAndLost_alwaysDispatch() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "AVAILABLE",
                transport = "wifi",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "wifi",
                lastUsable = true,
            ),
        )
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "LOST",
                transport = "none",
                usable = false,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "wifi",
                lastUsable = true,
            ),
        )
    }

    @Test
    fun settleCheck_alwaysDispatch() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "SETTLE_CHECK",
                transport = "vpn",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 10L,
                currentUnderlyingHandle = 10L,
            ),
        )
    }

    @Test
    fun capChanged_sameUplinkWhileConnected_suppressed() {
        assertFalse(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "CAP_CHANGED",
                transport = "wifi",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "wifi",
                lastUsable = true,
            ),
        )
    }

    @Test
    fun openVpn_capChanged_withoutHandles_stillSuppressed() {
        // OpenVPN omits underlying handles; default-network CAP noise must stay quiet.
        assertFalse(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "CAP_CHANGED",
                transport = "wifi",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "wifi",
                lastUsable = true,
                lastUnderlyingHandle = null,
                currentUnderlyingHandle = null,
            ),
        )
    }

    @Test
    fun capChanged_firstEvent_dispatches() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "CAP_CHANGED",
                transport = "wifi",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = null,
                lastUsable = null,
            ),
        )
    }

    @Test
    fun capChanged_transportChange_dispatches() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "CAP_CHANGED",
                transport = "cellular",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "wifi",
                lastUsable = true,
            ),
        )
    }

    @Test
    fun capChanged_usableBecameTrue_dispatches() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "CAP_CHANGED",
                transport = "wifi",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "wifi",
                lastUsable = false,
            ),
        )
    }

    @Test
    fun capChanged_waitingToReconnect_dispatches() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "CAP_CHANGED",
                transport = "wifi",
                usable = true,
                hasActiveSession = false,
                desiredConnection = true,
                lastTransport = "wifi",
                lastUsable = true,
            ),
        )
    }

    @Test
    fun underlyingCap_sameHandleConnected_suppressed() {
        assertFalse(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_CAP",
                transport = "vpn",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 10L,
                currentUnderlyingHandle = 10L,
            ),
        )
    }

    @Test
    fun underlyingCap_validatedFlapSameHandle_suppressed() {
        // validated/signal/bandwidth flicker must not look like a new uplink.
        assertFalse(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_CAP",
                transport = "vpn",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 42L,
                currentUnderlyingHandle = 42L,
            ),
        )
    }

    @Test
    fun underlyingCap_nullLastHandle_knownTransport_suppressedWhileConnected() {
        // After transport was seeded, CAP without a prior handle must not HEALTH-storm.
        assertFalse(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_CAP",
                transport = "vpn",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = null,
                currentUnderlyingHandle = 10L,
            ),
        )
    }

    @Test
    fun underlyingCap_handleChanged_dispatches() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_CAP",
                transport = "vpn",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 10L,
                currentUnderlyingHandle = 20L,
            ),
        )
    }

    @Test
    fun underlyingCap_usableDropped_dispatches() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_CAP",
                transport = "vpn",
                usable = false,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 10L,
                currentUnderlyingHandle = 10L,
            ),
        )
    }

    @Test
    fun underlyingCap_waitingToReconnect_dispatches() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_CAP",
                transport = "vpn",
                usable = true,
                hasActiveSession = false,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 10L,
                currentUnderlyingHandle = 10L,
            ),
        )
    }

    @Test
    fun underlyingAvailableAndLost_alwaysDispatch() {
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_AVAILABLE",
                transport = "vpn",
                usable = true,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 10L,
                currentUnderlyingHandle = 10L,
            ),
        )
        assertTrue(
            VpnNetworkCapChangePolicy.shouldDispatch(
                source = "UNDERLYING_LOST",
                transport = "vpn",
                usable = false,
                hasActiveSession = true,
                desiredConnection = true,
                lastTransport = "vpn",
                lastUsable = true,
                lastUnderlyingHandle = 10L,
                currentUnderlyingHandle = null,
            ),
        )
    }
}
