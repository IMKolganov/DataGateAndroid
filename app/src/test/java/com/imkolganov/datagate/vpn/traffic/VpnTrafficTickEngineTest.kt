package com.imkolganov.datagate.vpn.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTrafficTickEngineTest {

    @Test
    fun firstTick_isBaselineThenSecondTickAddsSample() {
        val engine = VpnTrafficTickEngine()
        val baseline = engine.tick(TrafficCounters(1_000, 100), nowMs = 1_000L)
        assertTrue(baseline.isActive)
        assertTrue(baseline.samples.isEmpty())
        assertEquals(0L, baseline.speedInBps)
        assertEquals(1_000L, baseline.sessionBytesIn)

        val next = engine.tick(TrafficCounters(3_000, 600), nowMs = 2_000L)
        assertEquals(2_000L, next.speedInBps)
        assertEquals(500L, next.speedOutBps)
        assertEquals(3_000L, next.sessionBytesIn)
        assertEquals(listOf(TrafficSample(2_000, 500)), next.samples)
    }

    @Test
    fun nullThenThrowEquivalent_keepsLastRates() {
        val engine = VpnTrafficTickEngine()
        engine.tick(TrafficCounters(1_000, 0), 1_000L)
        val withSample = engine.tick(TrafficCounters(2_000, 0), 2_000L)
        val afterNull = engine.tick(null, 3_000L)
        assertEquals(withSample.speedInBps, afterNull.speedInBps)
        assertEquals(withSample.samples, afterNull.samples)
        assertEquals(withSample.sessionBytesIn, afterNull.sessionBytesIn)
    }

    @Test
    fun counterReset_clearsSpeedWithoutDroppingHistory() {
        val engine = VpnTrafficTickEngine()
        engine.tick(TrafficCounters(0, 0), 1_000L)
        val growing = engine.tick(TrafficCounters(2_000, 400), 2_000L)
        val reset = engine.tick(TrafficCounters(50, 10), 3_000L)
        assertEquals(0L, reset.speedInBps)
        assertEquals(50L, reset.sessionBytesIn)
        assertEquals(growing.samples, reset.samples)
    }

    @Test
    fun sixtyOneGrowthTicks_capSamplesAtSixty() {
        val engine = VpnTrafficTickEngine()
        var bytes = 0L
        var last = engine.tick(TrafficCounters(0, 0), 0L)
        repeat(TrafficDelta.MAX_SAMPLES + 1) { i ->
            bytes += 1_000
            last = engine.tick(TrafficCounters(bytes, 0), (i + 1) * 1_000L)
        }
        assertEquals(TrafficDelta.MAX_SAMPLES, last.samples.size)
        assertTrue(TrafficDelta.shouldShowChart(last.samples.size))
    }
}
