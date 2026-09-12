package com.imkolganov.datagate.vpn.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun nonPositiveInterval_keepsLastRatesWithoutSample() {
        val samples = listOf(TrafficSample(10, 2))
        val tick = TrafficDelta.apply(
            previous = TrafficCounters(100, 20),
            previousAtMs = 2_000L,
            current = TrafficCounters(200, 40),
            nowMs = 2_000L,
            sessionBytesIn = 100,
            sessionBytesOut = 20,
            samples = samples,
            lastSpeedInBps = 10,
            lastSpeedOutBps = 2,
        )
        assertEquals(10L, tick.speedInBps)
        assertEquals(2L, tick.speedOutBps)
        assertEquals(2_000L, tick.previousAtMs)
        assertEquals(samples, tick.samples)
        assertEquals(200L, tick.sessionBytesIn)
    }

    @Test
    fun oneSidedCounterDrop_isTreatedAsReset() {
        val samples = listOf(TrafficSample(50, 5))
        val tick = TrafficDelta.apply(
            previous = TrafficCounters(5_000, 100),
            previousAtMs = 1_000L,
            current = TrafficCounters(4_000, 180),
            nowMs = 2_000L,
            sessionBytesIn = 5_000,
            sessionBytesOut = 100,
            samples = samples,
        )
        assertEquals(0L, tick.speedInBps)
        assertEquals(0L, tick.speedOutBps)
        assertEquals(4_000L, tick.sessionBytesIn)
        assertEquals(180L, tick.sessionBytesOut)
        assertEquals(samples, tick.samples)
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
    fun append_zeroOrNegativeMax_returnsEmpty() {
        val sample = TrafficSample(1, 2)
        assertTrue(TrafficDelta.append(listOf(sample), sample, maxSamples = 0).isEmpty())
        assertTrue(TrafficDelta.append(listOf(sample), sample, maxSamples = -3).isEmpty())
    }

    @Test
    fun append_oversizedInput_keepsOnlyLatestWindow() {
        val oversized = (0 until 80).map { TrafficSample(it.toLong(), 0) }
        val next = TrafficDelta.append(oversized, TrafficSample(99, 1), maxSamples = 5)
        assertEquals(5, next.size)
        assertEquals(listOf(76L, 77L, 78L, 79L, 99L), next.map { it.speedInBps })
    }

    @Test
    fun shouldShowChart_requiresTwoSamples() {
        assertFalse(TrafficDelta.shouldShowChart(0))
        assertFalse(TrafficDelta.shouldShowChart(1))
        assertTrue(TrafficDelta.shouldShowChart(2))
        assertTrue(TrafficDelta.shouldShowChart(TrafficDelta.MAX_SAMPLES))
    }

    @Test
    fun toUiState_inactive_keepsCounters() {
        val ui = TrafficDeltaResult(
            previous = TrafficCounters(1, 2),
            previousAtMs = 10,
            sessionBytesIn = 1,
            sessionBytesOut = 2,
            samples = listOf(TrafficSample(3, 4)),
            speedInBps = 3,
            speedOutBps = 4,
        ).toUiState(isActive = false)
        assertFalse(ui.isActive)
        assertEquals(3L, ui.speedInBps)
        assertEquals(listOf(TrafficSample(3, 4)), ui.samples)
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
        ).let { baseline ->
            TrafficDelta.apply(
                previous = baseline.previous,
                previousAtMs = baseline.previousAtMs,
                current = TrafficCounters(2_048, 1_024),
                nowMs = 2_000L,
                sessionBytesIn = baseline.sessionBytesIn,
                sessionBytesOut = baseline.sessionBytesOut,
                samples = baseline.samples,
            )
        }
        val ui = tick.toUiState(isActive = true)
        assertTrue(ui.isActive)
        assertEquals(1_024L, tick.speedInBps)
        assertEquals(512L, tick.speedOutBps)
        assertEquals(2_048L, tick.sessionBytesIn)
        assertEquals(1_024L, tick.sessionBytesOut)
        assertEquals(tick.speedInBps, ui.speedInBps)
        assertEquals(tick.speedOutBps, ui.speedOutBps)
        assertEquals(tick.sessionBytesIn, ui.sessionBytesIn)
        assertEquals(tick.sessionBytesOut, ui.sessionBytesOut)
        assertEquals(tick.samples, ui.samples)
    }
}
