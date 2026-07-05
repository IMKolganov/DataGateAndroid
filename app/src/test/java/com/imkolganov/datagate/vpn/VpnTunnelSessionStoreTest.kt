package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        val snapshot = VpnTunnelSessionStore.read(context)
        assertEquals("10.51.15.4", snapshot.vpnIpAddress)
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), snapshot.dnsServers)
    }

    @Test
    fun clear_removesStoredSessionValues() {
        VpnTunnelSessionStore.recordVpnIp(context, "10.51.15.4")
        VpnTunnelSessionStore.clear(context)

        val snapshot = VpnTunnelSessionStore.read(context)
        assertNull(snapshot.vpnIpAddress)
        assertEquals(emptyList<String>(), snapshot.dnsServers)
    }
}
