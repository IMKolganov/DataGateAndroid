package com.imkolganov.datagate.vpn.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficDeltaTest {

    @Test
    fun firstTick_establishesBaselineWithoutSample() {
        val tick = TrafficDelta.apply(
            previous = null,
            previousAtMs = 0L,
            current = TrafficCounters(1_000, 200),
            nowMs = 1_000L,
            sessionBytesIn = 0,
            sessionBytesOut = 0,
            samples = emptyList(),
        )
        assertEquals(TrafficCounters(1_000, 200), tick.previous)
        assertEquals(1_000L, tick.previousAtMs)
        assertEquals(0L, tick.speedInBps)
        assertEquals(0L, tick.speedOutBps)
        assertEquals(1_000L, tick.sessionBytesIn)
        assertEquals(200L, tick.sessionBytesOut)
        assertTrue(tick.samples.isEmpty())
    }

    @Test
    fun firstTick_nullSource_keepsEmptyState() {
        val tick = TrafficDelta.apply(
            previous = null,
            previousAtMs = 0L,
            current = null,
            nowMs = 1_000L,
            sessionBytesIn = 0,
            sessionBytesOut = 0,
            samples = emptyList(),
        )
        assertNull(tick.previous)
        assertEquals(0L, tick.speedInBps)
        assertEquals(0L, tick.speedOutBps)
        assertTrue(tick.samples.isEmpty())
    }

    @Test
    fun laterNullSource_keepsLastRatesAndDoesNotAppend() {
        val tick = TrafficDelta.apply(
            previous = TrafficCounters(4_000, 1_000),
            previousAtMs = 1_000L,
            current = null,
            nowMs = 2_000L,
            sessionBytesIn = 4_000,
            sessionBytesOut = 1_000,
            samples = listOf(TrafficSample(100, 10)),
            lastSpeedInBps = 100,
            lastSpeedOutBps = 10,
        )
        assertEquals(TrafficCounters(4_000, 1_000), tick.previous)
        assertEquals(1_000L, tick.previousAtMs)
        assertEquals(100L, tick.speedInBps)
        assertEquals(10L, tick.speedOutBps)
        assertEquals(4_000L, tick.sessionBytesIn)
        assertEquals(1_000L, tick.sessionBytesOut)
        assertEquals(listOf(TrafficSample(100, 10)), tick.samples)
    }

    @Test
    fun growingCounters_computeBytesPerSecondAndUseRawTotals() {
        val tick = TrafficDelta.apply(
            previous = TrafficCounters(1_000, 200),
            previousAtMs = 1_000L,
            current = TrafficCounters(3_000, 700),
            nowMs = 2_000L,
            sessionBytesIn = 1_000,
            sessionBytesOut = 200,
            samples = emptyList(),
        )
        assertEquals(2_000L, tick.speedInBps)
        assertEquals(500L, tick.speedOutBps)
        assertEquals(3_000L, tick.sessionBytesIn)
        assertEquals(700L, tick.sessionBytesOut)
        assertEquals(listOf(TrafficSample(2_000, 500)), tick.samples)
    }

    @Test
    fun halfSecondInterval_scalesSpeedToPerSecond() {
        val tick = TrafficDelta.apply(
            previous = TrafficCounters(0, 0),
            previousAtMs = 1_000L,
            current = TrafficCounters(500, 100),
            nowMs = 1_500L,
            sessionBytesIn = 0,
            sessionBytesOut = 0,
            samples = emptyList(),
        )
        assertEquals(1_000L, tick.speedInBps)
        assertEquals(200L, tick.speedOutBps)
        assertEquals(500L, tick.sessionBytesIn)
        assertEquals(100L, tick.sessionBytesOut)
    }

    @Test
    fun counterReset_isNewBaselineWithoutNegativeSpeedOrSample() {
        val previousSamples = listOf(TrafficSample(1_000, 400))
        val tick = TrafficDelta.apply(
            previous = TrafficCounters(10_000, 4_000),
            previousAtMs = 5_000L,
            current = TrafficCounters(100, 40),
            nowMs = 6_000L,
            sessionBytesIn = 10_000,
            sessionBytesOut = 4_000,
            samples = previousSamples,
            lastSpeedInBps = 1_000,
            lastSpeedOutBps = 400,
        )
        assertEquals(0L, tick.speedInBps)
        assertEquals(0L, tick.speedOutBps)
        assertEquals(100L, tick.sessionBytesIn)
        assertEquals(40L, tick.sessionBytesOut)
        assertEquals(TrafficCounters(100, 40), tick.previous)
        assertEquals(previousSamples, tick.samples)
    }

    @Test
    fun ringBuffer_capsAtMaxSamples() {
        var samples = emptyList<TrafficSample>()
        repeat(TrafficDelta.MAX_SAMPLES + 15) { i ->
            samples = TrafficDelta.append(
                samples,
                TrafficSample(i.toLong(), i.toLong() * 2),
            )
        }
        assertEquals(TrafficDelta.MAX_SAMPLES, samples.size)
        assertEquals(15L, samples.first().speedInBps)
        assertEquals((TrafficDelta.MAX_SAMPLES + 14).toLong(), samples.last().speedInBps)
    }

    @Test
    fun apply_capsSamplesThroughFullTicks() {
        var previous: TrafficCounters? = null
        var previousAtMs = 0L
        var sessionIn = 0L
        var sessionOut = 0L
        var samples = emptyList<TrafficSample>()
        var bytesIn = 0L
        repeat(TrafficDelta.MAX_SAMPLES + 5) { i ->
            bytesIn += 1_000
            val tick = TrafficDelta.apply(
                previous = previous,
                previousAtMs = previousAtMs,
                current = TrafficCounters(bytesIn, 0),
                nowMs = (i + 1) * 1_000L,
                sessionBytesIn = sessionIn,
                sessionBytesOut = sessionOut,
                samples = samples,
            )
            previous = tick.previous
            previousAtMs = tick.previousAtMs
            sessionIn = tick.sessionBytesIn
            sessionOut = tick.sessionBytesOut
            samples = tick.samples
        }
        assertEquals(TrafficDelta.MAX_SAMPLES, samples.size)
    }

    @Test
    fun toUiState_copiesTickFields() {
        val tick = TrafficDelta.apply(
            previous = TrafficCounters(0, 0),
            previousAtMs = 0L,
            current = TrafficCounters(1_024, 512),
            nowMs = 1_000L,
            sessionBytesIn = 0,
            sessionBytesOut = 0,
            samples = emptyList(),
        )
        val ui = tick.toUiState(isActive = true)
        assertTrue(ui.isActive)
        assertEquals(tick.speedInBps, ui.speedInBps)
        assertEquals(tick.speedOutBps, ui.speedOutBps)
        assertEquals(tick.sessionBytesIn, ui.sessionBytesIn)
        assertEquals(tick.sessionBytesOut, ui.sessionBytesOut)
        assertEquals(tick.samples, ui.samples)
    }
}
