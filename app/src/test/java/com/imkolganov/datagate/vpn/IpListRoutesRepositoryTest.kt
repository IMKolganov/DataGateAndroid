package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class IpListRoutesRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            IpListPreferences.saveSettings(
                context = context,
                sourceUrls = IpListPreferences.DEFAULT_SOURCE_URLS,
                updateFrequency = IpListUpdateFrequency.MANUAL,
                coverageMode = IpListCoverageMode.FULL,
                android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                cidrListsEnabled = true,
                priorityUrls = IpListPreferences.DEFAULT_PRIORITY_URLS,
                safeRouteLimitEnabled = true,
            )
            // Clear general + priority caches so MANUAL mode re-fetches in each test.
            IpListPreferences.saveCachedList(
                context = context,
                content = "",
                routeCount = 0,
                reachedRouteLimit = false,
                priorityRouteCount = 0,
            )
            IpListPreferences.saveCachedPriorityList(
                context = context,
                content = "",
                priorityRouteCount = 0,
            )
        }
    }

    @Test
    fun getRoutesForConnection_cidrDisabled_returnsEmptyLists() = runBlocking {
        IpListPreferences.setCidrListsEnabled(context, false)
        val repo = IpListRoutesRepository(context, OkHttpClient())

        val routes = repo.getRoutesForConnection()

        assertTrue(routes.generalRoutes.isEmpty())
        assertTrue(routes.priorityRoutes.isEmpty())
    }

    @Test
    fun getRoutesForConnection_fetchesGeneralAndPriorityFromHttp() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("10.0.0.0/8\n"))
            server.enqueue(MockResponse().setBody("203.0.113.0/24\n"))
            server.start()

            val generalUrl = server.url("/general.txt").toString()
            val priorityUrl = server.url("/priority.txt").toString()
            IpListPreferences.saveSettings(
                context = context,
                sourceUrls = listOf(generalUrl),
                updateFrequency = IpListUpdateFrequency.MANUAL,
                coverageMode = IpListCoverageMode.FULL,
                android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                cidrListsEnabled = true,
                priorityUrls = listOf(priorityUrl),
                safeRouteLimitEnabled = true,
            )

            val repo = IpListRoutesRepository(context, OkHttpClient())
            val routes = repo.getRoutesForConnection()

            assertEquals(listOf(Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8)), routes.generalRoutes)
            assertEquals(
                listOf(Ipv4CidrRoute("203.0.113.0", "255.255.255.0", 24)),
                routes.priorityRoutes,
            )
            val status = IpListPreferences.getStatus(context)
            assertEquals(1, status.loadedRouteCount)
            assertEquals(1, status.priorityRouteCount)

            // Second connect must use priority cache (no extra HTTP) while MANUAL still
            // re-fetches blank-cleared general… cache is warm after first success, so MANUAL
            // keeps cached general too until Update now / frequency refresh.
            server.enqueue(MockResponse().setBody("should-not-be-fetched\n"))
            val again = repo.getRoutesForConnection()
            assertEquals(routes.generalRoutes, again.generalRoutes)
            assertEquals(routes.priorityRoutes, again.priorityRoutes)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun getRoutesForConnection_priorityHttpFails_usesBundledAssetFallback() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("10.0.0.0/8\n"))
            server.enqueue(MockResponse().setResponseCode(500))
            server.start()

            val generalUrl = server.url("/general.txt").toString()
            val priorityUrl = server.url("/priority.txt").toString()
            IpListPreferences.saveSettings(
                context = context,
                sourceUrls = listOf(generalUrl),
                updateFrequency = IpListUpdateFrequency.MANUAL,
                coverageMode = IpListCoverageMode.FULL,
                android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                cidrListsEnabled = true,
                priorityUrls = listOf(priorityUrl),
                safeRouteLimitEnabled = true,
            )

            val repo = IpListRoutesRepository(context, OkHttpClient())
            val routes = repo.getRoutesForConnection()

            assertEquals(1, routes.generalRoutes.size)
            assertTrue(
                "Expected bundled priority fallback routes, got ${routes.priorityRoutes}",
                routes.priorityRoutes.isNotEmpty(),
            )
            assertTrue(
                routes.priorityRoutes.any { it.toCidrString() == "185.73.192.0/22" },
            )
        }
    }

    @Test
    fun getRoutesForConnection_partialGeneralUrlFailure_stillUsesSuccessfulUrl() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("10.0.0.0/8\n"))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("203.0.113.0/24\n"))
            server.start()

            val okGeneral = server.url("/ok.txt").toString()
            val badGeneral = server.url("/bad.txt").toString()
            val priorityUrl = server.url("/priority.txt").toString()
            IpListPreferences.saveSettings(
                context = context,
                sourceUrls = listOf(okGeneral, badGeneral),
                updateFrequency = IpListUpdateFrequency.MANUAL,
                coverageMode = IpListCoverageMode.FULL,
                android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                cidrListsEnabled = true,
                priorityUrls = listOf(priorityUrl),
                safeRouteLimitEnabled = true,
            )

            val repo = IpListRoutesRepository(context, OkHttpClient())
            val routes = repo.getRoutesForConnection()

            assertEquals(listOf(Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8)), routes.generalRoutes)
            assertEquals(
                listOf(Ipv4CidrRoute("203.0.113.0", "255.255.255.0", 24)),
                routes.priorityRoutes,
            )
        }
    }

    @Test
    fun updateNow_fetchesPriorityAndPersistsBothCounts() = runBlocking {
        MockWebServer().use { server ->
            // updateNow loads priority first, then general.
            server.enqueue(MockResponse().setBody("198.51.100.0/24\n"))
            server.enqueue(MockResponse().setBody("10.0.0.0/8\n11.0.0.0/8\n"))
            server.start()

            val generalUrl = server.url("/general.txt").toString()
            val priorityUrl = server.url("/priority.txt").toString()
            IpListPreferences.saveSettings(
                context = context,
                sourceUrls = listOf(generalUrl),
                updateFrequency = IpListUpdateFrequency.MANUAL,
                coverageMode = IpListCoverageMode.FULL,
                android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                cidrListsEnabled = true,
                priorityUrls = listOf(priorityUrl),
                safeRouteLimitEnabled = true,
            )

            val repo = IpListRoutesRepository(context, OkHttpClient())
            val result = repo.updateNow()

            assertNull(result.error)
            assertEquals(2, result.routeCount)
            assertEquals(1, result.priorityRouteCount)
            assertEquals(false, result.usedFallback)

            val status = IpListPreferences.getStatus(context)
            assertEquals(2, status.loadedRouteCount)
            assertEquals(1, status.priorityRouteCount)
        }
    }

    @Test
    fun updateNow_generalFails_stillReportsPriorityFromHttp() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("198.51.100.0/24\n"))
            server.enqueue(MockResponse().setResponseCode(503))
            server.start()

            val generalUrl = server.url("/general.txt").toString()
            val priorityUrl = server.url("/priority.txt").toString()
            IpListPreferences.saveSettings(
                context = context,
                sourceUrls = listOf(generalUrl),
                updateFrequency = IpListUpdateFrequency.MANUAL,
                coverageMode = IpListCoverageMode.FULL,
                android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
                cidrListsEnabled = true,
                priorityUrls = listOf(priorityUrl),
                safeRouteLimitEnabled = true,
            )

            val repo = IpListRoutesRepository(context, OkHttpClient())
            val result = repo.updateNow()

            assertTrue(result.error != null)
            assertEquals(1, result.priorityRouteCount)
            // Offline assets may supply general routes when cache is empty.
            assertTrue(result.usedFallback)
            assertTrue(result.routeCount > 0)
        }
    }
}
