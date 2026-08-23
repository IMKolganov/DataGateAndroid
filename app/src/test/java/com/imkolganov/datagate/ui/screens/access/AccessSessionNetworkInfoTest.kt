package com.imkolganov.datagate.ui.screens.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessSessionNetworkInfoTest {

    private val servers = listOf(
        AccessContract.ServerItem(
            id = 1,
            name = "Alpha",
            protocol = "udp",
            isOnline = true,
            isEnableWss = true,
            uptimeText = null,
            openVpnVersionText = null,
            totalInText = null,
            totalOutText = null,
            serverRemoteIp = " 203.0.113.10 "
        ),
        AccessContract.ServerItem(
            id = 2,
            name = "Beta",
            protocol = "udp",
            isOnline = true,
            isEnableWss = true,
            uptimeText = null,
            openVpnVersionText = null,
            totalInText = null,
            totalOutText = null,
            serverRemoteIp = ""
        )
    )

    @Test
    fun resolveExternalIp_returnsTrimmedIpForSessionServer() {
        assertEquals("203.0.113.10", AccessSessionNetworkInfo.resolveExternalIp(1, servers))
    }

    @Test
    fun resolveExternalIp_nullWhenMissingOrBlank() {
        assertNull(AccessSessionNetworkInfo.resolveExternalIp(null, servers))
        assertNull(AccessSessionNetworkInfo.resolveExternalIp(2, servers))
        assertNull(AccessSessionNetworkInfo.resolveExternalIp(99, servers))
    }

    @Test
    fun shouldShowPrivateDnsHint_onlyWhenConnectedAndIdentityEnabled() {
        assertTrue(
            AccessSessionNetworkInfo.shouldShowPrivateDnsHint(
                vpnConnected = true,
                dnsIdentityEnabled = true,
            ),
        )
        assertFalse(
            AccessSessionNetworkInfo.shouldShowPrivateDnsHint(
                vpnConnected = true,
                dnsIdentityEnabled = false,
            ),
        )
        assertFalse(
            AccessSessionNetworkInfo.shouldShowPrivateDnsHint(
                vpnConnected = false,
                dnsIdentityEnabled = true,
            ),
        )
        assertFalse(
            AccessSessionNetworkInfo.shouldShowPrivateDnsHint(
                vpnConnected = false,
                dnsIdentityEnabled = false,
            ),
        )
    }
}
