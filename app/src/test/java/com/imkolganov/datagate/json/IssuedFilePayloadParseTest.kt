package com.imkolganov.datagate.json

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class IssuedFilePayloadParseTest {

    @Test
    fun parse_usesIssuedOvpnFileName() {
        val root = JSONObject(
            """{"success":true,"data":{"issuedOvpn":{"fileName":"a.ovpn"},"content":"YQ=="}}"""
        )
        val payload = root.parseIssuedFilePayloadOrThrow("op", "client.ovpn")
        assertEquals("a.ovpn", payload.fileName)
        assertEquals("YQ==", payload.contentBase64)
    }

    @Test
    fun parse_withoutIssuedOvpn_usesDefaultFileName() {
        val root = JSONObject("""{"success":true,"data":{"content":"YQ=="}}""")
        val payload = root.parseIssuedFilePayloadOrThrow("op", "client.ovpn")
        assertEquals("client.ovpn", payload.fileName)
        assertEquals("YQ==", payload.contentBase64)
    }

    @Test
    fun parse_pascalCaseKeys() {
        val root = JSONObject(
            """{"Success":true,"Data":{"IssuedOvpn":{"FileName":"b.ovpn"},"Content":"Yg=="}}"""
        )
        val payload = root.parseIssuedFilePayloadOrThrow("op", "client.ovpn")
        assertEquals("b.ovpn", payload.fileName)
        assertEquals("Yg==", payload.contentBase64)
    }

    @Test
    fun parse_missingContent_throwsClearMessage() {
        val root = JSONObject("""{"success":true,"data":{"issuedOvpn":{"fileName":"x.ovpn"}}}""")
        val err = assertThrows(IOException::class.java) {
            root.parseIssuedFilePayloadOrThrow("Download failed", "client.ovpn")
        }
        assertTrue(err.message!!.contains("missing file content"))
    }
}
