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
    }
}
