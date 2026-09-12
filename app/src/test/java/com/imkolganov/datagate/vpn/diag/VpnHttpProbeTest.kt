package com.imkolganov.datagate.vpn.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class VpnHttpProbeTest {

    @Test
    fun defaultUrl_isHttpsSoCleartextPolicyCannotFailTheProbe() {
        assertTrue(VpnHttpProbe.DEFAULT_URL.startsWith("https://"))
        assertTrue(VpnHttpProbe.DEFAULT_URL.contains(VpnHttpProbe.DEFAULT_HOST))
        assertTrue(VpnHttpProbe.DEFAULT_URL.endsWith("/generate_204"))
    }

    @Test
    fun successStatuses() {
        assertTrue(VpnHttpProbe.isSuccessStatus(204))
        assertTrue(VpnHttpProbe.isSuccessStatus(200))
        assertFalse(VpnHttpProbe.isSuccessStatus(302))
        assertFalse(VpnHttpProbe.isSuccessStatus(500))
    }

    @Test
    fun run_readsResponseCode() {
        val result = VpnHttpProbe.run(open = { FakeHttp(204) })
        assertTrue(result.ok)
        assertEquals(204, result.code)
        assertEquals(null, result.error)
    }

    @Test
    fun run_mapsHttpFailure() {
        val result = VpnHttpProbe.run(open = { FakeHttp(503) })
        assertFalse(result.ok)
        assertEquals("http_503", result.error)
    }

    @Test
    fun run_mapsOpenError() {
        val result = VpnHttpProbe.run(open = { error("no network") })
        assertFalse(result.ok)
        assertEquals("IllegalStateException", result.error)
    }

    private class FakeHttp(private val code: Int) : HttpURLConnection(URL("http://example.invalid")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
    }
}
