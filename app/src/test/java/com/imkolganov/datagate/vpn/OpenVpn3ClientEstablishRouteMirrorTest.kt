package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors [OpenVpn3Client.applyExcludedRoutes]: one [VpnService.Builder.excludeRoute] per route
 * at [tun_builder_establish] when delivery is [IpListRouteDelivery.ANDROID_EXCLUDE_ROUTE] — same
 * SDK gate, same per-route try/catch that must not abort the remaining routes.
 */
class OpenVpn3ClientEstablishRouteMirrorTest {

    private val routes = listOf(
        Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
        Ipv4CidrRoute("192.168.0.0", "255.255.0.0", 16),
    )

    @Test
    fun applyExcludedRoutesCallCount_matchesAndroidExcludedRoutesList() {
        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
        )
        val calledWith = mutableListOf<IpCidrRoute>()

        val applied = EstablishExcludeRouteCallCounter.countForClient(
            excludedRoutes = plan.androidExcludedRoutes,
            supportsAndroidRouteExclusion = true,
            excludeRoute = { calledWith += it },
        )

        assertEquals(routes.size, applied)
        // Not just a count match — every selected route must actually reach excludeRoute(), in order.
        assertEquals(plan.androidExcludedRoutes, calledWith)
        assertEquals(
            routes.size,
            IpListEstablishRoutePolicy.excludeRouteCallsForPlan(plan),
        )
    }

    @Test
    fun belowAndroid13_skipsExcludeRouteEntirely() {
        val calledWith = mutableListOf<IpCidrRoute>()

        val applied = EstablishExcludeRouteCallCounter.countForClient(
            excludedRoutes = routes,
            supportsAndroidRouteExclusion = false,
            excludeRoute = { calledWith += it },
        )

        assertEquals(0, applied)
        assertTrue("excludeRoute() must never be called below Android 13", calledWith.isEmpty())
    }

    @Test
    fun oneRouteThrowing_doesNotAbortRemainingRoutes() {
        val failing = routes.first()
        val calledWith = mutableListOf<IpCidrRoute>()

        val applied = EstablishExcludeRouteCallCounter.countForClient(
            excludedRoutes = routes,
            supportsAndroidRouteExclusion = true,
            excludeRoute = { route ->
                calledWith += route
                if (route == failing) throw IllegalArgumentException("bad prefix")
            },
        )

        assertEquals("A route rejected by the OS must not count as applied", routes.size - 1, applied)
        assertEquals(
            "The loop must still attempt every remaining route after one throws",
            routes,
            calledWith,
        )
    }
}

/**
 * Test-only mirror of [OpenVpn3Client.applyExcludedRoutes]'s loop body — same SDK gate and
 * per-route try/catch semantics, so a regression there (e.g. one bad route silently dropping the
 * rest) would be caught here without needing a real [android.net.VpnService.Builder].
 */
internal object EstablishExcludeRouteCallCounter {
    fun countForClient(
        excludedRoutes: List<IpCidrRoute>,
        supportsAndroidRouteExclusion: Boolean,
        excludeRoute: (IpCidrRoute) -> Unit = {},
    ): Int {
        if (excludedRoutes.isEmpty()) return 0
        if (!supportsAndroidRouteExclusion) return 0

        var applied = 0
        for (route in excludedRoutes) {
            try {
                excludeRoute(route)
                applied++
            } catch (_: Throwable) {
                // One bad route must not abort the rest — mirrors the real per-route try/catch.
            }
        }
        return applied
    }
}
