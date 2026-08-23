package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures mirror DataGateMonitor:
 * - frontend `XRAY_EXPORT_TEMPLATE`
 * - xray `ClientLinkService` placeholder expansion (`{{dns_servers_json}}`, `{{dns_identity_enabled}}`)
 */
class XrayVpnDnsTest {

    /** Same shape as Monitor dashboard Save template after node issue. */
    private fun monitorIssuedProfile(
        dnsServersJson: String = """["172.20.0.1"]""",
        dnsIdentityEnabled: String = "true",
        vless: String = "vless://11111111-1111-1111-1111-111111111111@node.example.com:443?encryption=none&security=tls&type=tcp#DataGate",
        friendlyName: String = "Norway [xs2]",
    ): String = """
        {
          "vless":"$vless",
          "dnsServers":$dnsServersJson,
          "dnsIdentityEnabled":$dnsIdentityEnabled,
          "friendlyName":"$friendlyName",
          "uuid":"11111111-1111-1111-1111-111111111111",
          "endpoint":"node.example.com:443"
        }
    """.trimIndent()

    @Test
    fun resolve_prefersExplicitServers() {
        assertEquals(
            listOf("10.0.0.53"),
            XrayVpnDns.resolve(explicitDnsServers = listOf("10.0.0.53")),
        )
    }

    @Test
    fun resolve_empty_usesPublicClassicDns() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolve(explicitDnsServers = emptyList()),
        )
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolve(explicitDnsServers = null),
        )
    }

    @Test
    fun resolve_trimsDedupesAndSkipsBlank() {
        assertEquals(
            listOf("172.20.0.1", "8.8.8.8"),
            XrayVpnDns.resolve(listOf(" 172.20.0.1 ", "", "172.20.0.1", "8.8.8.8")),
        )
    }

    @Test
    fun resolve_rejectsNonIpv4Literals() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolve(listOf("dns.google", "2001:db8::1", "999.1.1.1", "01.2.3.4")),
        )
        assertEquals(
            listOf("172.20.0.1"),
            XrayVpnDns.resolve(listOf("dns.example", "172.20.0.1")),
        )
    }

    @Test
    fun isIpv4Literal_acceptsCanonicalDottedQuad() {
        assertTrue(XrayVpnDns.isIpv4Literal("0.0.0.0"))
        assertTrue(XrayVpnDns.isIpv4Literal("172.20.0.1"))
        assertFalse(XrayVpnDns.isIpv4Literal("172.20.0"))
        assertFalse(XrayVpnDns.isIpv4Literal("host"))
        assertFalse(XrayVpnDns.isIpv4Literal("8.8.8.08"))
    }

    @Test
    fun extract_fromMonitorIssuedProfile_matchesNodeDnsPlaceholderExpansion() {
        // Mirrors ClientLinkServiceDnsPlaceholderTests: DNS1+DNS2 → json array + identity true.
        val raw = monitorIssuedProfile(
            dnsServersJson = """["172.20.0.1","8.8.8.8"]""",
            dnsIdentityEnabled = "true",
        )
        assertEquals(
            listOf("172.20.0.1", "8.8.8.8"),
            XrayVpnDns.extractExplicitDnsServers(raw),
        )
        assertEquals(true, XrayVpnDns.extractDnsIdentityEnabled(raw))
        assertEquals(
            listOf("172.20.0.1", "8.8.8.8"),
            XrayVpnDns.resolve(XrayVpnDns.extractExplicitDnsServers(raw)),
        )
    }

    @Test
    fun extract_identityOnlyDns1_singleServer() {
        val raw = monitorIssuedProfile(dnsServersJson = """["172.20.0.1"]""")
        assertEquals(listOf("172.20.0.1"), XrayVpnDns.extractExplicitDnsServers(raw))
        assertTrue(XrayVpnDns.extractDnsIdentityEnabled(raw) == true)
    }

    @Test
    fun extract_dnsIdentityEnabledFalse() {
        val raw = monitorIssuedProfile(
            dnsServersJson = """["1.1.1.1"]""",
            dnsIdentityEnabled = "false",
        )
        assertEquals(listOf("1.1.1.1"), XrayVpnDns.extractExplicitDnsServers(raw))
        assertEquals(false, XrayVpnDns.extractDnsIdentityEnabled(raw))
    }

    @Test
    fun extract_emptyDnsServersArray_fallsBackOnResolve() {
        val raw = monitorIssuedProfile(dnsServersJson = "[]", dnsIdentityEnabled = "false")
        assertEquals(emptyList<String>(), XrayVpnDns.extractExplicitDnsServers(raw))
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolve(XrayVpnDns.extractExplicitDnsServers(raw)),
        )
    }

    @Test
    fun extract_pascalCaseDnsServers_accepted() {
        val raw = """{"DnsServers":["9.9.9.9"],"DnsIdentityEnabled":true}"""
        assertEquals(listOf("9.9.9.9"), XrayVpnDns.extractExplicitDnsServers(raw))
        assertEquals(true, XrayVpnDns.extractDnsIdentityEnabled(raw))
    }

    @Test
    fun extract_plainShareLink_andLegacyTextTemplate_noDns() {
        // Seed DefaultXrayClientLinkTemplate still used by some servers.
        val legacy = """
            vless://uuid@host:443?encryption=none#Node
            # Norway
            UUID: uuid
            Endpoint: host:443
        """.trimIndent()
        assertEquals(emptyList<String>(), XrayVpnDns.extractExplicitDnsServers(legacy))
        assertNull(XrayVpnDns.extractDnsIdentityEnabled(legacy))
        assertEquals(
            emptyList<String>(),
            XrayVpnDns.extractExplicitDnsServers("vless://u@h:443?encryption=none#n"),
        )
        assertNull(XrayVpnDns.extractDnsIdentityEnabled("vless://u@h:443"))
    }

    @Test
    fun extract_doesNotInferDnsFromFriendlyNameOrEndpoint() {
        // No dnsServers field — even if friendlyName/endpoint look like identity nodes.
        val raw = """
            {
              "vless":"vless://u@xs2.datagateapp.com:443?encryption=none#n",
              "friendlyName":"DataGate xs2 Norway",
              "endpoint":"xs2.datagateapp.com:443"
            }
        """.trimIndent()
        assertEquals(emptyList<String>(), XrayVpnDns.extractExplicitDnsServers(raw))
        assertNull(XrayVpnDns.extractDnsIdentityEnabled(raw))
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolve(XrayVpnDns.extractExplicitDnsServers(raw)),
        )
    }

    @Test
    fun extract_dnsIdentityAbsent_returnsNull() {
        val raw = """{"vless":"vless://u@h:1","dnsServers":["172.20.0.1"]}"""
        assertEquals(listOf("172.20.0.1"), XrayVpnDns.extractExplicitDnsServers(raw))
        assertNull(XrayVpnDns.extractDnsIdentityEnabled(raw))
    }

    @Test
    fun extract_invalidJson_returnsEmpty() {
        assertEquals(emptyList<String>(), XrayVpnDns.extractExplicitDnsServers("{not-json"))
        assertNull(XrayVpnDns.extractDnsIdentityEnabled("{not-json"))
    }

    @Test
    fun extract_diagnosticDns1Dns2Fields_areIgnoredForVpnDns() {
        // Node may embed dns1/dns2 for diagnostics; Android uses dnsServers only.
        val raw = """
            {
              "vless":"vless://u@h:443?encryption=none#n",
              "dnsServers":["172.20.0.1"],
              "dnsIdentityEnabled":true,
              "dns1":"172.20.0.1",
              "dns2":"8.8.8.8"
            }
        """.trimIndent()
        assertEquals(listOf("172.20.0.1"), XrayVpnDns.extractExplicitDnsServers(raw))
        assertFalse(XrayVpnDns.extractExplicitDnsServers(raw).contains("8.8.8.8"))
    }
}
