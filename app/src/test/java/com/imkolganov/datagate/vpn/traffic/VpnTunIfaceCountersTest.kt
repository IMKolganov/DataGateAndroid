package com.imkolganov.datagate.vpn.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnTunIfaceCountersTest {

    @Test
    fun mapsTunRxTxToUserDownloadUpload() {
        val counters = VpnTunIfaceCounters.mapTunIfaceToUserCounters(
            rxBytes = 111L, // ifi_ibytes = download
            txBytes = 222L, // ifi_obytes = upload
        )
        assertEquals(TrafficCounters(bytesIn = 111L, bytesOut = 222L), counters)
    }

    @Test
    fun unsupportedCounters_returnNull() {
        assertNull(VpnTunIfaceCounters.mapTunIfaceToUserCounters(-1L, 10L))
        assertNull(VpnTunIfaceCounters.mapTunIfaceToUserCounters(10L, -1L))
        assertNull(VpnTunIfaceCounters.mapTunIfaceToUserCounters(-1L, -1L))
    }

    @Test
    fun zeroCounters_areValid() {
        assertEquals(
            TrafficCounters(0, 0),
            VpnTunIfaceCounters.mapTunIfaceToUserCounters(0L, 0L),
        )
    }

    @Test
    fun ifaceMiss_shortStreakAfterConnect_isNotReported() {
        val tracker = IfaceMissTracker(graceMs = 5_000L)
        assertNull(tracker.onMiss(0L))
        assertNull(tracker.onMiss(1_000L))
        assertNull(tracker.onFound(2_000L))
    }

    @Test
    fun ifaceMiss_longStreak_isReportedOnceThenRecovery() {
        val tracker = IfaceMissTracker(graceMs = 5_000L)
        for (t in 0L..4_000L step 1_000L) assertNull(tracker.onMiss(t))
        assertEquals(5_000L, tracker.onMiss(5_000L))
        assertNull(tracker.onMiss(6_000L))
        assertEquals(7_000L, tracker.onFound(7_000L))
        assertNull(tracker.onFound(8_000L))
    }

    @Test
    fun ifaceMiss_gapBetweenMisses_startsNewStreak() {
        val tracker = IfaceMissTracker(graceMs = 5_000L, streakGapMs = 5_000L)
        assertNull(tracker.onMiss(0L))
        assertNull(tracker.onMiss(1_000L))
        // Next session: misses resume long after the previous streak.
        assertNull(tracker.onMiss(60_000L))
        assertNull(tracker.onMiss(64_000L))
        assertEquals(5_000L, tracker.onMiss(65_000L))
    }
}
