package com.imkolganov.datagate.vpn.xray

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end connect preparation: Monitor-issued Content → VPN DNS + TUN routing.
 * Mirrors DataGateMonitor frontend XRAY_EXPORT_TEMPLATE + Xray ClientLinkService expansion.
 */
class XrayIssuedProfileDnsPipelineTest {

    @Test
    fun monitorIssuedProfile_toTunConfig_classicDnsThroughProxy() {
        val issued =
            """{"vless":"vless://11111111-1111-1111-1111-111111111111@node.example.com:443?encryption=none&security=tls&type=tcp#DataGate","dnsServers":["172.20.0.1"],"dnsIdentityEnabled":true,"friendlyName":"Norway [1]","uuid":"11111111-1111-1111-1111-111111111111","endpoint":"node.example.com:443"}"""

        val share = XrayConfigBuilder.extractShareLink(issued)
        assertEquals(
            "vless://11111111-1111-1111-1111-111111111111@node.example.com:443?encryption=none&security=tls&type=tcp#DataGate",
            share,
        )

        val tunnelDns = XrayVpnDns.resolve(XrayVpnDns.extractExplicitDnsServers(issued))
        assertEquals(listOf("172.20.0.1"), tunnelDns)
        assertEquals(true, XrayVpnDns.extractDnsIdentityEnabled(issued))

        // Outbounds would normally come from libXray convert; use equivalent shape for routing asserts.
        val outbounds = """
            [{
              "tag":"proxy",
              "protocol":"vless",
              "settings":{
                "vnext":[{"address":"node.example.com","port":443,"users":[{"id":"11111111-1111-1111-1111-111111111111","encryption":"none"}]}]
              }
            }]
        """.trimIndent()

        val tun = JSONObject(
            XrayConfigBuilder.buildTunClientConfig(
                outboundsJson = outbounds,
                tunFd = 42,
                tunnelDnsServers = tunnelDns,
            ),
        )
        assertTrue(!tun.has("dns"))
        val rules = tun.getJSONObject("routing").getJSONArray("rules")
        assertEquals("proxy", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("172.20.0.1/32", rules.getJSONObject(0).getJSONArray("ip").getString(0))
        assertEquals("direct", rules.getJSONObject(1).getString("outboundTag"))
        assertTrue(rules.getJSONObject(1).getJSONArray("ip").toString().contains("172.16.0.0/12"))
    }

    @Test
    fun legacyPlainTextTemplate_fallsBackToPublicDns_noIdentityHint() {
        val legacy = """
            vless://uuid@host:443?encryption=none#Node
            # Norway
            UUID: uuid
            Endpoint: host:443
        """.trimIndent()

        assertEquals(
            "vless://uuid@host:443?encryption=none#Node",
            XrayConfigBuilder.extractShareLink(legacy),
        )
        assertEquals(emptyList<String>(), XrayVpnDns.extractExplicitDnsServers(legacy))
        assertEquals(null, XrayVpnDns.extractDnsIdentityEnabled(legacy))
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8"),
            XrayVpnDns.resolve(XrayVpnDns.extractExplicitDnsServers(legacy)),
        )
    }
}
