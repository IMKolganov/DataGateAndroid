package com.imkolganov.datagate.vpn.xray

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    @Test
    fun extractShareLink_picksFirstVlessLine() {
        val text = """
            # comment
            vless://uuid@host:443?encryption=none#Node
            vmess://ignored
        """.trimIndent()
        assertEquals(
            "vless://uuid@host:443?encryption=none#Node",
            XrayConfigBuilder.extractShareLink(text),
        )
    }

    @Test
    fun extractShareLink_ignoresBlankAndComments() {
        assertEquals(null, XrayConfigBuilder.extractShareLink("# only comment\n\n"))
        assertEquals(
            "ss://abc@host:8388#node",
            XrayConfigBuilder.extractShareLink("ss://abc@host:8388#node"),
        )
    }

    @Test
    fun extractOutbounds_fromFullConfig() {
        val raw = """{"outbounds":[{"tag":"proxy","protocol":"vless"}]}"""
        val arr = XrayConfigBuilder.extractOutbounds(raw)
        assertEquals(1, arr.length())
        assertEquals("proxy", arr.getJSONObject(0).getString("tag"))
    }

    @Test
    fun extractOutbounds_fromArray() {
        val arr = XrayConfigBuilder.extractOutbounds("""[{"tag":"a","protocol":"freedom"}]""")
        assertEquals(1, arr.length())
        assertEquals("a", arr.getJSONObject(0).getString("tag"))
    }

    @Test
    fun buildTunClientConfig_injectsFdAndRouting() {
        val outbounds = """[{"protocol":"freedom","settings":{}}]"""
        val json = XrayConfigBuilder.buildTunClientConfig(outbounds, tunFd = 42)
        val obj = JSONObject(json)
        assertEquals("42", obj.getJSONObject("env").getString("xray.tun.fd"))
        assertTrue(obj.getJSONArray("inbounds").length() >= 1)
        assertEquals("tun", obj.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
        assertNotNull(obj.getJSONArray("outbounds"))
        assertTrue(obj.getJSONArray("outbounds").length() >= 2) // proxy + direct (+ block)

        val rules = obj.getJSONObject("routing").getJSONArray("rules")
        val privateRule = rules.getJSONObject(0)
        assertEquals("direct", privateRule.getString("outboundTag"))
        val ips = privateRule.getJSONArray("ip")
        assertTrue(ips.length() > 1)
        // Must not depend on geoip.dat / geosite.dat on device.
        for (i in 0 until ips.length()) {
            assertTrue(
                "unexpected geo tag: ${ips.getString(i)}",
                !ips.getString(i).startsWith("geoip:") && !ips.getString(i).startsWith("geosite:"),
            )
        }
        assertTrue(ips.toString().contains("10.0.0.0/8"))
        assertTrue(ips.toString().contains("192.168.0.0/16"))
    }

    @Test
    fun buildTunClientConfig_stripsLibXraySendThroughDisplayName() {
        // Regression: convertShareLinks puts fragment in sendThrough;
        // runXrayFromJson then fails with "unable to send through: DataGate+🇳🇴+Norway+xray".
        val outbounds = """
            [{
              "tag":"proxy",
              "protocol":"vless",
              "sendThrough":"DataGate+🇳🇴+Norway+xray",
              "settings":{
                "vnext":[{"address":"xs2.datagateapp.com","port":8443,"users":[{"id":"95c7c85a-9d7a-4016-968c-4e8153624ce6","encryption":"none"}]}]
              },
              "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"xs2.datagateapp.com"}}
            }]
        """.trimIndent()
        val json = XrayConfigBuilder.buildTunClientConfig(outbounds, tunFd = 7)
        val built = JSONObject(json)
        val proxy = built.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("proxy", proxy.getString("tag"))
        assertTrue(!proxy.has("sendThrough"))
        assertTrue(!json.contains("sendThrough"))
        assertTrue(!json.contains("geoip:"))
    }

    @Test
    fun sanitizeOutboundsForRuntime_removesSendThroughFromAll() {
        val arr = org.json.JSONArray(
            """[
              {"tag":"a","sendThrough":"Node A","protocol":"freedom","settings":{}},
              {"tag":"b","sendThrough":"10.0.0.1","protocol":"freedom","settings":{}}
            ]"""
        )
        XrayConfigBuilder.sanitizeOutboundsForRuntime(arr)
        assertTrue(!arr.getJSONObject(0).has("sendThrough"))
        assertTrue(!arr.getJSONObject(1).has("sendThrough"))
    }

    @Test
    fun buildTunClientConfig_matchesLinuxShape_noGeoipNoSendThrough() {
        // Mirrors DataGateLinux wrapClientConfig outbound (datagate-xray-*.json).
        val outbounds = """
            [{
              "tag":"proxy",
              "protocol":"vless",
              "settings":{
                "vnext":[{"address":"xs2.datagateapp.com","port":8443,"users":[{"id":"95c7c85a-9d7a-4016-968c-4e8153624ce6","encryption":"none"}]}]
              },
              "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{"serverName":"xs2.datagateapp.com"}}
            }]
        """.trimIndent()
        val obj = JSONObject(XrayConfigBuilder.buildTunClientConfig(outbounds, tunFd = 3))
        assertEquals("tun", obj.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
        assertEquals("proxy", obj.getJSONArray("outbounds").getJSONObject(0).getString("tag"))
        val routing = obj.getJSONObject("routing").toString()
        assertTrue(!routing.contains("geoip:"))
        assertTrue(!routing.contains("geosite:"))
        assertTrue(!obj.toString().contains("sendThrough"))
    }

    @Test
    fun buildTunClientConfig_fromFullConfigWithOutboundConfigsAndSendThrough() {
        val raw = """
            {
              "OutboundConfigs":[{
                "protocol":"vless",
                "sendThrough":"DataGate+🇳🇴+Norway+xray",
                "settings":{"vnext":[{"address":"h.example","port":443,"users":[{"id":"u","encryption":"none"}]}]}
              }]
            }
        """.trimIndent()
        val obj = JSONObject(XrayConfigBuilder.buildTunClientConfig(raw, tunFd = 9))
        val proxy = obj.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("proxy", proxy.getString("tag"))
        assertTrue(!proxy.has("sendThrough"))
        assertEquals("9", obj.getJSONObject("env").getString("xray.tun.fd"))
    }

    @Test
    fun buildTunClientConfig_preservesCustomProxyTagInRouting() {
        val outbounds = """[{"tag":"node-a","protocol":"freedom","settings":{}}]"""
        val obj = JSONObject(XrayConfigBuilder.buildTunClientConfig(outbounds, tunFd = 1))
        val rules = obj.getJSONObject("routing").getJSONArray("rules")
        assertEquals("node-a", rules.getJSONObject(1).getString("outboundTag"))
    }

    @Test
    fun buildTunClientConfig_injectsDirectBypassCidrsBeforeProxyRule() {
        val outbounds = """[{"tag":"proxy","protocol":"freedom","settings":{}}]"""
        val obj = JSONObject(
            XrayConfigBuilder.buildTunClientConfig(
                outboundsJson = outbounds,
                tunFd = 1,
                directBypassCidrs = listOf("185.62.200.0/23", "185.73.192.0/22"),
            ),
        )
        val rules = obj.getJSONObject("routing").getJSONArray("rules")
        assertEquals(3, rules.length())
        assertEquals("direct", rules.getJSONObject(0).getString("outboundTag")) // private
        val bypass = rules.getJSONObject(1)
        assertEquals("direct", bypass.getString("outboundTag"))
        val ips = bypass.getJSONArray("ip")
        assertEquals(2, ips.length())
        assertEquals("185.62.200.0/23", ips.getString(0))
        assertEquals("185.73.192.0/22", ips.getString(1))
        assertEquals("proxy", rules.getJSONObject(2).getString("outboundTag"))
    }

    @Test
    fun buildTunClientConfig_chunksLargeDirectBypassLists() {
        val cidrs = (0 until 501).map { "10.${it / 256}.${it % 256}.0/24" }
        val obj = JSONObject(
            XrayConfigBuilder.buildTunClientConfig(
                outboundsJson = """[{"tag":"proxy","protocol":"freedom","settings":{}}]""",
                tunFd = 1,
                directBypassCidrs = cidrs,
            ),
        )
        val rules = obj.getJSONObject("routing").getJSONArray("rules")
        // private + 3 chunks (250+250+1) + proxy
        assertEquals(5, rules.length())
        assertEquals(250, rules.getJSONObject(1).getJSONArray("ip").length())
        assertEquals(250, rules.getJSONObject(2).getJSONArray("ip").length())
        assertEquals(1, rules.getJSONObject(3).getJSONArray("ip").length())
        assertEquals("proxy", rules.getJSONObject(4).getString("outboundTag"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildTunClientConfig_emptyOutbounds_throws() {
        XrayConfigBuilder.buildTunClientConfig("""[]""", tunFd = 1)
    }

    @Test
    fun extractOutbounds_OutboundConfigsAlias() {
        val arr = XrayConfigBuilder.extractOutbounds(
            """{"OutboundConfigs":[{"tag":"x","protocol":"freedom"}]}""",
        )
        assertEquals(1, arr.length())
        assertEquals("x", arr.getJSONObject(0).getString("tag"))
    }
}
