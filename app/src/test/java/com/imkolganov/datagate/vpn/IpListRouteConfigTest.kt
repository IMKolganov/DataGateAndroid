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
}
