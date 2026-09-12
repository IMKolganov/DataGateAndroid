package com.imkolganov.datagate.vpn.diag

import com.imkolganov.datagate.vpn.traffic.TrafficSample
import com.imkolganov.datagate.vpn.traffic.VpnTrafficUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficStallTrackerTest {

    @Test
    fun stall_afterFifteenZeroSamples() {
        val tracker = TrafficStallTracker(stallAfterSec = 3, resendEverySec = 10)
        repeat(2) { i ->
            val tick = tracker.onTick(zeroState(i + 1), sourceMissing = false)
            assertFalse(tick.emitStall)
        }
        val stall = tracker.onTick(zeroState(3), sourceMissing = false)
        assertTrue(stall.emitStall)
        assertEquals(3, stall.zeroSeconds)
    }

    @Test
    fun stall_resendsAfterGap() {
        val tracker = TrafficStallTracker(stallAfterSec = 2, resendEverySec = 3)
        assertFalse(tracker.onTick(zeroState(1), false).emitStall)
        assertTrue(tracker.onTick(zeroState(2), false).emitStall)
        assertFalse(tracker.onTick(zeroState(3), false).emitStall)
        assertFalse(tracker.onTick(zeroState(4), false).emitStall)
        assertTrue(tracker.onTick(zeroState(5), false).emitStall)
    }

    @Test
    fun recovered_afterTrafficReturns() {
        val tracker = TrafficStallTracker(stallAfterSec = 2, resendEverySec = 99)
        tracker.onTick(zeroState(1), false)
        tracker.onTick(zeroState(2), false)
        val recovered = tracker.onTick(
            VpnTrafficUiState(
                isActive = true,
                speedInBps = 1_000,
                samples = listOf(TrafficSample(1_000, 0)),
            ),
            sourceMissing = false,
        )
        assertTrue(recovered.emitRecovered)
        assertFalse(recovered.emitStall)
    }

    @Test
    fun sourceMissing_emitsOnce() {
        val tracker = TrafficStallTracker(sourceMissingAfterSec = 2)
        val first = tracker.onTick(VpnTrafficUiState(isActive = true), sourceMissing = true)
        assertFalse(first.emitSourceMissing)
        val second = tracker.onTick(VpnTrafficUiState(isActive = true), sourceMissing = true)
        assertTrue(second.emitSourceMissing)
        val third = tracker.onTick(VpnTrafficUiState(isActive = true), sourceMissing = true)
        assertFalse(third.emitSourceMissing)
    }

    @Test
    fun inactive_resets() {
        val tracker = TrafficStallTracker(stallAfterSec = 2)
        tracker.onTick(zeroState(1), false)
        tracker.onTick(VpnTrafficUiState(isActive = false), false)
        val again = tracker.onTick(zeroState(1), false)
        assertFalse(again.emitStall)
        assertEquals(1, again.zeroSeconds)
    }

    private fun zeroState(zeros: Int) = VpnTrafficUiState(
        isActive = true,
        samples = List(zeros) { TrafficSample(0, 0) },
    )
}
