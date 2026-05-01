package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class IpListRouteConfigTest {
    @Test
    fun parseIpv4CidrRoutes_acceptsHostRoute32() {
        val routes = IpListRouteConfig.parseIpv4CidrRoutes("1.2.3.4/32")

        assertEquals(listOf(Ipv4CidrRoute("1.2.3.4", "255.255.255.255", 32)), routes)
    }

    @Test
    fun parseIpv4CidrRoutes_acceptsNetworkRoute24() {
        val routes = IpListRouteConfig.parseIpv4CidrRoutes("1.2.3.0/24")

        assertEquals(listOf(Ipv4CidrRoute("1.2.3.0", "255.255.255.0", 24)), routes)
    }

    @Test
    fun parseIpv4CidrRoutes_ignoresCommentsAndEmptyLines() {
        val routes = IpListRouteConfig.parseIpv4CidrRoutes(
            """
            # full-line comment

            1.2.3.4/32 # inline comment
               # indented comment
            1.2.3.0/24
            """.trimIndent()
        )

        assertEquals(
            listOf(
                Ipv4CidrRoute("1.2.3.4", "255.255.255.255", 32),
                Ipv4CidrRoute("1.2.3.0", "255.255.255.0", 24)
            ),
            routes
        )
    }

    @Test
    fun parseIpv4CidrRoutes_ignoresGarbageLines() {
        val routes = IpListRouteConfig.parseIpv4CidrRoutes(
            """
            garbage
            999.2.3.4/32
            1.2.3.4/33
            1.2.3.4/not-a-prefix
            1.2.3.4/32
            """.trimIndent()
        )

        assertEquals(listOf(Ipv4CidrRoute("1.2.3.4", "255.255.255.255", 32)), routes)
    }

    @Test
    fun parseIpv4CidrRoutes_stillReturnsOnlyIpv4() {
        val routes = IpListRouteConfig.parseIpv4CidrRoutes(
            """
            2001:db8::/32
            1.2.3.4/32
            """.trimIndent()
        )

        assertEquals(listOf(Ipv4CidrRoute("1.2.3.4", "255.255.255.255", 32)), routes)
    }

    @Test
    fun parseCidrRoutesResult_acceptsIpv6() {
        val result = IpListRouteConfig.parseCidrRoutesResult(
            """
            2001:db8:abcd:1234::1/48
            1.2.3.4/32
            """.trimIndent()
        )

        assertEquals(
            listOf(
                Ipv6CidrRoute("2001:db8:abcd:0:0:0:0:0", 48),
                Ipv4CidrRoute("1.2.3.4", "255.255.255.255", 32)
            ),
            result.routes
        )
    }

    @Test
    fun appendBypassRoutes_writesIpv6RouteDirectives() {
        val config = IpListRouteConfig.appendBypassRoutes(
            config = "client",
            routes = listOf(Ipv6CidrRoute("2001:db8:0:0:0:0:0:0", 32))
        )

        assertEquals(
            "client\n\n# DataGate IP list bypass routes\n" +
                "route-ipv6 2001:db8:0:0:0:0:0:0/32 net_gateway\n",
            config
        )
    }

    @Test
    fun selectAndroid12OvpnRoutes_prefersBroadIpv4RoutesAndLimits() {
        val narrowRoutes = (0 until 60).map {
            Ipv4CidrRoute("10.2.$it.0", "255.255.255.0", 24)
        }
        val routes = listOf(
            Ipv6CidrRoute("2001:db8:0:0:0:0:0:0", 32),
            Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
            Ipv4CidrRoute("10.1.0.0", "255.255.0.0", 16)
        ) + narrowRoutes

        val selected = IpListRouteConfig.selectAndroid12OvpnRoutes(
            routes,
            limit = IpListRouteConfig.MIN_ANDROID12_OVPN_ROUTE_LIMIT
        )

        assertEquals(
            listOf(
                Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
                Ipv4CidrRoute("10.1.0.0", "255.255.0.0", 16)
            ),
            selected.take(2)
        )
        assertEquals(IpListRouteConfig.MIN_ANDROID12_OVPN_ROUTE_LIMIT, selected.size)
    }

    @Test
    fun selectAndroid12OvpnRoutes_prefersBroadIpv4RoutesEvenBelowRouteLimit() {
        val routes = listOf(
            Ipv4CidrRoute("10.1.2.0", "255.255.255.0", 24),
            Ipv6CidrRoute("2001:db8:0:0:0:0:0:0", 32),
            Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
            Ipv4CidrRoute("10.1.0.0", "255.255.0.0", 16)
        )

        val selected = IpListRouteConfig.selectAndroid12OvpnRoutes(
            routes,
            limit = IpListRouteConfig.MAX_ANDROID12_OVPN_ROUTE_LIMIT
        )

        assertEquals(
            listOf(
                Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
                Ipv4CidrRoute("10.1.0.0", "255.255.0.0", 16),
                Ipv4CidrRoute("10.1.2.0", "255.255.255.0", 24)
            ),
            selected
        )
    }

    @Test
    fun sanitizeAndroid12OvpnRouteLimit_clampsToSupportedRange() {
        assertEquals(
            IpListRouteConfig.MIN_ANDROID12_OVPN_ROUTE_LIMIT,
            IpListRouteConfig.sanitizeAndroid12OvpnRouteLimit(1)
        )
        assertEquals(
            IpListRouteConfig.MAX_ANDROID12_OVPN_ROUTE_LIMIT,
            IpListRouteConfig.sanitizeAndroid12OvpnRouteLimit(999_999)
        )
        assertEquals(700, IpListRouteConfig.sanitizeAndroid12OvpnRouteLimit(700))
    }

    @Test
    fun appendBypassRoutesResult_doesNotGrowProfilePastHeaderLimit() {
        val oversizedConfig = "x".repeat(IpListRouteConfig.MAX_OPENVPN_PROFILE_BYTES)

        val result = IpListRouteConfig.appendBypassRoutesResult(
            config = oversizedConfig,
            routes = listOf(Ipv4CidrRoute("1.2.3.0", "255.255.255.0", 24))
        )

        assertEquals(oversizedConfig, result.config)
        assertEquals(0, result.appliedRouteCount)
        assertEquals(true, result.reachedProfileSizeLimit)
    }

    @Test
    fun appendBypassRoutesResult_appliesPartialRoutesWhenProfileLimitIsReached() {
        val route = Ipv4CidrRoute("1.2.3.0", "255.255.255.0", 24)
        val routeLineBytes = (route.toOpenVpnNetGatewayRoute() + '\n').toByteArray(Charsets.UTF_8).size
        val headerBytes = "\n\n# DataGate IP list bypass routes\n".toByteArray(Charsets.UTF_8).size
        val baseConfig = "x".repeat(IpListRouteConfig.MAX_OPENVPN_PROFILE_BYTES - headerBytes - routeLineBytes)

        val result = IpListRouteConfig.appendBypassRoutesResult(
            config = baseConfig,
            routes = listOf(
                route,
                Ipv4CidrRoute("1.2.4.0", "255.255.255.0", 24)
            )
        )

        assertEquals(1, result.appliedRouteCount)
        assertEquals(true, result.reachedProfileSizeLimit)
        assertEquals(IpListRouteConfig.MAX_OPENVPN_PROFILE_BYTES, result.config.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun prepareConnectionRoutes_onAndroid13KeepsConfigAndUsesExcludedRoutes() {
        val routes = listOf(Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8))

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true
        )

        assertEquals("client\n", plan.config)
        assertEquals(routes, plan.androidExcludedRoutes)
        assertEquals(1, plan.selectedRouteCount)
        assertEquals(1, plan.appliedRouteCount)
        assertEquals(false, plan.reachedProfileSizeLimit)
        assertEquals(IpListRouteDelivery.ANDROID_EXCLUDE_ROUTE, plan.delivery)
    }

    @Test
    fun prepareConnectionRoutes_onAndroid12WritesOvpnRoutesAndSkipsExcludedRoutes() {
        val routes = listOf(Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8))

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = false
        )

        assertEquals(emptyList<IpCidrRoute>(), plan.androidExcludedRoutes)
        assertEquals(1, plan.selectedRouteCount)
        assertEquals(1, plan.appliedRouteCount)
        assertEquals(false, plan.reachedProfileSizeLimit)
        assertEquals(IpListRouteDelivery.OVPN_PROFILE, plan.delivery)
        assertEquals(
            "client\n\n# DataGate IP list bypass routes\nroute 10.0.0.0 255.0.0.0 net_gateway\n",
            plan.config
        )
    }

    @Test
    fun prepareConnectionRoutes_onAndroid12SkipsIpv6BecauseOvpnGatewayBypassIsIpv4Only() {
        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = listOf(Ipv6CidrRoute("2001:db8:0:0:0:0:0:0", 32)),
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = false
        )

        assertEquals("client\n", plan.config)
        assertEquals(emptyList<IpCidrRoute>(), plan.androidExcludedRoutes)
        assertEquals(0, plan.selectedRouteCount)
        assertEquals(0, plan.appliedRouteCount)
        assertEquals(false, plan.reachedProfileSizeLimit)
        assertEquals(IpListRouteDelivery.OVPN_PROFILE, plan.delivery)
    }
}
