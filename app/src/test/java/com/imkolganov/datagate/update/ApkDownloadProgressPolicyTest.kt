package com.imkolganov.datagate.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkDownloadProgressPolicyTest {

    @Test
    fun percent_unknownOrEmptyLength_isIndeterminate() {
        assertNull(ApkDownloadProgressPolicy.percent(0, 0))
        assertNull(ApkDownloadProgressPolicy.percent(1_000, ApkDownloadProgressPolicy.UNKNOWN_LENGTH))
        assertNull(ApkDownloadProgressPolicy.percent(1_000, 0))
        assertNull(ApkDownloadProgressPolicy.percent(-1, 100))
    }

    @Test
    fun percent_tracksKnownContentLength() {
        assertEquals(0, ApkDownloadProgressPolicy.percent(0, 1_000))
        assertEquals(25, ApkDownloadProgressPolicy.percent(250, 1_000))
        assertEquals(50, ApkDownloadProgressPolicy.percent(500, 1_000))
        assertEquals(100, ApkDownloadProgressPolicy.percent(1_000, 1_000))
        assertEquals(100, ApkDownloadProgressPolicy.percent(1_200, 1_000))
    }

    @Test
    fun fraction_matchesPercent() {
        assertEquals(0.42f, ApkDownloadProgressPolicy.fraction(42, 100))
        assertNull(ApkDownloadProgressPolicy.fraction(10, -1))
    }

    @Test
    fun progressModel_exposesPercentAndFraction() {
        val p = ApkDownloadProgress(bytesRead = 40, contentLength = 80)
        assertEquals(50, p.percent)
        assertEquals(0.5f, p.fraction)
        assertNull(ApkDownloadProgress(bytesRead = 40, contentLength = -1).percent)
    }

    @Test
    fun shouldPublish_everyPercentWhenLengthKnown() {
        val first = ApkDownloadProgress(0, 10_000)
        assertTrue(ApkDownloadProgressPolicy.shouldPublish(null, first))
        assertFalse(
            ApkDownloadProgressPolicy.shouldPublish(
                first,
                ApkDownloadProgress(50, 10_000),
            ),
        )
        assertTrue(
            ApkDownloadProgressPolicy.shouldPublish(
                first,
                ApkDownloadProgress(100, 10_000),
            ),
        )
        assertTrue(
            ApkDownloadProgressPolicy.shouldPublish(
                ApkDownloadProgress(9_900, 10_000),
                ApkDownloadProgress(10_000, 10_000),
            ),
        )
    }

    @Test
    fun shouldPublish_indeterminateOnlyOnLargeDeltas() {
        val start = ApkDownloadProgress(0, ApkDownloadProgressPolicy.UNKNOWN_LENGTH)
        assertTrue(ApkDownloadProgressPolicy.shouldPublish(null, start))
        assertFalse(
            ApkDownloadProgressPolicy.shouldPublish(
                start,
                ApkDownloadProgress(100, ApkDownloadProgressPolicy.UNKNOWN_LENGTH),
            ),
        )
        assertTrue(
            ApkDownloadProgressPolicy.shouldPublish(
                start,
                ApkDownloadProgress(
                    ApkDownloadProgressPolicy.INDETERMINATE_MIN_DELTA_BYTES,
                    ApkDownloadProgressPolicy.UNKNOWN_LENGTH,
                ),
            ),
        )
    }

    @Test
    fun bar_isDeterminateOnlyWhenPercentIsKnown() {
        assertEquals(ApkDownloadBar.Indeterminate, ApkDownloadProgressPolicy.bar(null))
        assertEquals(
            ApkDownloadBar.Indeterminate,
            ApkDownloadProgressPolicy.bar(
                ApkDownloadProgress(1_024, ApkDownloadProgressPolicy.UNKNOWN_LENGTH),
            ),
        )
        assertEquals(
            ApkDownloadBar.Determinate(fraction = 0.42f, percent = 42),
            ApkDownloadProgressPolicy.bar(ApkDownloadProgress(42, 100)),
        )
    }

    @Test
    fun isComplete_rejectsEmptyOrTruncatedOrOverlong() {
        assertFalse(ApkDownloadProgressPolicy.isComplete(0, 1_000))
        assertFalse(ApkDownloadProgressPolicy.isComplete(0, 0))
        assertFalse(ApkDownloadProgressPolicy.isComplete(0, ApkDownloadProgressPolicy.UNKNOWN_LENGTH))
        assertFalse(ApkDownloadProgressPolicy.isComplete(500, 1_000))
        assertFalse(ApkDownloadProgressPolicy.isComplete(1_200, 1_000))
        assertTrue(ApkDownloadProgressPolicy.isComplete(1_000, 1_000))
        assertTrue(ApkDownloadProgressPolicy.isComplete(2_048, ApkDownloadProgressPolicy.UNKNOWN_LENGTH))
    }

    @Test
    fun finishedProgress_usesBytesReadWhenLengthUnknown() {
        val known = ApkDownloadProgressPolicy.finishedProgress(500, 500)
        assertEquals(500, known.bytesRead)
        assertEquals(500, known.contentLength)
        assertEquals(100, known.percent)

        val unknown = ApkDownloadProgressPolicy.finishedProgress(
            2_048,
            ApkDownloadProgressPolicy.UNKNOWN_LENGTH,
        )
        assertEquals(2_048, unknown.bytesRead)
        assertEquals(2_048, unknown.contentLength)
        assertEquals(100, unknown.percent)
    }
}
