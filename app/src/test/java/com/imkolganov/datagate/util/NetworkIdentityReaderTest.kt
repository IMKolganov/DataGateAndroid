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

    @Test
    fun isConnectivitySystemFailure_treatsSecurityAndIllegalStateAsTransient() {
        assertTrue(isConnectivitySystemFailure(SecurityException("denied")))
        assertTrue(isConnectivitySystemFailure(IllegalStateException("busy")))
    }

    @Test
    fun isConnectivitySystemFailure_walksCauseChain() {
        val root = SecurityException("denied")
        val wrapped = RuntimeException("outer", RuntimeException("mid", root))
        assertTrue(isConnectivitySystemFailure(wrapped))
    }

    @Test
    fun isConnectivitySystemFailure_detectsDeadSystemExceptionInCauseChain() {
        val deadSystem = runCatching {
            Class.forName("android.os.DeadSystemException")
                .getDeclaredConstructor()
                .newInstance() as Throwable
        }.getOrNull() ?: return

        assertTrue(isConnectivitySystemFailure(RuntimeException("wrap", deadSystem)))
    }

    @Test
    fun mergeWithSession_prefersSystemValuesAndFallsBackToSession() {
        val merged = NetworkIdentityReader.mergeWithSession(
            fromSystem = NetworkIdentitySnapshot(vpnIpAddress = null, dnsServers = emptyList()),
            fromSession = NetworkIdentitySnapshot(
                vpnIpAddress = "10.51.15.4",
                dnsServers = listOf("8.8.8.8", "1.1.1.1"),
                dnsIdentityEnabled = true,
            )
        )
        assertEquals("10.51.15.4", merged.vpnIpAddress)
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), merged.dnsServers)
        assertTrue(merged.dnsIdentityEnabled)
    }

    @Test
    fun mergeWithSession_keepsSystemWhenPresent() {
        val merged = NetworkIdentityReader.mergeWithSession(
            fromSystem = NetworkIdentitySnapshot(
                vpnIpAddress = "10.8.0.2",
                dnsServers = listOf("9.9.9.9")
            ),
            fromSession = NetworkIdentitySnapshot(
                vpnIpAddress = "10.51.15.4",
                dnsServers = listOf("8.8.8.8"),
                dnsIdentityEnabled = true,
            )
        )
        assertEquals("10.8.0.2", merged.vpnIpAddress)
        assertEquals(listOf("9.9.9.9"), merged.dnsServers)
        assertTrue(merged.dnsIdentityEnabled)
    }

    @Test
    fun mergeWithSession_preservesDnsIdentityFlagFromSessionOnly() {
        val merged = NetworkIdentityReader.mergeWithSession(
            fromSystem = NetworkIdentitySnapshot(
                vpnIpAddress = "10.8.0.2",
                dnsServers = listOf("172.20.0.1"),
            ),
            fromSession = NetworkIdentitySnapshot(dnsIdentityEnabled = true),
        )
        assertTrue(merged.dnsIdentityEnabled)
    }

    @Test
    fun isConnectivitySystemFailure_falseForUnrelatedErrors() {
        assertFalse(isConnectivitySystemFailure(IllegalArgumentException("bad arg")))
    }
}
