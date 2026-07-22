package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IpCidrNormalizerTest {

    @Test
    fun removesExactDuplicates() {
        val input = listOf(
            route4("1.2.3.0/24"),
            route4("1.2.3.0/24"),
            route4("1.2.3.0/24"),
        )
        val result = IpCidrNormalizer.normalize(input)
        assertEquals(1, result.routes.size)
        assertEquals("1.2.3.0/24", result.routes.single().toCidrString())
        assertEquals(3, result.ipv4.original)
        assertEquals(1, result.ipv4.distinct)
    }

    @Test
    fun removesNestedPrefixCoveredByBroader() {
        val input = listOf(
            route4("10.0.0.0/8"),
            route4("10.1.0.0/16"),
            route4("10.1.2.3/32"),
        )
        val result = IpCidrNormalizer.normalize(input)
        assertEquals(listOf("10.0.0.0/8"), result.routes.map { it.toCidrString() })
        assertEquals(1, result.ipv4.afterNestedRemoval)
        assertEquals(1, result.ipv4.afterSiblingMerge)
    }

    @Test
    fun mergesAdjacentSiblingsRecursively() {
        val input = listOf(
            route4("1.2.0.0/24"),
            route4("1.2.1.0/24"),
        )
        val result = IpCidrNormalizer.normalize(input)
        assertEquals(listOf("1.2.0.0/23"), result.routes.map { it.toCidrString() })
        assertEquals(2, result.ipv4.afterNestedRemoval)
        assertEquals(1, result.ipv4.afterSiblingMerge)
    }

    @Test
    fun mergesFourSlash24IntoSlash22() {
        val input = listOf(
            route4("1.2.0.0/24"),
            route4("1.2.1.0/24"),
            route4("1.2.2.0/24"),
            route4("1.2.3.0/24"),
        )
        val result = IpCidrNormalizer.normalize(input)
        assertEquals(listOf("1.2.0.0/22"), result.routes.map { it.toCidrString() })
    }

    @Test
    fun ipv6NestedRemoval() {
        val input = listOf(
            route6("2001:db8::/32"),
            route6("2001:db8:1::/48"),
        )
        val result = IpCidrNormalizer.normalize(input)
        assertEquals(1, result.routes.size)
        assertTrue(result.routes.single().toCidrString().startsWith("2001:db8:"))
        assertEquals(32, result.routes.single().prefixLength)
    }

    @Test
    fun keepsIpv4AndIpv6Separate() {
        val input = listOf(
            route4("10.0.0.0/8"),
            route6("2001:db8::/32"),
        )
        val result = IpCidrNormalizer.normalize(input)
        assertEquals(2, result.routes.size)
        assertEquals(1, result.ipv4.afterSiblingMerge)
        assertEquals(1, result.ipv6.afterSiblingMerge)
    }

    @Test
    fun realFullIpv4List_upstreamAlreadyCollapsed() {
        val file = File("/tmp/cidr-norm-audit/ru_ipv4.txt")
        if (!file.isFile) return // skip if audit download missing
        val parsed = IpListRouteConfig.parseCidrRoutesResult(file.readText()).routes
        val result = IpCidrNormalizer.normalize(parsed)
        // ipverse "aggregated" export is already fully collapsed for RU IPv4.
        assertEquals(result.distinctCount, result.afterSiblingMergeCount)
        assertTrue(
            "FULL IPv4 still far above Binder-safe budget after normalize: ${result.afterSiblingMergeCount}",
            result.afterSiblingMergeCount > 1_750
        )
    }

    private fun route4(cidr: String): Ipv4CidrRoute =
        IpListRouteConfig.parseCidrRoutesResult(cidr).routes.filterIsInstance<Ipv4CidrRoute>().single()

    private fun route6(cidr: String): Ipv6CidrRoute =
        IpListRouteConfig.parseCidrRoutesResult(cidr).routes.filterIsInstance<Ipv6CidrRoute>().single()
}
