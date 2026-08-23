package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Connect handoff plans used by [com.imkolganov.datagate.vpn.VpnConnectInteractor]
 * and intent extras resolution used by [XrayVpnService].
 */
class XrayVpnDnsConnectPlanTest {

    @Test
    fun planFromLocalProfile_prefersIndexDnsAndIdentity() {
        val raw =
            """{"vless":"vless://u@h:443","dnsServers":["8.8.8.8"],"dnsIdentityEnabled":false}"""
        val plan = XrayVpnDns.planFromLocalProfile(
            profileDnsServers = listOf("172.20.0.1"),
            profileDnsIdentityEnabled = true,
            rawConfig = raw,
        )
        assertEquals(listOf("172.20.0.1"), plan.dnsServers)
        assertTrue(plan.dnsIdentityEnabled)
    }

    @Test
    fun planFromLocalProfile_emptyIndex_extractsFromRawJson() {
        val raw =
            """{"vless":"vless://u@h:443","dnsServers":["172.20.0.1"],"dnsIdentityEnabled":true}"""
        val plan = XrayVpnDns.planFromLocalProfile(
            profileDnsServers = emptyList(),
            profileDnsIdentityEnabled = false,
            rawConfig = raw,
        )
        assertEquals(listOf("172.20.0.1"), plan.dnsServers)
        assertTrue(plan.dnsIdentityEnabled)
    }

    @Test
    fun planFromLocalProfile_emptyIndexAndPlainLink_fallsBackPublic_noIdentity() {
        val plan = XrayVpnDns.planFromLocalProfile(
            profileDnsServers = emptyList(),
            profileDnsIdentityEnabled = false,
            rawConfig = "vless://uuid@host:443?encryption=none#plain",
        )
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), plan.dnsServers)
        assertFalse(plan.dnsIdentityEnabled)
    }

    @Test
    fun planFromLocalProfile_indexIdentityFalse_butRawTrue_enablesHint() {
        val raw =
            """{"vless":"vless://u@h:1","dnsServers":["9.9.9.9"],"dnsIdentityEnabled":true}"""
        val plan = XrayVpnDns.planFromLocalProfile(
            profileDnsServers = listOf("9.9.9.9"),
            profileDnsIdentityEnabled = false,
            rawConfig = raw,
        )
        assertEquals(listOf("9.9.9.9"), plan.dnsServers)
        assertTrue(plan.dnsIdentityEnabled)
    }

    @Test
    fun planFromIssuedLink_usesLinkBodyDnsOnly() {
        val issued =
            """{"vless":"vless://u@h:443","dnsServers":["172.20.0.1"],"dnsIdentityEnabled":true}"""
        val plan = XrayVpnDns.planFromIssuedLink(issued)
        assertEquals(listOf("172.20.0.1"), plan.dnsServers)
        assertTrue(plan.dnsIdentityEnabled)
    }

    @Test
    fun planFromIssuedLink_ignoresCatalogBestServerDns_usesIssuedBodyOnly() {
        // Catalog BestServerResult.dnsServers must not be mixed into issued-link plan.
        val catalogDns = listOf("10.0.0.53")
        val issued =
            """{"vless":"vless://u@h:443","dnsServers":["172.20.0.1"],"dnsIdentityEnabled":true}"""
        val plan = XrayVpnDns.planFromIssuedLink(issued)
        assertEquals(listOf("172.20.0.1"), plan.dnsServers)
        assertFalse(plan.dnsServers.containsAll(catalogDns))
        assertTrue(plan.dnsIdentityEnabled)
    }

    @Test
    fun planFromIssuedLink_legacyPlainText_publicFallback() {
        val legacy = """
            vless://uuid@host:443?encryption=none#Node
            # Norway
        """.trimIndent()
        val plan = XrayVpnDns.planFromIssuedLink(legacy)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), plan.dnsServers)
        assertFalse(plan.dnsIdentityEnabled)
    }

    @Test
    fun resolveFromIntentExtras_explicitServers() {
        assertEquals(
            listOf("172.20.0.1"),
            XrayVpnDns.resolveFromIntentExtras(listOf("172.20.0.1")),
        )
    }

    @Test
    fun resolveFromIntentExtras_nullOrEmpty_usesPublicFallback() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolveFromIntentExtras(null),
        )
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolveFromIntentExtras(emptyList()),
        )
    }

    @Test
    fun resolveFromIntentExtras_rejectsNonIpv4_fallsBackOrKeepsValid() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolveFromIntentExtras(listOf("dns.google", "2001:db8::1")),
        )
        assertEquals(
            listOf("172.20.0.1"),
            XrayVpnDns.resolveFromIntentExtras(listOf("bad", "172.20.0.1", "01.2.3.4")),
        )
    }
}
