package com.imkolganov.datagate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkIdentityReaderTest {

    @Test
    fun formatHostAddress_trimsAndDropsBlank() {
        assertEquals("10.0.0.1", NetworkIdentityReader.formatHostAddress(" 10.0.0.1 "))
        assertNull(NetworkIdentityReader.formatHostAddress("   "))
        assertNull(NetworkIdentityReader.formatHostAddress(null))
    }

    @Test
    fun isLikelyIpv4Host_acceptsValidIpv4Only() {
        assertTrue(isLikelyIpv4Host("10.8.0.5"))
        assertFalse(isLikelyIpv4Host(""))
        assertFalse(isLikelyIpv4Host("not-an-ip"))
        assertFalse(isLikelyIpv4Host("2001:db8::1"))
        assertFalse(isLikelyIpv4Host("999.1.1.1"))
    }

    @Test
    fun pickFirstIpv4HostAddress_skipsInvalidEntries() {
        val picked = pickFirstIpv4HostAddress(
            listOf("", "not-ip", " 10.51.15.4 ", "2001:db8::1", "8.8.8.8")
        )
        assertEquals("10.51.15.4", picked)
    }
}
