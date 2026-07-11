package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mirrors [OpenVpn3Client.applyExcludedRoutes]: one [VpnService.Builder.excludeRoute] per route
 * at [tun_builder_establish] when delivery is [IpListRouteDelivery.ANDROID_EXCLUDE_ROUTE].
 */
class OpenVpn3ClientEstablishRouteMirrorTest {

    @Test
    fun applyExcludedRoutesCallCount_matchesAndroidExcludedRoutesList() {
        val routes = listOf(
            Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
            Ipv4CidrRoute("192.168.0.0", "255.255.0.0", 16),
        )
        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
        )

        assertEquals(
            routes.size,
            EstablishExcludeRouteCallCounter.countForClient(plan.androidExcludedRoutes),
        )
        assertEquals(
            routes.size,
            IpListEstablishRoutePolicy.excludeRouteCallsForPlan(plan),
        )
    }
}

/** Test-only mirror of applyExcludedRoutes loop body. */
internal object EstablishExcludeRouteCallCounter {
    fun countForClient(excludedRoutes: List<IpCidrRoute>): Int {
        if (excludedRoutes.isEmpty()) return 0
        return excludedRoutes.size
    }
}
