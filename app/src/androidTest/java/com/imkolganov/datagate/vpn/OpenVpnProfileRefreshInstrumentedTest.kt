package com.imkolganov.datagate.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenVpnProfileRefreshInstrumentedTest {

    @Test
    fun android12Establish_rewritesBakedRoutesOnDevice() {
        val stale = IpListRouteConfig.appendBypassRoutes(
            "client\ndev tun\nremote example.com 443\n",
            listOf(Ipv4CidrRoute("9.9.9.0", "255.255.255.0", 24)),
        )
        val live = ExcludeRouteLiveInput(
            generalRoutes = listOf(Ipv4CidrRoute("8.8.8.0", "255.255.255.0", 24)),
            priorityRoutes = emptyList(),
            enabled = true,
            coverageMode = IpListCoverageMode.FAST,
            android12OvpnRouteLimit = IpListRouteConfig.DEFAULT_ANDROID12_OVPN_ROUTE_LIMIT,
            safeRouteLimitEnabled = true,
        )
        val plan = ExcludeRouteSessionPolicy.resolveOpenVpnEstablish(
            storedConfig = stale,
            intentRoutes = emptyList(),
            live = live,
            supportsAndroidRouteExclusion = false,
        )
        assertTrue(plan.configText.contains("8.8.8.0"))
        assertTrue(!plan.configText.contains("9.9.9.0"))
    }
}
