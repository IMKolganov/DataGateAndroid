package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VpnTunnelSessionStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recordAndRead_persistsVpnIpAndDns() {
        VpnTunnelSessionStore.clear(context)
        VpnTunnelSessionStore.recordVpnIp(context, "10.51.15.4")
        VpnTunnelSessionStore.recordDnsServers(context, listOf("8.8.8.8", "1.1.1.1"))
        VpnTunnelSessionStore.recordDnsIdentityEnabled(context, true)

        val snapshot = VpnTunnelSessionStore.read(context)
        assertEquals("10.51.15.4", snapshot.vpnIpAddress)
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), snapshot.dnsServers)
        assertEquals(true, snapshot.dnsIdentityEnabled)
    }

    @Test
    fun clear_removesStoredSessionValues() {
        VpnTunnelSessionStore.recordVpnIp(context, "10.51.15.4")
        VpnTunnelSessionStore.recordDnsIdentityEnabled(context, true)
        VpnTunnelSessionStore.clear(context)

        val snapshot = VpnTunnelSessionStore.read(context)
        assertNull(snapshot.vpnIpAddress)
        assertEquals(emptyList<String>(), snapshot.dnsServers)
        assertEquals(false, snapshot.dnsIdentityEnabled)
    }

    @Test
    fun clear_withExpectedOwner_skipsWhenPeerEngineOwnsSession() {
        VpnTunnelSessionStore.clear(context)
        VpnTunnelSessionStore.recordDnsServers(
            context,
            listOf("9.9.9.9"),
            owner = VpnTunnelSessionStore.OWNER_OPENVPN,
        )

        // Late Xray teardown must not wipe OpenVPN session DNS.
        VpnTunnelSessionStore.clear(context, expectedOwner = VpnTunnelSessionStore.OWNER_XRAY)

        val snapshot = VpnTunnelSessionStore.read(context)
        assertEquals(listOf("9.9.9.9"), snapshot.dnsServers)
    }

    @Test
    fun clear_withExpectedOwner_clearsWhenOwnerMatches() {
        VpnTunnelSessionStore.recordDnsServers(
            context,
            listOf("172.20.0.1"),
            owner = VpnTunnelSessionStore.OWNER_XRAY,
        )
        VpnTunnelSessionStore.clear(context, expectedOwner = VpnTunnelSessionStore.OWNER_XRAY)
        assertEquals(emptyList<String>(), VpnTunnelSessionStore.read(context).dnsServers)
    }

    @Test
    fun recordDnsServers_openVpn_clearsStaleDnsIdentityFlag() {
        VpnTunnelSessionStore.clear(context)
        VpnTunnelSessionStore.recordDnsIdentityEnabled(context, true)
        assertTrue(VpnTunnelSessionStore.read(context).dnsIdentityEnabled)

        VpnTunnelSessionStore.recordDnsServers(
            context,
            listOf("8.8.8.8"),
            owner = VpnTunnelSessionStore.OWNER_OPENVPN,
        )
        assertFalse(VpnTunnelSessionStore.read(context).dnsIdentityEnabled)
        assertEquals(listOf("8.8.8.8"), VpnTunnelSessionStore.read(context).dnsServers)
    }
}
