package com.imkolganov.datagate.vpn.xray

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConnectSessionPolicyTest {

    @Test
    fun currentSession_continuesUntilGenerationOrStopChanges() {
        assertTrue(XrayConnectSessionPolicy.isCurrent(session = 3, generation = 3, stopping = false))
        assertFalse(XrayConnectSessionPolicy.isCurrent(session = 2, generation = 3, stopping = false))
        assertFalse(XrayConnectSessionPolicy.isCurrent(session = 3, generation = 3, stopping = true))
    }

    @Test
    fun disconnect_mustNotTeardownNewerConnect() {
        var generation = 1
        val connectingSession = generation

        // User disconnect, then a new Connect arrives before the stop coroutine runs.
        generation++
        val stopSession = generation
        generation++
        val newerConnect = generation

        assertFalse(
            XrayConnectSessionPolicy.isCurrent(
                session = connectingSession,
                generation = generation,
                stopping = false,
            )
        )
        assertFalse(XrayConnectSessionPolicy.shouldApplyStop(stopSession, generation))
        assertTrue(
            XrayConnectSessionPolicy.isCurrent(
                session = newerConnect,
                generation = generation,
                stopping = false,
            )
        )
    }

    @Test
    fun pauseIsDisconnect_invalidatesInFlightConnect() {
        var generation = 4
        val connecting = generation
        generation++
        assertFalse(
            XrayConnectSessionPolicy.isCurrent(
                session = connecting,
                generation = generation,
                stopping = true,
            ),
        )
        assertTrue(XrayConnectSessionPolicy.shouldApplyStop(generation, generation))
    }

    @Test
    fun onDestroy_invalidatesConnectAndPendingStop() {
        var generation = 2
        val connectSession = generation
        val stopSession = generation
        generation++
        assertFalse(XrayConnectSessionPolicy.isCurrent(connectSession, generation, stopping = true))
        assertFalse(XrayConnectSessionPolicy.shouldApplyStop(stopSession, generation))
    }

    @Test
    fun concurrentConnect_loserDoesNotOwnTeardown() {
        var generation = 1
        val first = generation
        generation++
        val second = generation

        assertFalse(XrayConnectSessionPolicy.isCurrent(first, generation, stopping = false))
        assertTrue(XrayConnectSessionPolicy.isCurrent(second, generation, stopping = false))
        assertFalse(XrayConnectSessionPolicy.shouldApplyStop(first, generation))
    }
}
