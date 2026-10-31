package com.imkolganov.datagate.vpn.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OpenVpnLiveTrafficReadTest {

    @Test
    fun ifaceWins_andDoesNotCallTunStats() {
        var fallbackCalled = false
        val resolved = OpenVpnLiveTrafficRead.resolve(
            ifaceCounters = TrafficCounters(11, 22),
            tunStatsProvider = {
                fallbackCalled = true
                TrafficCounters(99, 88)
            },
        )
        assertEquals(TrafficCounters(11, 22), resolved)
        assertFalse(fallbackCalled)
    }

    @Test
    fun missingIface_usesTunStats() {
        val resolved = OpenVpnLiveTrafficRead.resolve(
            ifaceCounters = null,
            tunStatsProvider = { TrafficCounters(7, 3) },
        )
        assertEquals(TrafficCounters(7, 3), resolved)
    }

    @Test
    fun bothMissing_returnsNull() {
        assertNull(
            OpenVpnLiveTrafficRead.resolve(
                ifaceCounters = null,
                tunStatsProvider = { null },
            ),
        )
    }
}
