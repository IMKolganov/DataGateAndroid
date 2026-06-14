package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnRuntimePolicyTest {
    @Test
    fun restoreCachedStatus_connectedIsDowngradedToDisconnectedSnapshot() {
        val restored = OpenVpnRuntimePolicy.restoreCachedStatus(
            cachedName = "CONNECTED",
            cachedInfo = "Session active"
        )

        assertEquals("DISCONNECTED", restored.eventName)
        assertEquals("Session restored after process restart", restored.eventInfo)
        assertTrue(restored.shouldPersist)
    }

    @Test
    fun restoreCachedStatus_errorIsKeptAsIs() {
        val restored = OpenVpnRuntimePolicy.restoreCachedStatus(
            cachedName = "ERROR",
            cachedInfo = "auth failed"
        )

        assertEquals("ERROR", restored.eventName)
        assertEquals("auth failed", restored.eventInfo)
        assertFalse(restored.shouldPersist)
    }

    @Test
    fun canAttemptReconnect_respectsBackoffWindow() {
        assertFalse(
            OpenVpnRuntimePolicy.canAttemptReconnect(
                nowMs = 3_000L,
                lastAttemptAtMs = 1_000L,
                backoffMs = 4_000L,
                enforceBackoff = true
            )
        )
        assertTrue(
            OpenVpnRuntimePolicy.canAttemptReconnect(
                nowMs = 5_001L,
                lastAttemptAtMs = 1_000L,
                backoffMs = 4_000L,
                enforceBackoff = true
            )
        )
    }

    @Test
    fun canAttemptReconnect_ignoresBackoffWhenDisabled() {
        assertTrue(
            OpenVpnRuntimePolicy.canAttemptReconnect(
                nowMs = 1_500L,
                lastAttemptAtMs = 1_000L,
                backoffMs = 10_000L,
                enforceBackoff = false
            )
        )
    }

    @Test
    fun shouldIgnoreIdleQueryDisconnected_trueForInFlightConnect() {
        assertTrue(
            OpenVpnRuntimePolicy.shouldIgnoreIdleQueryDisconnected(
                fromQuery = true,
                eventName = "DISCONNECTED",
                isConnectRequested = true,
                isVpnConnected = false
            )
        )
    }

    @Test
    fun shouldIgnoreIdleQueryDisconnected_falseWhenConnectedOrNotFromQuery() {
        assertFalse(
            OpenVpnRuntimePolicy.shouldIgnoreIdleQueryDisconnected(
                fromQuery = true,
                eventName = "DISCONNECTED",
                isConnectRequested = true,
                isVpnConnected = true
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldIgnoreIdleQueryDisconnected(
                fromQuery = false,
                eventName = "DISCONNECTED",
                isConnectRequested = true,
                isVpnConnected = false
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldIgnoreIdleQueryDisconnected(
                fromQuery = true,
                eventName = "CONNECTED",
                isConnectRequested = true,
                isVpnConnected = false
            )
        )
    }
}
