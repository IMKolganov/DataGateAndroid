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
}
