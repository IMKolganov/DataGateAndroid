package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpListRouteConfigTest {

    /**
     * Builds [count] valid, non-adjacent /16s so [IpCidrNormalizer] cannot sibling-merge them
     * (odd second-octet partners are absent).
     */
    private fun disjointBroadRoutes(count: Int): List<Ipv4CidrRoute> =
        (0 until count).map { i ->
            val flat = i * 2
            val a = 1 + flat / 256
            val b = flat % 256
            Ipv4CidrRoute("$a.$b.0.0", "255.255.0.0", 16)
        }

    /**
     * Non-adjacent /32s that survive normalization without collapsing into fewer prefixes.
     */
    private fun disjointHostRoutes(count: Int): List<Ipv4CidrRoute> =
        (0 until count).map { i ->
            val flat = i * 2
            val a = 10 + flat / 65_536
            val b = (flat / 256) % 256
            val c = flat % 256
            Ipv4CidrRoute("$a.$b.$c.1", "255.255.255.255", 32)
        }

    /**
     * Regression: the "IP list routes prepared" debug log always reported
     * [IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL] as the applied cap even when
     * [IpListCoverageMode.FAST] (capped at [IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FAST]) truncated
     * the list — misleading anyone debugging route counts in FAST mode.
     */
    @Test
    fun androidExcludeRouteLimitFor_fastMode_returnsFastCap() {
        assertEquals(
            IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FAST,
            IpListRouteConfig.androidExcludeRouteLimitFor(IpListCoverageMode.FAST)
        )
    }

    @Test
    fun androidExcludeRouteLimitFor_fullMode_returnsFullCap() {
        assertEquals(
            IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL,
            IpListRouteConfig.androidExcludeRouteLimitFor(IpListCoverageMode.FULL)
        )
    }

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
    fun selectAndroid13FullExcludedRoutes_capsAtLimitAndPrefersBroadPrefixes() {
        val narrowRoutes = (0 until 5_000).map {
            Ipv4CidrRoute("10.2.$it.0", "255.255.255.0", 24)
        }
        val routes = listOf(
            Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
            Ipv4CidrRoute("10.1.0.0", "255.255.0.0", 16),
        ) + narrowRoutes

        val selected = IpListRouteConfig.selectAndroid13FullExcludedRoutes(routes)

        assertEquals(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL, selected.size)
        assertEquals(Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8), selected.first())
        assertEquals(Ipv4CidrRoute("10.1.0.0", "255.255.0.0", 16), selected[1])
    }

    @Test
    fun selectAndroid13FullExcludedRoutes_priorityRoutesSurviveTruncation() {
        // A narrow priority block that would rank far outside the broadest-first cutoff on its own.
        val priorityRoute = Ipv4CidrRoute("203.0.112.0", "255.255.252.0", 22)
        val broadNoise = disjointBroadRoutes(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL)

        val selected = IpListRouteConfig.selectAndroid13FullExcludedRoutes(
            routes = broadNoise,
            priorityRoutes = listOf(priorityRoute),
        )

        assertEquals(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL, selected.size)
        assertTrue(selected.contains(priorityRoute))
    }

    @Test
    fun selectAndroid13FullExcludedRoutes_dedupesPriorityAgainstGeneralList() {
        val shared = Ipv4CidrRoute("203.0.113.0", "255.255.255.252", 22)

        val selected = IpListRouteConfig.selectAndroid13FullExcludedRoutes(
            routes = listOf(shared),
            priorityRoutes = listOf(shared),
        )

        assertEquals(listOf(shared), selected)
    }

    @Test
    fun prepareConnectionRoutes_onAndroid13Full_setsEstablishRouteLimitFlag() {
        val routes = disjointBroadRoutes(5_000)

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
        )

        assertEquals(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL, plan.androidExcludedRoutes.size)
        assertTrue(plan.reachedEstablishRouteLimit)
    }

    @Test
    fun prepareConnectionRoutes_safeLimitDisabled_skipsTruncation() {
        val routeCount = IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL + 500
        val routes = disjointHostRoutes(routeCount)

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
            safeRouteLimitEnabled = false,
        )

        assertEquals(routeCount, plan.androidExcludedRoutes.size)
        assertFalse(plan.reachedEstablishRouteLimit)
    }

    @Test
    fun prepareConnectionRoutes_priorityRouteSurvivesEvenWhenGeneralListIsTruncated() {
        val priorityRoute = Ipv4CidrRoute("203.0.112.0", "255.255.252.0", 22)
        val broadNoise = disjointBroadRoutes(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL)

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = broadNoise,
            priorityRoutes = listOf(priorityRoute),
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
        )

        assertTrue(plan.androidExcludedRoutes.contains(priorityRoute))
        assertTrue(plan.reachedEstablishRouteLimit)
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
        assertEquals(false, plan.reachedEstablishRouteLimit)
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

    @Test
    fun prepareConnectionRoutes_onAndroid12_priorityIpv4SurvivesOvpnCap() {
        val priorityRoute = Ipv4CidrRoute("203.0.113.0", "255.255.255.252", 30)
        val broadNoise = disjointBroadRoutes(IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT)

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = broadNoise,
            priorityRoutes = listOf(priorityRoute),
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = false,
        )

        assertEquals(IpListRouteDelivery.OVPN_PROFILE, plan.delivery)
        assertTrue(plan.config.contains("route 203.0.113.0 255.255.255.252 net_gateway"))
        assertEquals(IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT, plan.selectedRouteCount)
    }

    @Test
    fun prepareConnectionRoutes_fastMode_prioritySurvivesFastCap() {
        val priorityRoute = Ipv4CidrRoute("203.0.113.0", "255.255.255.252", 30)
        val broadNoise = disjointBroadRoutes(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FAST)

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = broadNoise,
            priorityRoutes = listOf(priorityRoute),
            coverageMode = IpListCoverageMode.FAST,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
        )

        assertEquals(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FAST, plan.androidExcludedRoutes.size)
        assertTrue(plan.androidExcludedRoutes.contains(priorityRoute))
        assertTrue(plan.reachedEstablishRouteLimit)
    }

    @Test
    fun selectAndroid13FullExcludedRoutes_priorityLargerThanCap_keepsPriorityPrefixOnly() {
        val priority = (0 until IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL + 10).map {
            Ipv4CidrRoute("203.0.${it / 256}.${it % 256}", "255.255.255.255", 32)
        }
        val general = listOf(Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8))

        val selected = IpListRouteConfig.selectAndroid13FullExcludedRoutes(
            routes = general,
            priorityRoutes = priority,
        )

        assertEquals(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL, selected.size)
        assertFalse(selected.contains(general.first()))
        assertEquals(priority.take(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL), selected)
    }

    @Test
    fun prepareConnectionRoutes_ipv6PrioritySurvivesTruncationOnAndroid13() {
        val priorityRoute = Ipv6CidrRoute("2001:db8:abcd:0:0:0:0:0", 48)
        val broadNoise = disjointBroadRoutes(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL)

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = broadNoise,
            priorityRoutes = listOf(priorityRoute),
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
        )

        assertTrue(
            plan.androidExcludedRoutes.any {
                it is Ipv6CidrRoute && it.prefixLength == 48 &&
                    it.networkAddress.startsWith("2001:db8:abcd")
            },
        )
        assertEquals(IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES_FULL, plan.androidExcludedRoutes.size)
    }

    @Test
    fun prepareConnectionRoutes_normalizesNestedAndSiblingPrefixesBeforeCap() {
        val nested = listOf(
            Ipv4CidrRoute("10.0.0.0", "255.0.0.0", 8),
            Ipv4CidrRoute("10.1.0.0", "255.255.0.0", 16),
            Ipv4CidrRoute("10.1.2.0", "255.255.255.0", 24),
        )
        val siblings = listOf(
            Ipv4CidrRoute("192.168.0.0", "255.255.255.0", 24),
            Ipv4CidrRoute("192.168.1.0", "255.255.255.0", 24),
        )

        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = nested + siblings,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = true,
        )

        assertEquals(2, plan.normalizedCandidateCount)
        assertEquals(
            setOf("10.0.0.0/8", "192.168.0.0/23"),
            plan.androidExcludedRoutes.map { it.toCidrString() }.toSet(),
        )
        assertFalse(plan.reachedEstablishRouteLimit)
    }
}

