package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertEquals
import org.junit.Test

class XrayQueryStatusPolicyTest {

    @Test
    fun liveTun_winsOverStaleDisconnectedCache() {
        val resolved = XrayQueryStatusPolicy.resolve(
            running = true,
            hasTun = true,
            stopping = false,
            lastEventName = "DISCONNECTED",
            lastEventInfo = "stale",
            disconnectedInfo = "down",
            connectedInfo = "up",
            connectingInfo = "wait",
        )
        assertEquals("CONNECTED" to "up", resolved)
    }

    @Test
    fun staleConnectedCache_withoutTun_isDisconnected() {
        val resolved = XrayQueryStatusPolicy.resolve(
            running = false,
            hasTun = false,
            stopping = false,
            lastEventName = "CONNECTED",
            lastEventInfo = "Session active",
            disconnectedInfo = "down",
            connectedInfo = "up",
            connectingInfo = "wait",
        )
        assertEquals("DISCONNECTED" to "down", resolved)
    }

    @Test
    fun inFlightConnect_keepsConnecting() {
        val resolved = XrayQueryStatusPolicy.resolve(
            running = false,
            hasTun = false,
            stopping = false,
            lastEventName = "CONNECTING",
            lastEventInfo = "",
            disconnectedInfo = "down",
            connectedInfo = "up",
            connectingInfo = "wait",
        )
        assertEquals("CONNECTING" to "wait", resolved)
    }

    @Test
    fun waitingNetwork_andReconnecting_areKept() {
        assertEquals(
            "WAITING_NETWORK",
            XrayQueryStatusPolicy.resolve(
                running = false,
                hasTun = false,
                stopping = false,
                lastEventName = "WAITING_NETWORK",
                lastEventInfo = "no net",
                disconnectedInfo = "down",
                connectedInfo = "up",
                connectingInfo = "wait",
            ).first,
        )
        assertEquals(
            "RECONNECTING",
            XrayQueryStatusPolicy.resolve(
                running = false,
                hasTun = false,
                stopping = false,
                lastEventName = "RECONNECTING",
                lastEventInfo = "",
                disconnectedInfo = "down",
                connectedInfo = "up",
                connectingInfo = "wait",
            ).first,
        )
    }

    @Test
    fun errorWithoutTun_staysError() {
        val resolved = XrayQueryStatusPolicy.resolve(
            running = false,
            hasTun = false,
            stopping = false,
            lastEventName = "ERROR",
            lastEventInfo = "lib missing",
            disconnectedInfo = "down",
            connectedInfo = "up",
            connectingInfo = "wait",
        )
        assertEquals("ERROR" to "lib missing", resolved)
    }

    @Test
    fun paused_winsOverStaleConnectedAndStopping() {
        val resolved = XrayQueryStatusPolicy.resolve(
            running = false,
            hasTun = false,
            stopping = false,
            paused = true,
            lastEventName = "CONNECTED",
            lastEventInfo = "up",
            disconnectedInfo = "down",
            connectedInfo = "up",
            connectingInfo = "wait",
            pausedInfo = "paused",
        )
        assertEquals("PAUSED" to "paused", resolved)
    }

    @Test
    fun stopping_alwaysDisconnected() {
        val resolved = XrayQueryStatusPolicy.resolve(
            running = true,
            hasTun = true,
            stopping = true,
            lastEventName = "CONNECTED",
            lastEventInfo = "up",
            disconnectedInfo = "down",
            connectedInfo = "up",
            connectingInfo = "wait",
        )
        assertEquals("DISCONNECTED" to "down", resolved)
    }
}
