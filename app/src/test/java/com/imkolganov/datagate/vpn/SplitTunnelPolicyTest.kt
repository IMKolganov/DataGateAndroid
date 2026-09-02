package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTunnelPolicyTest {

    private val selfPackage = "com.imkolganov.datagate"

    private fun app(packageName: String, label: String, system: Boolean = false) =
        InstalledAppInfo(packageName = packageName, label = label, isSystemApp = system)

    @Test
    fun sanitizePackages_dropsBlanksDuplicatesAndOwnPackage() {
        val sanitized = SplitTunnelPolicy.sanitizePackages(
            packages = listOf(
                " com.google.android.projection.gearhead ",
                "com.google.android.projection.gearhead",
                "",
                "   ",
                selfPackage,
                "com.bank.app",
            ),
            selfPackage = selfPackage,
        )

        assertEquals(listOf("com.bank.app", "com.google.android.projection.gearhead"), sanitized)
    }

    /** Trimming must happen before the own-package comparison, not after. */
    @Test
    fun sanitizePackages_dropsPaddedOwnPackage() {
        val sanitized = SplitTunnelPolicy.sanitizePackages(
            packages = listOf("  $selfPackage  ", "com.bank.app"),
            selfPackage = selfPackage,
        )

        assertEquals(listOf("com.bank.app"), sanitized)
    }

    @Test
    fun bypassPackagesForSession_isEmptyWhileFeatureIsOff() {
        val settings = SplitTunnelSettings(
            enabled = false,
            bypassPackages = listOf("com.google.android.projection.gearhead"),
        )

        assertTrue(
            "A stale list must not leak traffic out of the tunnel while the feature is off",
            SplitTunnelPolicy.bypassPackagesForSession(settings, selfPackage).isEmpty(),
        )
    }

    @Test
    fun bypassPackagesForSession_returnsSanitizedListWhenEnabled() {
        val settings = SplitTunnelSettings(
            enabled = true,
            bypassPackages = listOf("com.bank.app", selfPackage, "com.bank.app"),
        )

        assertEquals(
            listOf("com.bank.app"),
            SplitTunnelPolicy.bypassPackagesForSession(settings, selfPackage),
        )
    }

    @Test
    fun visibleApps_matchesLabelAndPackageCaseInsensitively() {
        val apps = listOf(
            app("com.google.android.projection.gearhead", "Android Auto"),
            app("com.bank.app", "Bank"),
        )

        assertEquals(
            listOf(apps[0]),
            SplitTunnelPolicy.visibleApps(apps, "auto", bypassOnly = false, bypassPackages = emptySet()),
        )
        assertEquals(
            listOf(apps[0]),
            SplitTunnelPolicy.visibleApps(apps, "GEARHEAD", bypassOnly = false, bypassPackages = emptySet()),
        )
    }

    @Test
    fun visibleApps_bypassOnlyKeepsSelectionAndPreservesCatalogOrder() {
        val apps = listOf(
            app("com.a.app", "Alpha"),
            app("com.b.app", "Beta"),
            app("com.c.app", "Gamma"),
        )

        assertEquals(
            listOf(apps[0], apps[2]),
            SplitTunnelPolicy.visibleApps(
                apps = apps,
                query = "",
                bypassOnly = true,
                bypassPackages = setOf("com.c.app", "com.a.app"),
            ),
        )
    }

    @Test
    fun matchesQuery_blankQueryMatchesEverything() {
        val target = app("com.bank.app", "Bank")

        assertTrue(SplitTunnelPolicy.matchesQuery(target, ""))
        assertTrue(SplitTunnelPolicy.matchesQuery(target, "   "))
        assertFalse(SplitTunnelPolicy.matchesQuery(target, "netflix"))
    }
}
