package com.imkolganov.datagate.vpn

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real [VpnBypassApps.apply] loop through its injected sink, because
 * [android.net.VpnService.Builder] cannot be constructed without a live service. Runs under
 * Robolectric because the loop logs through [com.imkolganov.datagate.logger.VpnDebugLogger].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class VpnBypassAppsTest {

    private val bypassPackages = listOf("com.alpha.app", "com.beta.app", "com.gamma.app")

    @Test
    fun everySelectedPackageReachesTheBuilderInOrder() {
        val calledWith = mutableListOf<String>()

        val applied = VpnBypassApps.apply({ bypassPackages }) { calledWith += it }

        assertEquals(bypassPackages.size, applied)
        assertEquals(bypassPackages, calledWith)
    }

    @Test
    fun emptySelection_neverTouchesTheBuilder() {
        val calledWith = mutableListOf<String>()

        val applied = VpnBypassApps.apply({ emptyList() }) { calledWith += it }

        assertEquals(0, applied)
        assertTrue(calledWith.isEmpty())
    }

    @Test
    fun oneUninstalledPackage_doesNotAbortRemainingPackages() {
        val missing = bypassPackages.first()
        val calledWith = mutableListOf<String>()

        val applied = VpnBypassApps.apply({ bypassPackages }) { packageName ->
            calledWith += packageName
            if (packageName == missing) throw nameNotFound(packageName)
        }

        assertEquals(
            "A package the OS rejects must not count as applied",
            bypassPackages.size - 1,
            applied,
        )
        assertEquals(
            "The loop must still attempt every remaining package after one throws",
            bypassPackages,
            calledWith,
        )
    }

    @Test
    fun allSelectedPackagesUninstalled_collapsesToZeroWithoutThrowing() {
        val calledWith = mutableListOf<String>()

        val applied = VpnBypassApps.apply({ bypassPackages }) { packageName ->
            calledWith += packageName
            throw nameNotFound(packageName)
        }

        assertEquals("Nothing can bypass the VPN if no selected package exists", 0, applied)
        assertEquals(
            "Every package must still be attempted so one survivor would be applied",
            bypassPackages,
            calledWith,
        )
    }

    @Test
    fun selectionIsResolvedOncePerApply_soEachEstablishSeesTheCurrentList() {
        var stored = listOf("com.alpha.app")
        var resolveCount = 0
        val resolver = {
            resolveCount++
            stored
        }
        val firstEstablish = mutableListOf<String>()
        val secondEstablish = mutableListOf<String>()

        VpnBypassApps.apply(resolver) { firstEstablish += it }
        // The user edits the list while the tunnel is up, then the session re-establishes.
        stored = listOf("com.beta.app", "com.gamma.app")
        VpnBypassApps.apply(resolver) { secondEstablish += it }

        assertEquals(listOf("com.alpha.app"), firstEstablish)
        assertEquals(
            "A re-establish must apply the current list, not the one from the first establish",
            listOf("com.beta.app", "com.gamma.app"),
            secondEstablish,
        )
        assertEquals("The selection must be resolved exactly once per establish", 2, resolveCount)
    }

    private fun nameNotFound(packageName: String) =
        IllegalArgumentException("package not found: $packageName")
}
