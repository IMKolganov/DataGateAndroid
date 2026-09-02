package com.imkolganov.datagate.vpn

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The picker offers apps by INTERNET permission, not by whether they have a launcher entry: a
 * background-only app can still be the one breaking under VPN.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class InstalledAppsCatalogTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun onlyAppsRequestingInternetAreOffered() {
        installApp("com.networked.app", "Networked", internet = true)
        installApp("com.offline.app", "Offline", internet = false)

        val packages = InstalledAppsCatalog.loadNetworkApps(context).map { it.packageName }

        assertTrue(packages.contains("com.networked.app"))
        assertFalse(
            "An app without INTERNET has no traffic to keep out of the tunnel",
            packages.contains("com.offline.app"),
        )
    }

    @Test
    fun appWithoutLauncherEntryIsStillOffered() {
        installApp("com.background.sync", "Background Sync", internet = true, launcher = false)

        val packages = InstalledAppsCatalog.loadNetworkApps(context).map { it.packageName }

        assertTrue(
            "Filtering by launcher intent would hide networked background apps",
            packages.contains("com.background.sync"),
        )
    }

    @Test
    fun appWithNullRequestedPermissionsIsSkipped() {
        installApp("com.nopermissions.app", "No Permissions", internet = null)

        val packages = InstalledAppsCatalog.loadNetworkApps(context).map { it.packageName }

        assertFalse(packages.contains("com.nopermissions.app"))
    }

    @Test
    fun ownPackageIsNeverOffered() {
        val packages = InstalledAppsCatalog.loadNetworkApps(context).map { it.packageName }

        assertFalse(packages.contains(context.packageName))
    }

    @Test
    fun appsAreSortedByLabelIgnoringCase() {
        installApp("com.b.app", "beta", internet = true)
        installApp("com.a.app", "Alpha", internet = true)
        installApp("com.c.app", "Gamma", internet = true)

        val labels = InstalledAppsCatalog.loadNetworkApps(context)
            .map { it.label }
            .filter { it in setOf("Alpha", "beta", "Gamma") }

        assertEquals(listOf("Alpha", "beta", "Gamma"), labels)
    }

    @Test
    fun systemAppsAreOfferedButFlagged() {
        installApp("com.system.app", "System App", internet = true, system = true)
        installApp("com.user.app", "User App", internet = true, system = false)

        val apps = InstalledAppsCatalog.loadNetworkApps(context).associateBy { it.packageName }

        assertTrue(apps.getValue("com.system.app").isSystemApp)
        assertFalse(apps.getValue("com.user.app").isSystemApp)
    }

    private fun installApp(
        packageName: String,
        label: String,
        internet: Boolean?,
        launcher: Boolean = true,
        system: Boolean = false,
    ) {
        val applicationInfo = ApplicationInfo().apply {
            this.packageName = packageName
            name = label
            nonLocalizedLabel = label
            if (system) flags = flags or ApplicationInfo.FLAG_SYSTEM
        }
        val packageInfo = PackageInfo().apply {
            this.packageName = packageName
            this.applicationInfo = applicationInfo
            requestedPermissions = when (internet) {
                null -> null
                true -> arrayOf(Manifest.permission.INTERNET)
                false -> arrayOf(Manifest.permission.VIBRATE)
            }
        }
        shadowOf(context.packageManager).installPackage(packageInfo)
        if (launcher) {
            shadowOf(context.packageManager).addActivityIfNotPresent(
                android.content.ComponentName(packageName, "$packageName.MainActivity"),
            )
        }
    }
}
