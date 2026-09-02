package com.imkolganov.datagate.vpn

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the resolver that [OpenVpn3Client] and [com.imkolganov.datagate.vpn.xray.XrayVpnService]
 * hand to [VpnBypassApps]: it is read per `establish()`, so what it returns at that moment is what
 * the tunnel gets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SplitTunnelSessionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SplitTunnelStore.clear(context)
    }

    @Test
    fun reEstablishAfterAnEdit_appliesTheCurrentListNotTheSessionSnapshot() {
        SplitTunnelStore.setEnabled(context, true)
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app"))
        val resolver = SplitTunnelSession.bypassAppsResolver(context)

        val firstEstablish = resolver()
        // Edit made while the tunnel is up, then OpenVPN reconnects and rebuilds the TUN.
        SplitTunnelStore.setBypassPackages(context, listOf("com.beta.app", "com.gamma.app"))
        val reEstablish = resolver()

        assertEquals(listOf("com.alpha.app"), firstEstablish)
        assertEquals(listOf("com.beta.app", "com.gamma.app"), reEstablish)
    }

    @Test
    fun rapidEditsThenReconnect_applyOnlyTheFinalState() {
        SplitTunnelStore.setEnabled(context, true)
        val resolver = SplitTunnelSession.bypassAppsResolver(context)

        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app"))
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app", "com.beta.app"))
        SplitTunnelStore.setBypassPackages(context, listOf("com.gamma.app"))

        assertEquals(
            "Reconnect must see the last write, never an intermediate selection",
            listOf("com.gamma.app"),
            resolver(),
        )
    }

    @Test
    fun disablingWhileConnected_emptiesTheListOnTheNextEstablish() {
        SplitTunnelStore.setEnabled(context, true)
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app"))
        val resolver = SplitTunnelSession.bypassAppsResolver(context)
        assertEquals(listOf("com.alpha.app"), resolver())

        SplitTunnelStore.setEnabled(context, false)

        assertTrue(
            "A stored selection must not leak traffic out of the tunnel once the switch is off",
            resolver().isEmpty(),
        )
    }

    @Test
    fun masterSwitchOff_resolvesEmptyEvenWithStoredSelection() {
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app", "com.beta.app"))

        assertTrue(SplitTunnelStore.getSettings(context).bypassPackages.isNotEmpty())
        assertTrue(SplitTunnelSession.resolveBypassApps(context).isEmpty())
    }

    @Test
    fun nothingSelected_resolvesEmptyEvenWhenEnabled() {
        SplitTunnelStore.setEnabled(context, true)

        assertTrue(SplitTunnelSession.resolveBypassApps(context).isEmpty())
    }

    @Test
    fun resolvedListNeverContainsOwnPackage() {
        SplitTunnelStore.setEnabled(context, true)
        SplitTunnelStore.setBypassPackages(context, listOf(context.packageName, "com.alpha.app"))

        assertEquals(listOf("com.alpha.app"), SplitTunnelSession.resolveBypassApps(context))
    }
}
