package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeIdleProbePolicyTest {

    @Test
    fun shouldDeclareIdle_false_until_timeout() {
        val started = 1_000_000L
        assertFalse(
            BridgeIdleProbePolicy.shouldDeclareIdle(
                lastActivityMs = started,
                nowMs = started + BridgeIdleProbePolicy.IDLE_TIMEOUT_MS - 1,
            )
        )
        assertTrue(
            BridgeIdleProbePolicy.shouldDeclareIdle(
                lastActivityMs = started,
                nowMs = started + BridgeIdleProbePolicy.IDLE_TIMEOUT_MS,
            )
        )
    }

    @Test
    fun shouldDeclareIdle_false_when_activity_never_started() {
        assertFalse(BridgeIdleProbePolicy.shouldDeclareIdle(lastActivityMs = 0L, nowMs = 50_000L))
        assertFalse(BridgeIdleProbePolicy.shouldDeclareIdle(lastActivityMs = -1L, nowMs = 50_000L))
    }

    @Test
    fun formatIdleReason_isStableForLogs() {
        assertEquals("wss_idle:90000ms", BridgeIdleProbePolicy.formatIdleReason(90_000L))
        assertEquals("wss_idle:90000ms", BridgeTransportLoss.formatIdleReason(90_000L))
    }

    @Test
    fun customTimeout_honoured() {
        assertTrue(
            BridgeIdleProbePolicy.shouldDeclareIdle(
                lastActivityMs = 100L,
                nowMs = 200L,
                idleTimeoutMs = 100L,
            )
        )
        assertFalse(
            BridgeIdleProbePolicy.shouldDeclareIdle(
                lastActivityMs = 100L,
                nowMs = 199L,
                idleTimeoutMs = 100L,
            )
        )
    }
}
