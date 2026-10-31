package com.imkolganov.datagate.vpn.diag

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnPathHintPolicyTest {

    @Test
    fun bothOk_meansSiteOrApp() {
        assertEquals(
            VpnPathHint.SITE_OR_APP,
            VpnPathHintPolicy.fromProbes(vpnOk = true, underOk = true),
        )
    }

    @Test
    fun vpnDownUnderlyingUp_meansVpnPath() {
        assertEquals(
            VpnPathHint.VPN_PATH,
            VpnPathHintPolicy.fromProbes(vpnOk = false, underOk = true),
        )
    }

    @Test
    fun vpnOk_winsEvenIfUnderlyingFailedOrMissing() {
        assertEquals(
            VpnPathHint.SITE_OR_APP,
            VpnPathHintPolicy.fromProbes(vpnOk = true, underOk = false),
        )
        assertEquals(
            VpnPathHint.SITE_OR_APP,
            VpnPathHintPolicy.fromProbes(vpnOk = true, underOk = null),
        )
    }

    @Test
    fun bothDown_meansDeviceInternet() {
        assertEquals(
            VpnPathHint.DEVICE_INTERNET,
            VpnPathHintPolicy.fromProbes(vpnOk = false, underOk = false),
        )
        assertEquals(
            VpnPathHint.DEVICE_INTERNET,
            VpnPathHintPolicy.fromProbes(vpnOk = null, underOk = false),
        )
    }

    @Test
    fun incomplete_isUnknown() {
        assertEquals(VpnPathHint.UNKNOWN, VpnPathHintPolicy.fromProbes(null, null))
        assertEquals(VpnPathHint.UNKNOWN, VpnPathHintPolicy.fromProbes(false, null))
    }

    @Test
    fun missingUnderlyingNetwork_isNullNotFalse() {
        assertEquals(null, VpnPathHintPolicy.underlyingOk(hasUnderlyingNetwork = false, httpOk = false))
        assertEquals(true, VpnPathHintPolicy.underlyingOk(hasUnderlyingNetwork = true, httpOk = true))
        assertEquals(false, VpnPathHintPolicy.underlyingOk(hasUnderlyingNetwork = true, httpOk = false))
    }
}
