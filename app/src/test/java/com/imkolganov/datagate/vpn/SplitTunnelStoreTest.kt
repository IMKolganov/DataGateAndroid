package com.imkolganov.datagate.vpn

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SplitTunnelStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SplitTunnelStore.clear(context)
    }

    @Test
    fun defaults_areOffWithNoBypassApps() {
        val settings = SplitTunnelStore.getSettings(context)

        assertFalse(settings.enabled)
        assertTrue(settings.bypassPackages.isEmpty())
    }

    @Test
    fun setBypassPackages_roundTripsSortedAndDeduplicated() {
        SplitTunnelStore.setBypassPackages(
            context,
            listOf("com.zeta.app", "com.alpha.app", "com.zeta.app"),
        )

        assertEquals(
            listOf("com.alpha.app", "com.zeta.app"),
            SplitTunnelStore.getSettings(context).bypassPackages,
        )
    }

    @Test
    fun setBypassPackages_neverStoresOwnPackage() {
        SplitTunnelStore.setBypassPackages(context, listOf(context.packageName, "com.alpha.app"))

        assertEquals(
            listOf("com.alpha.app"),
            SplitTunnelStore.getSettings(context).bypassPackages,
        )
    }

    @Test
    fun emptySelection_clearsStoredList() {
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app"))
        SplitTunnelStore.setBypassPackages(context, emptyList())

        assertTrue(SplitTunnelStore.getSettings(context).bypassPackages.isEmpty())
    }

    /**
     * Writes the on-disk value directly to simulate a list stored before the sanitize rules, or
     * hand-edited prefs: reading must filter as strictly as writing does.
     */
    @Test
    fun getSettings_sanitizesOwnPackageAndBlanksAlreadyOnDisk() {
        context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
            .edit()
            .putString(
                "split_tunnel_bypass_packages",
                listOf(
                    "com.zeta.app",
                    "",
                    "   ",
                    context.packageName,
                    " com.alpha.app ",
                    "com.zeta.app",
                ).joinToString("\n"),
            )
            .commit()

        assertEquals(
            listOf("com.alpha.app", "com.zeta.app"),
            SplitTunnelStore.getSettings(context).bypassPackages,
        )
    }

    @Test
    fun enabledFlagIsIndependentOfTheList() {
        SplitTunnelStore.setEnabled(context, true)

        val settings = SplitTunnelStore.getSettings(context)
        assertTrue("The switch can be on before any app is chosen", settings.enabled)
        assertTrue(settings.bypassPackages.isEmpty())
    }

    /** Turning the feature off is not "forget my apps": re-enabling must restore the selection. */
    @Test
    fun togglingEnabledOffAndOn_keepsTheSelection() {
        SplitTunnelStore.setEnabled(context, true)
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app", "com.beta.app"))

        SplitTunnelStore.setEnabled(context, false)
        SplitTunnelStore.setEnabled(context, true)

        assertEquals(
            listOf("com.alpha.app", "com.beta.app"),
            SplitTunnelStore.getSettings(context).bypassPackages,
        )
    }

    /** This store shares the `vpn_state` prefs file with [VpnController] and server selection. */
    @Test
    fun clear_leavesUnrelatedVpnStateKeysAlone() {
        val prefs = context.getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_server_name", "Frankfurt").commit()
        SplitTunnelStore.setEnabled(context, true)
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app"))

        SplitTunnelStore.clear(context)

        assertEquals("Frankfurt", prefs.getString("selected_server_name", null))
    }

    @Test
    fun clear_removesEnabledFlagAndList() {
        SplitTunnelStore.setEnabled(context, true)
        SplitTunnelStore.setBypassPackages(context, listOf("com.alpha.app"))

        SplitTunnelStore.clear(context)

        val settings = SplitTunnelStore.getSettings(context)
        assertFalse(settings.enabled)
        assertTrue(settings.bypassPackages.isEmpty())
    }
}
