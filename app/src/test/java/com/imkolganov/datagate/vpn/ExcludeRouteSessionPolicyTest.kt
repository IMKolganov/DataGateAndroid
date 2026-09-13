package com.imkolganov.datagate.vpn

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExcludeRouteSessionPolicyTest {

    private val intentRoutes = listOf(Ipv4CidrRoute("1.0.0.0", "255.0.0.0", 8))
    private val liveGeneral = listOf(Ipv4CidrRoute("8.8.8.0", "255.255.255.0", 24))
    private val livePriority = listOf(Ipv4CidrRoute("1.1.1.1", "255.255.255.255", 32))

    @Before
    @After
    fun reset() {
        ExcludeRouteSession.resetForTests()
    }

    @Test
    fun noLiveSnapshot_keepsIntentRoutes() {
        val resolved = ExcludeRouteSessionPolicy.resolveForEstablish(
            intentRoutes = intentRoutes,
            live = null,
            forXray = true,
            supportsAndroidRouteExclusion = true,
        )
        assertEquals(intentRoutes, resolved)
    }

    @Test
    fun disabledLiveSnapshot_winsOverStaleIntent() {
        val live = liveInput(enabled = false, general = liveGeneral)
        val resolved = ExcludeRouteSessionPolicy.resolveForEstablish(
            intentRoutes = intentRoutes,
            live = live,
            forXray = true,
            supportsAndroidRouteExclusion = true,
        )
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun liveSnapshot_replacesFrozenIntentOnEstablish() {
        val live = liveInput(enabled = true, general = liveGeneral, priority = livePriority)
        val resolved = ExcludeRouteSessionPolicy.resolveForEstablish(
            intentRoutes = intentRoutes,
            live = live,
            forXray = true,
            supportsAndroidRouteExclusion = true,
        )
        val cidrs = resolved.map { it.toCidrString() }.toSet()
        assertTrue(cidrs.contains("8.8.8.0/24"))
        assertTrue(cidrs.contains("1.1.1.1/32"))
        assertTrue("stale intent CIDR must not survive a live list", "1.0.0.0/8" !in cidrs)
    }

    @Test
    fun processCache_openVpnReconnectUsesPublishedList() {
        ExcludeRouteSession.publish(
            routes = IpListConnectionRoutes(liveGeneral, livePriority),
            settings = settings(enabled = true),
        )
        val resolved = ExcludeRouteSession.resolveForEstablish(
            intentRoutes = intentRoutes,
            forXray = false,
            supportsAndroidRouteExclusion = true,
        )
        val cidrs = resolved.map { it.toCidrString() }.toSet()
        assertTrue(cidrs.contains("8.8.8.0/24"))
        assertTrue("1.0.0.0/8" !in cidrs)
    }

    @Test
    fun android12_rewritesOvpnProfileFromLiveList() {
        val stale = IpListRouteConfig.appendBypassRoutes(
            "client\ndev tun\n",
            listOf(Ipv4CidrRoute("9.9.9.0", "255.255.255.0", 24)),
        )
        val live = liveInput(enabled = true, general = liveGeneral)
        val plan = ExcludeRouteSessionPolicy.resolveOpenVpnEstablish(
            storedConfig = stale,
            intentRoutes = intentRoutes,
            live = live,
            supportsAndroidRouteExclusion = false,
        )
        assertTrue(plan.configText.contains("8.8.8.0"))
        assertTrue("stale baked route must be replaced", !plan.configText.contains("9.9.9.0"))
        assertTrue(plan.configText.contains(IpListRouteConfig.BYPASS_ROUTES_MARKER))
        assertTrue(plan.excludedRoutes.isEmpty())
    }

    @Test
    fun android12_disabledLive_stripsBakedRoutes() {
        val stale = IpListRouteConfig.appendBypassRoutes(
            "client\ndev tun\n",
            listOf(Ipv4CidrRoute("9.9.9.0", "255.255.255.0", 24)),
        )
        val plan = ExcludeRouteSessionPolicy.resolveOpenVpnEstablish(
            storedConfig = stale,
            intentRoutes = intentRoutes,
            live = liveInput(enabled = false),
            supportsAndroidRouteExclusion = false,
        )
        assertTrue(!plan.configText.contains("9.9.9.0"))
        assertTrue(!plan.configText.contains(IpListRouteConfig.BYPASS_ROUTES_MARKER))
    }

    @Test
    fun android13_keepsBaseProfileAndUsesExcludeRoutes() {
        val stale = IpListRouteConfig.appendBypassRoutes(
            "client\ndev tun\n",
            listOf(Ipv4CidrRoute("9.9.9.0", "255.255.255.0", 24)),
        )
        val plan = ExcludeRouteSessionPolicy.resolveOpenVpnEstablish(
            storedConfig = stale,
            intentRoutes = intentRoutes,
            live = liveInput(enabled = true, general = liveGeneral),
            supportsAndroidRouteExclusion = true,
        )
        assertTrue(!plan.configText.contains("9.9.9.0"))
        assertTrue(!plan.configText.contains(IpListRouteConfig.BYPASS_ROUTES_MARKER))
        assertTrue(plan.excludedRoutes.map { it.toCidrString() }.contains("8.8.8.0/24"))
    }

    @Test
    fun processCache_disabledPublishClearsIntentRoutes() {
        ExcludeRouteSession.publishDisabled()
        val resolved = ExcludeRouteSession.resolveForEstablish(
            intentRoutes = intentRoutes,
            forXray = true,
            supportsAndroidRouteExclusion = true,
        )
        assertTrue(resolved.isEmpty())
    }

    private fun liveInput(
        enabled: Boolean,
        general: List<IpCidrRoute> = emptyList(),
        priority: List<IpCidrRoute> = emptyList(),
    ) = ExcludeRouteLiveInput(
        generalRoutes = general,
        priorityRoutes = priority,
        enabled = enabled,
        coverageMode = IpListCoverageMode.FAST,
        android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
        safeRouteLimitEnabled = true,
    )

    private fun settings(enabled: Boolean) = IpListSettings(
        sourceUrls = emptyList(),
        updateFrequency = IpListUpdateFrequency.MANUAL,
        coverageMode = IpListCoverageMode.FAST,
        android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
        cidrListsEnabled = enabled,
    )
}
