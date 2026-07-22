package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeIdleProbePolicyTest {

    @Test
    fun shouldDeclareStall_onlyWhenOutboundUnansweredPastTimeout() {
        val t0 = 1_000_000L
        val timeout = BridgeIdleProbePolicy.IDLE_TIMEOUT_MS

        // Quiet tunnel — OkHttp ping owns liveness; no OpenVPN outbound.
        assertFalse(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = 0L,
                lastInboundMs = 0L,
                nowMs = t0 + timeout,
            )
        )

        // Outbound with timely inbound reply — healthy.
        assertFalse(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = t0,
                lastInboundMs = t0 + 1_000L,
                nowMs = t0 + timeout,
            )
        )

        // Outbound then silence past timeout — stall.
        assertTrue(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = t0,
                lastInboundMs = 0L,
                nowMs = t0 + timeout,
            )
        )
        assertFalse(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = t0,
                lastInboundMs = 0L,
                nowMs = t0 + timeout - 1,
            )
        )

        // Last inbound older than last outbound: clock from outbound, not old inbound.
        assertFalse(
            "Must wait full timeout after unanswered outbound, not from prior inbound",
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = t0 + 50_000L,
                lastInboundMs = t0,
                nowMs = t0 + 50_000L + timeout - 1,
            ),
        )
        assertTrue(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = t0 + 50_000L,
                lastInboundMs = t0,
                nowMs = t0 + 50_000L + timeout,
            ),
        )
    }

    @Test
    fun quietTunnel_withOnlyInbound_isNotStall() {
        val t0 = 1_000_000L
        assertFalse(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = 0L,
                lastInboundMs = t0,
                nowMs = t0 + BridgeIdleProbePolicy.IDLE_TIMEOUT_MS * 2,
            )
        )
    }

    @Test
    fun formatIdleReason_isStableForLogs() {
        assertEquals("wss_stall:90000ms", BridgeIdleProbePolicy.formatIdleReason(90_000L))
        assertEquals("wss_stall:90000ms", BridgeTransportLoss.formatIdleReason(90_000L))
    }

    @Test
    fun customTimeout_honoured() {
        assertTrue(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = 100L,
                lastInboundMs = 0L,
                nowMs = 200L,
                stallTimeoutMs = 100L,
            )
        )
        assertFalse(
            BridgeIdleProbePolicy.shouldDeclareStall(
                lastOutboundMs = 100L,
                lastInboundMs = 0L,
                nowMs = 199L,
                stallTimeoutMs = 100L,
            )
        )
    }
}
