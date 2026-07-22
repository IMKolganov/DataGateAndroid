package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class IpListPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            // Reset to known defaults so tests do not leak DataStore state across cases.
            IpListPreferences.saveSettings(
                context = context,
                sourceUrls = IpListPreferences.DEFAULT_SOURCE_URLS,
                updateFrequency = IpListUpdateFrequency.DAILY,
                coverageMode = IpListCoverageMode.FULL,
                android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                cidrListsEnabled = true,
                priorityUrls = IpListPreferences.DEFAULT_PRIORITY_URLS,
                safeRouteLimitEnabled = true,
            )
            IpListPreferences.saveStatus(
                context = context,
                routeCount = 0,
                reachedRouteLimit = false,
                priorityRouteCount = 0,
            )
        }
    }

    @Test
    fun defaultPrioritySourceUrl_pointsAtImKolganovGitHubRawList() {
        assertEquals(
            "https://raw.githubusercontent.com/IMKolganov/DataGateAndroid/main/ip-lists/ru_priority_sites.txt",
            IpListPreferences.DEFAULT_PRIORITY_SOURCE_URL,
        )
        assertEquals(
            listOf(IpListPreferences.DEFAULT_PRIORITY_SOURCE_URL),
            IpListPreferences.DEFAULT_PRIORITY_URLS,
        )
    }

    @Test
    fun getSettings_returnsDefaultPriorityUrlsInitially() = runBlocking {
        val settings = IpListPreferences.getSettings(context)
        assertEquals(IpListPreferences.DEFAULT_PRIORITY_URLS, settings.priorityUrls)
        assertTrue(settings.safeRouteLimitEnabled)
        assertEquals(IpListPreferences.DEFAULT_SOURCE_URLS, settings.sourceUrls)
    }

    @Test
    fun saveSettings_persistsPriorityUrlsAndSafeRouteLimit() = runBlocking {
        val customPriority = listOf("https://example.com/priority.txt")
        IpListPreferences.saveSettings(
            context = context,
            sourceUrls = IpListPreferences.DEFAULT_SOURCE_URLS,
            updateFrequency = IpListUpdateFrequency.WEEKLY,
            coverageMode = IpListCoverageMode.FAST,
            android12OvpnRouteLimit = 500,
            cidrListsEnabled = true,
            priorityUrls = customPriority,
            safeRouteLimitEnabled = false,
        )

        val settings = IpListPreferences.getSettings(context)
        assertEquals(customPriority, settings.priorityUrls)
        assertFalse(settings.safeRouteLimitEnabled)
        assertEquals(IpListUpdateFrequency.WEEKLY, settings.updateFrequency)
        assertEquals(IpListCoverageMode.FAST, settings.coverageMode)
    }

    @Test
    fun saveSettings_emptyPriorityUrls_decodeBackToDefaultGitHubList() = runBlocking {
        // Intentional: clearing all priority URLs must not disable the curated default list.
        IpListPreferences.saveSettings(
            context = context,
            sourceUrls = IpListPreferences.DEFAULT_SOURCE_URLS,
            updateFrequency = IpListUpdateFrequency.DAILY,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            cidrListsEnabled = true,
            priorityUrls = emptyList(),
            safeRouteLimitEnabled = true,
        )

        val settings = IpListPreferences.getSettings(context)
        assertEquals(IpListPreferences.DEFAULT_PRIORITY_URLS, settings.priorityUrls)
    }

    @Test
    fun saveStatus_persistsPriorityRouteCount() = runBlocking {
        IpListPreferences.saveStatus(
            context = context,
            routeCount = 1200,
            reachedRouteLimit = true,
            priorityRouteCount = 2,
        )

        val status = IpListPreferences.getStatus(context)
        assertEquals(1200, status.loadedRouteCount)
        assertEquals(2, status.priorityRouteCount)
        assertTrue(status.reachedRouteLimit)
    }

    @Test
    fun savePriorityRouteCount_updatesOnlyPriorityField() = runBlocking {
        IpListPreferences.saveStatus(
            context = context,
            routeCount = 100,
            reachedRouteLimit = false,
            priorityRouteCount = 1,
        )
        IpListPreferences.savePriorityRouteCount(context, 5)

        val status = IpListPreferences.getStatus(context)
        assertEquals(100, status.loadedRouteCount)
        assertEquals(5, status.priorityRouteCount)
    }

    @Test
    fun priorityCache_manualMode_refreshesOnlyWhenBlank() = runBlocking {
        val settings = IpListPreferences.getSettings(context).copy(
            updateFrequency = IpListUpdateFrequency.MANUAL,
        )
        IpListPreferences.saveCachedPriorityList(context, content = "", priorityRouteCount = 0)
        assertTrue(IpListPreferences.shouldRefreshCachedPriorityList(context, settings))

        IpListPreferences.saveCachedPriorityList(
            context,
            content = "203.0.113.0/24\n",
            priorityRouteCount = 1,
        )
        assertFalse(IpListPreferences.shouldRefreshCachedPriorityList(context, settings))
        assertEquals("203.0.113.0/24", IpListPreferences.getCachedPriorityList(context)?.trim())
    }

    @Test
    fun priorityCache_dailyMode_refreshesWhenStaleOrMissing() = runBlocking {
        val settings = IpListPreferences.getSettings(context).copy(
            updateFrequency = IpListUpdateFrequency.DAILY,
        )
        IpListPreferences.saveCachedPriorityList(context, content = "", priorityRouteCount = 0)
        assertTrue(IpListPreferences.shouldRefreshCachedPriorityList(context, settings))

        IpListPreferences.saveCachedPriorityList(
            context,
            content = "198.51.100.0/24\n",
            priorityRouteCount = 1,
        )
        assertFalse(
            "Fresh priority cache must not refetch on every connect",
            IpListPreferences.shouldRefreshCachedPriorityList(context, settings),
        )
    }
}
