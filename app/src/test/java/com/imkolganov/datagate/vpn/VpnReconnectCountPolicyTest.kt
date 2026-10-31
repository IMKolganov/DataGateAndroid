package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnReconnectCountPolicyTest {

    @Test
    fun reconnecting_incrementsOncePerCycle() {
        var snap = VpnReconnectCountPolicy.Snapshot()
        snap = VpnReconnectCountPolicy.onEvent(snap, "RECONNECTING")
        assertEquals(1, snap.reconnectCount)
        assertTrue(snap.cycleActive)

        snap = VpnReconnectCountPolicy.onEvent(snap, "RECONNECTING")
        assertEquals(1, snap.reconnectCount)
        assertTrue(snap.cycleActive)

        snap = VpnReconnectCountPolicy.onEvent(snap, "CONNECTED")
        assertEquals(1, snap.reconnectCount)
        assertFalse(snap.cycleActive)

        snap = VpnReconnectCountPolicy.onEvent(snap, "RECONNECTING")
        assertEquals(2, snap.reconnectCount)
    }

    @Test
    fun reconnecting_isCaseInsensitive() {
        val snap = VpnReconnectCountPolicy.onEvent(
            VpnReconnectCountPolicy.Snapshot(),
            "  reconnecting ",
        )
        assertEquals(1, snap.reconnectCount)
        assertTrue(snap.cycleActive)
    }

    @Test
    fun paused_endsCycleKeepsCount() {
        var snap = VpnReconnectCountPolicy.Snapshot(reconnectCount = 2, cycleActive = true)
        snap = VpnReconnectCountPolicy.onEvent(snap, "PAUSED")
        assertEquals(2, snap.reconnectCount)
        assertFalse(snap.cycleActive)
    }

    @Test
    fun disconnect_resets() {
        var snap = VpnReconnectCountPolicy.Snapshot(reconnectCount = 4, cycleActive = true)
        snap = VpnReconnectCountPolicy.onEvent(snap, "DISCONNECTED")
        assertEquals(0, snap.reconnectCount)
        assertFalse(snap.cycleActive)
    }

    @Test
    fun disconnecting_error_tunFailed_reset() {
        val base = VpnReconnectCountPolicy.Snapshot(reconnectCount = 3, cycleActive = true)
        assertEquals(0, VpnReconnectCountPolicy.onEvent(base, "DISCONNECTING").reconnectCount)
        assertEquals(0, VpnReconnectCountPolicy.onEvent(base, "ERROR").reconnectCount)
        assertEquals(0, VpnReconnectCountPolicy.onEvent(base, "TUN_SETUP_FAILED").reconnectCount)
    }

    @Test
    fun selectingServer_resetsForNewConnection() {
        val snap = VpnReconnectCountPolicy.onEvent(
            VpnReconnectCountPolicy.Snapshot(reconnectCount = 3),
            "SELECTING_SERVER",
        )
        assertEquals(0, snap.reconnectCount)
    }

    @Test
    fun waitingNetworkAndConnecting_doNotIncrement() {
        var snap = VpnReconnectCountPolicy.Snapshot(reconnectCount = 1, cycleActive = false)
        snap = VpnReconnectCountPolicy.onEvent(snap, "WAITING_NETWORK")
        assertEquals(1, snap.reconnectCount)
        assertFalse(snap.cycleActive)
        snap = VpnReconnectCountPolicy.onEvent(snap, "CONNECTING")
        assertEquals(1, snap.reconnectCount)
        assertFalse(snap.cycleActive)
        snap = VpnReconnectCountPolicy.onEvent(snap, "NETWORK_CHANGED")
        assertEquals(1, snap.reconnectCount)
    }

    @Test
    fun openVpnQuietPath_connectThenConnected_staysZero() {
        var snap = VpnReconnectCountPolicy.Snapshot()
        snap = VpnReconnectCountPolicy.onEvent(snap, "CONNECTING")
        snap = VpnReconnectCountPolicy.onEvent(snap, "CONNECTED")
        assertEquals(0, snap.reconnectCount)
        assertFalse(snap.cycleActive)
        assertEquals(1, VpnReconnectCountPolicy.estimatedSessions(snap.reconnectCount, sessionActive = true))
    }

    @Test
    fun estimatedSessions_initialPlusReconnectsWhileActive() {
        assertEquals(0, VpnReconnectCountPolicy.estimatedSessions(0, sessionActive = false))
        assertEquals(1, VpnReconnectCountPolicy.estimatedSessions(0, sessionActive = true))
        assertEquals(6, VpnReconnectCountPolicy.estimatedSessions(5, sessionActive = true))
    }
}
