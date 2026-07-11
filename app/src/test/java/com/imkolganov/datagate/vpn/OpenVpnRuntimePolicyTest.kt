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
    fun shouldHandleBridgeTransportLost_trueOnlyForActiveSession() {
        assertTrue(
            OpenVpnRuntimePolicy.shouldHandleBridgeTransportLost(
                isStopping = false,
                desiredConnection = true,
                isPaused = false,
                hasActiveSession = true
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldHandleBridgeTransportLost(
                isStopping = true,
                desiredConnection = true,
                isPaused = false,
                hasActiveSession = true
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldHandleBridgeTransportLost(
                isStopping = false,
                desiredConnection = false,
                isPaused = false,
                hasActiveSession = true
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldHandleBridgeTransportLost(
                isStopping = false,
                desiredConnection = true,
                isPaused = true,
                hasActiveSession = true
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldHandleBridgeTransportLost(
                isStopping = false,
                desiredConnection = true,
                isPaused = false,
                hasActiveSession = false
            )
        )
    }

    @Test
    fun shouldReconnectAfterBridgeTransportLost_requiresPendingFlagAndActiveIntent() {
        assertTrue(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = true,
                desiredConnection = true,
                isStopping = false,
                isPaused = false
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = false,
                desiredConnection = true,
                isStopping = false,
                isPaused = false
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = true,
                desiredConnection = false,
                isStopping = false,
                isPaused = false
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = true,
                desiredConnection = true,
                isStopping = true,
                isPaused = false
            )
        )
        assertFalse(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = true,
                desiredConnection = true,
                isStopping = false,
                isPaused = true
            )
        )
    }

    @Test
    fun restoreCachedStatus_connectingIsDowngradedToDisconnectedSnapshot() {
        val restored = OpenVpnRuntimePolicy.restoreCachedStatus(
            cachedName = "CONNECTING",
            cachedInfo = "Negotiating"
        )

        assertEquals("DISCONNECTED", restored.eventName)
        assertTrue(restored.shouldPersist)
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

    @Test
    fun restoreCachedStatus_pausedIsDowngradedToDisconnectedSnapshot() {
        val restored = OpenVpnRuntimePolicy.restoreCachedStatus(
            cachedName = "PAUSED",
            cachedInfo = "Paused by user"
        )

        assertEquals("DISCONNECTED", restored.eventName)
        assertTrue(restored.shouldPersist)
    }

    @Test
    fun restoreCachedStatus_reconnectingIsDowngradedToDisconnectedSnapshot() {
        val restored = OpenVpnRuntimePolicy.restoreCachedStatus(
            cachedName = "RECONNECTING",
            cachedInfo = "Connection lost"
        )

        assertEquals("DISCONNECTED", restored.eventName)
        assertTrue(restored.shouldPersist)
    }

    @Test
    fun shouldAwaitNativeStopOnCallerThread_avoidsDeadlockWhileConnectBlocksNativeExecutor() {
        assertFalse(
            OpenVpnRuntimePolicy.shouldAwaitNativeStopOnCallerThread(
                runsOnNativeThread = false,
                nativeVpnJobActive = true,
            )
        )
        assertTrue(
            OpenVpnRuntimePolicy.shouldAwaitNativeStopOnCallerThread(
                runsOnNativeThread = true,
                nativeVpnJobActive = true,
            )
        )
        assertTrue(
            OpenVpnRuntimePolicy.shouldAwaitNativeStopOnCallerThread(
                runsOnNativeThread = false,
                nativeVpnJobActive = false,
            )
        )
    }

    @Test
    fun mustNotSchedulePauseResumeOnNativeExecutor_whenScheduledOnNative() {
        assertFalse(OpenVpnRuntimePolicy.mustNotSchedulePauseResumeOnNativeExecutor(true))
    }

    @Test
    fun shouldSchedulePauseResumeOnForeignThread_always() {
        assertTrue(OpenVpnRuntimePolicy.shouldSchedulePauseResumeOnForeignThread(true))
        assertTrue(OpenVpnRuntimePolicy.shouldSchedulePauseResumeOnForeignThread(false))
    }
}
