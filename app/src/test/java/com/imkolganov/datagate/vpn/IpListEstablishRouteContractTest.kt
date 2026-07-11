package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CIDR establish budget — FULL capped at [IpListRouteConfig.MAX_ANDROID13_EXCLUDE_ROUTE_LIMIT] (4000).
 */
class IpListEstablishRouteContractTest {

    @Test
    fun productionRuFallback_parsesThousandsOfRoutes() {
        val routes = parseProductionFallbackLists()
        assertTrue(
            "Production RU fallback is not a toy list — this is what users get offline.",
            routes.size > 5_000,
        )
    }

    @Test
    fun fullCoverage_onAndroid13_capsAt4000_andFitsEstablishBudget() {
        val routes = parseProductionFallbackLists()
        val plan = planForAndroid13(IpListCoverageMode.FULL, routes)

        assertEquals(IpListRouteDelivery.ANDROID_EXCLUDE_ROUTE, plan.delivery)
        assertEquals(IpListRouteConfig.MAX_ANDROID13_EXCLUDE_ROUTE_LIMIT, plan.androidExcludedRoutes.size)
        assertTrue(plan.reachedEstablishRouteLimit)
        assertNull(IpListEstablishRoutePolicy.establishBudgetViolation(plan, IpListCoverageMode.FULL))
        assertTrue(
            IpListEstablishRoutePolicy.isWithinEstablishBudget(
                IpListEstablishRoutePolicy.excludeRouteCallsForPlan(plan),
            ),
        )
    }

    @Test
    fun contract_productionFallback_fullMode_mustFitEstablishExcludeBudget() {
        val routes = parseProductionFallbackLists()
        val plan = planForAndroid13(IpListCoverageMode.FULL, routes)
        val calls = IpListEstablishRoutePolicy.excludeRouteCallsForPlan(plan)

        assertTrue(
            calls <= IpListEstablishRoutePolicy.SAFE_ESTABLISH_EXCLUDE_ROUTE_BUDGET,
        )
    }

    @Test
    fun fastCoverage_staysWithin3000_fullCapsAt4000() {
        val routes = parseProductionFallbackLists()
        val fast = planForAndroid13(IpListCoverageMode.FAST, routes)
        val full = planForAndroid13(IpListCoverageMode.FULL, routes)

        assertTrue(fast.androidExcludedRoutes.size <= IpListRouteConfig.MAX_ANDROID_EXCLUDED_ROUTES)
        assertEquals(IpListRouteConfig.MAX_ANDROID13_EXCLUDE_ROUTE_LIMIT, full.androidExcludedRoutes.size)
        assertTrue(full.androidExcludedRoutes.size > fast.androidExcludedRoutes.size)
    }

    @Test
    fun android12FallbackPath_stillUsesLowerOvpnCap() {
        val routes = parseProductionFallbackLists()
        val android12 = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = false,
        )
        val android13Full = planForAndroid13(IpListCoverageMode.FULL, routes)

        assertEquals(IpListRouteDelivery.OVPN_PROFILE, android12.delivery)
        assertTrue(android12.appliedRouteCount <= IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT)
        assertEquals(IpListRouteConfig.MAX_ANDROID13_EXCLUDE_ROUTE_LIMIT, android13Full.androidExcludedRoutes.size)
    }

    @Test
    fun app1_0_6_fullMode_wasUnbounded_currentCapsAt4000() {
        val routes = parseProductionFallbackLists()
        val app106Excluded = routes
        val current = planForAndroid13(IpListCoverageMode.FULL, routes)

        assertTrue(app106Excluded.size > IpListRouteConfig.MAX_ANDROID13_EXCLUDE_ROUTE_LIMIT)
        assertFalse(app106Excluded.size == current.androidExcludedRoutes.size)
        assertEquals(IpListRouteConfig.MAX_ANDROID13_EXCLUDE_ROUTE_LIMIT, current.androidExcludedRoutes.size)
    }

    @Test
    fun ovpnProfileDelivery_doesNotDoubleExcludeAtEstablish() {
        val routes = parseProductionFallbackLists()
        val plan = IpListRouteConfig.prepareConnectionRoutes(
            config = "client\n",
            routes = routes,
            coverageMode = IpListCoverageMode.FULL,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            supportsAndroidRouteExclusion = false,
        )

        assertEquals(0, IpListEstablishRoutePolicy.excludeRouteCallsForPlan(plan))
        assertTrue(plan.appliedRouteCount > 0)
        assertTrue(plan.config.contains("net_gateway"))
    }

    private fun planForAndroid13(
        coverageMode: IpListCoverageMode,
        routes: List<IpCidrRoute>,
    ): IpListConnectionRoutePlan = IpListRouteConfig.prepareConnectionRoutes(
        config = "client\n",
        routes = routes,
        coverageMode = coverageMode,
        android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
        supportsAndroidRouteExclusion = true,
    )

    private fun parseProductionFallbackLists(): List<IpCidrRoute> {
        val content = listOf(
            readRepoAsset("ip_lists/ru_ipv4_fallback.txt"),
            readRepoAsset("ip_lists/ru_ipv6_fallback.txt"),
        ).joinToString("\n")
        return IpListRouteConfig.parseCidrRoutesResult(content).routes
    }

    private fun readRepoAsset(relativePath: String): String {
        val candidates = listOf(
            File("src/main/assets/$relativePath"),
            File("app/src/main/assets/$relativePath"),
            File("../app/src/main/assets/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing asset $relativePath; tried ${candidates.map { it.absolutePath }}")
        return file.readText()
    }
}
