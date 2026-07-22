package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenVpnSessionTeardownPolicyTest {

    @Test
    fun finally_runs_only_for_current_generation() {
        assertTrue(OpenVpnSessionTeardownPolicy.shouldRunVpnJobFinally(3, 3))
        assertFalse(OpenVpnSessionTeardownPolicy.shouldRunVpnJobFinally(2, 3))
        assertFalse(OpenVpnSessionTeardownPolicy.shouldRunVpnJobFinally(4, 3))
    }

    @Test
    fun disconnected_defers_when_bridge_loss_owns_reconnect() {
        assertTrue(
            OpenVpnSessionTeardownPolicy.shouldDeferReconnectToBridgeLossFinally(
                reconnectPendingAfterJob = true
            )
        )
        assertFalse(
            OpenVpnSessionTeardownPolicy.shouldDeferReconnectToBridgeLossFinally(
                reconnectPendingAfterJob = false
            )
        )
    }

    @Test
    fun race_bridgeLoss_then_coreDisconnected_then_newStart_staleFinallySkipped() {
        // Mirrors the High finding: foreign-thread stop → DISCONNECTED may startVpn (gen++)
        // before the dying job's finally. Stale finally must not teardown; DISCONNECTED must
        // not also reconnect while reconnectPendingAfterJob is armed.
        var generation = 1
        val dyingSession = generation
        val reconnectPendingAfterJob = true

        assertTrue(
            OpenVpnSessionTeardownPolicy.shouldDeferReconnectToBridgeLossFinally(
                reconnectPendingAfterJob
            )
        )

        // Replacement session claims the globals.
        generation++
        val liveSession = generation
        assertTrue(OpenVpnSessionTeardownPolicy.shouldRunVpnJobFinally(liveSession, generation))
        assertFalse(OpenVpnSessionTeardownPolicy.shouldRunVpnJobFinally(dyingSession, generation))

        // Dying job's finally owns reconnect when flag was armed before gen bump.
        assertTrue(
            OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = true,
                desiredConnection = true,
                isStopping = false,
                isPaused = false,
            )
        )
    }

    @Test
    fun mirror_fixedPath_bridgeLoss_finallyOwnsSingleReconnect() {
        val mirror = OpenVpnSessionRaceMirror()
        val sessionA = mirror.startSession()

        mirror.onBridgeTransportLost()
        assertFalse(
            "DISCONNECTED must defer while reconnectPendingAfterJob is armed",
            mirror.onCoreDisconnected(),
        )
        assertEquals(0, mirror.reconnectFromDisconnectedCount)

        val outcome = mirror.runFinally(sessionA)
        assertEquals(OpenVpnSessionRaceMirror.FinallyOutcome.TEARDOWN_AND_RECONNECT, outcome)
        assertEquals(1, mirror.reconnectFromFinallyCount)
        assertEquals(2, mirror.generation)
        assertEquals(2, mirror.ownerGeneration)
        assertFalse(mirror.reconnectPendingAfterJob)
    }

    @Test
    fun mirror_staleFinally_doesNotTearDownReplacementOwner() {
        val mirror = OpenVpnSessionRaceMirror()
        val sessionA = mirror.startSession()
        mirror.onBridgeTransportLost()

        // Replacement claims globals before A's finally (the race DISCONNECTED used to cause).
        val sessionB = mirror.startSession()
        assertEquals(2, sessionB)
        assertEquals(2, mirror.ownerGeneration)

        val outcomeA = mirror.runFinally(sessionA)
        assertEquals(OpenVpnSessionRaceMirror.FinallyOutcome.SKIPPED_STALE, outcomeA)
        assertEquals(null, mirror.tornDownGeneration)
        assertEquals(2, mirror.ownerGeneration)
        assertEquals(0, mirror.reconnectFromFinallyCount)
        assertFalse(
            "Stale finally must clear reconnectPendingAfterJob so live DISCONNECTED can reconnect",
            mirror.reconnectPendingAfterJob,
        )
        assertFalse(
            OpenVpnSessionTeardownPolicy.shouldDeferReconnectToBridgeLossFinally(
                mirror.reconnectPendingAfterJob
            )
        )
    }

    @Test
    fun staleFinally_policy_clearsReconnectPending() {
        assertTrue(OpenVpnSessionTeardownPolicy.shouldClearReconnectPendingOnStaleFinally())
    }

    @Test
    fun pendingConnect_deferredWhileBridgeLossOwnsReconnect_exceptFinallyRetry() {
        assertTrue(
            OpenVpnSessionTeardownPolicy.shouldDeferPendingConnectWhileBridgeLossOwnsReconnect(
                reconnectPendingAfterJob = true,
                reason = "network_available",
            )
        )
        assertTrue(
            OpenVpnSessionTeardownPolicy.shouldDeferPendingConnectWhileBridgeLossOwnsReconnect(
                reconnectPendingAfterJob = true,
                reason = "core_disconnected_reconnect",
            )
        )
        assertFalse(
            OpenVpnSessionTeardownPolicy.shouldDeferPendingConnectWhileBridgeLossOwnsReconnect(
                reconnectPendingAfterJob = true,
                reason = OpenVpnSessionTeardownPolicy.BRIDGE_TRANSPORT_LOST_RETRY_REASON,
            )
        )
        assertFalse(
            OpenVpnSessionTeardownPolicy.shouldDeferPendingConnectWhileBridgeLossOwnsReconnect(
                reconnectPendingAfterJob = false,
                reason = "network_available",
            )
        )
    }
}

class OpenVpnCoreLogFilterTest {

    @Before
    fun reset() {
        OpenVpnCoreLogFilter.resetRateLimitForTests()
    }

    @Test
    fun persists_warn_and_error_lines_only() {
        assertTrue(OpenVpnCoreLogFilter.shouldPersistToDebugFile("ERROR: auth failed", nowMs = 1_000L))
        assertTrue(OpenVpnCoreLogFilter.shouldPersistToDebugFile("WARN: slow path", nowMs = 1_000L))
        assertTrue(OpenVpnCoreLogFilter.shouldPersistToDebugFile("Fatal: abort", nowMs = 1_000L))
        assertFalse(OpenVpnCoreLogFilter.shouldPersistToDebugFile("Tunnel Options: ...", nowMs = 1_000L))
        assertFalse(OpenVpnCoreLogFilter.shouldPersistToDebugFile("EVENT: CONNECTED", nowMs = 1_000L))
    }

    @Test
    fun rate_limits_bursts_within_window() {
        val t0 = 10_000L
        repeat(8) {
            assertTrue(
                "line $it should pass",
                OpenVpnCoreLogFilter.shouldPersistToDebugFile("ERROR: $it", nowMs = t0)
            )
        }
        assertFalse(OpenVpnCoreLogFilter.shouldPersistToDebugFile("ERROR: overflow", nowMs = t0 + 100))
        assertTrue(OpenVpnCoreLogFilter.shouldPersistToDebugFile("ERROR: next window", nowMs = t0 + 1_000))
    }
}
