package com.imkolganov.datagate.vpn

import com.imkolganov.datagate.vpn.xray.XrayConnectSessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSessionCyclePolicyTest {

    private val openVpn = OpenVpn3Service.ENGINE_OPENVPN
    private val xray = OpenVpn3Service.ENGINE_XRAY

    @Test
    fun catalog_startsWithDisconnectThenConnectThenTunnel() {
        val catalog = VpnSessionPhase.catalog
        assertEquals(VpnSessionPhase.entries.toSet(), catalog.toSet())
        assertEquals(VpnSessionPhase.DISCONNECTED, catalog[0])
        assertEquals(VpnSessionPhase.CONNECTING, catalog[1])
        assertEquals(VpnSessionPhase.CONNECTED, catalog[2])
        assertEquals(VpnSessionPhase.DISCONNECTING, catalog[3])
        assertEquals(VpnSessionPhase.PAUSED, catalog[4])
        assertEquals(VpnSessionPhase.RESUMED, catalog[5])
        assertEquals(VpnSessionPhase.WAITING_NETWORK, catalog[6])
        assertEquals(VpnSessionPhase.RECONNECTING, catalog[7])
        assertEquals(VpnSessionPhase.ERROR, catalog[8])
        assertEquals(VpnSessionPhase.TUN_SETUP_FAILED, catalog[9])
    }

    @Test
    fun everyCatalogPhase_roundTripsFromEventName() {
        for (phase in VpnSessionPhase.catalog) {
            assertEquals(phase, VpnSessionPhase.fromEventName(phase.eventName))
            assertEquals(phase, VpnSessionPhase.fromEventName(phase.eventName.lowercase()))
        }
        assertNull(VpnSessionPhase.fromEventName("UNKNOWN"))
        assertNull(VpnSessionPhase.fromEventName("NETWORK_CHANGED"))
        assertNull(VpnSessionPhase.fromEventName(""))
    }

    @Test
    fun xray_doesNotSupportOpenVpnOnlyPhases() {
        val openVpnOnly = listOf(
            VpnSessionPhase.PAUSED,
            VpnSessionPhase.RESUMED,
            VpnSessionPhase.RESOLVE,
            VpnSessionPhase.WAIT,
            VpnSessionPhase.GET_CONFIG,
            VpnSessionPhase.ASSIGN_IP,
        )
        for (phase in openVpnOnly) {
            assertTrue(phase.name, VpnSessionCyclePolicy.isSupportedBy(phase, openVpn))
            assertFalse(phase.name, VpnSessionCyclePolicy.isSupportedBy(phase, xray))
        }
        for (phase in listOf(
            VpnSessionPhase.DISCONNECTED,
            VpnSessionPhase.CONNECTING,
            VpnSessionPhase.CONNECTED,
            VpnSessionPhase.DISCONNECTING,
            VpnSessionPhase.WAITING_NETWORK,
            VpnSessionPhase.RECONNECTING,
            VpnSessionPhase.ERROR,
        )) {
            assertTrue(phase.name, VpnSessionCyclePolicy.isSupportedBy(phase, xray))
        }
    }

    @Test
    fun openVpn_happyPath_connectThenDisconnect() {
        var state = VpnSessionCyclePolicy.CycleState()
        state = VpnSessionCyclePolicy.markActiveEngine(state, openVpn)
        state = walk(
            state,
            openVpn,
            "CONNECTING",
            "RESOLVE",
            "WAIT",
            "GET_CONFIG",
            "ASSIGN_IP",
            "CONNECTED",
        )
        assertEquals(VpnSessionPhase.CONNECTED, state.phase)
        assertTrue(state.ui.connected)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.monitorOwner)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.storeOwner)

        state = walk(state, openVpn, "DISCONNECTING", "DISCONNECTED")
        assertEquals(VpnSessionPhase.DISCONNECTED, state.phase)
        assertFalse(state.ui.connectRequested)
        assertNull(state.monitorOwner)
        assertNull(state.storeOwner)
    }

    @Test
    fun xray_happyPath_connectThenDisconnect() {
        var state = VpnSessionCyclePolicy.CycleState()
        val (session, started) = VpnSessionCyclePolicy.beginXrayConnect(state)
        state = started
        assertTrue(
            XrayConnectSessionPolicy.isCurrent(session, state.xrayGeneration, state.xrayStopping),
        )
        state = walk(state, xray, "CONNECTING", "CONNECTED")
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.monitorOwner)
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.storeOwner)

        val (stopSession, stopping) = VpnSessionCyclePolicy.beginXrayStop(state)
        state = stopping
        assertTrue(XrayConnectSessionPolicy.shouldApplyStop(stopSession, state.xrayGeneration))
        state = VpnSessionCyclePolicy.teardownEngine(state, VpnTunnelSessionStore.OWNER_XRAY)
        state = walk(state, xray, "DISCONNECTED")
        assertNull(state.monitorOwner)
        assertNull(state.storeOwner)
        assertEquals(VpnSessionPhase.DISCONNECTED, state.phase)
    }

    @Test
    fun openVpn_pauseResume_cycleKeepsStoreDropsMonitorWhilePaused() {
        var state = connected(openVpn)
        assertTrue(
            VpnSessionCyclePolicy.isAllowedTransition(
                VpnSessionPhase.CONNECTED,
                VpnSessionPhase.PAUSED,
                openVpn,
            ),
        )
        state = walk(state, openVpn, "PAUSED")
        assertTrue(state.ui.paused)
        assertFalse(state.ui.connected)
        assertNull(state.monitorOwner)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.storeOwner)

        state = walk(state, openVpn, "RESUMED", "CONNECTING", "CONNECTED")
        assertTrue(state.ui.connected)
        assertFalse(state.ui.paused)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.monitorOwner)
    }

    @Test
    fun openVpn_pauseThenUserDisconnect() {
        var state = walk(connected(openVpn), openVpn, "PAUSED")
        state = VpnSessionCyclePolicy.userDisconnect(state)
        assertEquals(VpnSessionPhase.DISCONNECTED, state.phase)
        assertNull(state.activeEngine)
        assertNull(state.monitorOwner)
        assertNull(state.storeOwner)
    }

    @Test
    fun openVpn_waitingNetworkThenRecover() {
        var state = VpnSessionCyclePolicy.markActiveEngine(VpnSessionCyclePolicy.CycleState(), openVpn)
        state = walk(state, openVpn, "CONNECTING", "WAITING_NETWORK")
        assertTrue(state.ui.connectRequested)
        assertFalse(state.ui.connected)
        assertNull(state.monitorOwner)

        state = walk(state, openVpn, "CONNECTING", "CONNECTED")
        assertTrue(state.ui.connected)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.monitorOwner)
    }

    @Test
    fun openVpn_dropWhileConnected_reconnects() {
        var state = connected(openVpn)
        state = walk(state, openVpn, "RECONNECTING", "CONNECTING", "CONNECTED")
        assertTrue(state.ui.connected)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.monitorOwner)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.storeOwner)
    }

    @Test
    fun openVpn_errorDuringConnect_clearsResources() {
        var state = VpnSessionCyclePolicy.markActiveEngine(VpnSessionCyclePolicy.CycleState(), openVpn)
        state = walk(state, openVpn, "CONNECTING", "ERROR")
        assertEquals(VpnSessionPhase.ERROR, state.phase)
        assertFalse(state.ui.connectRequested)
        assertNull(state.monitorOwner)
        assertNull(state.storeOwner)
        assertTrue(
            VpnSessionCyclePolicy.isAllowedTransition(
                VpnSessionPhase.ERROR,
                VpnSessionPhase.CONNECTING,
                openVpn,
            ),
        )
    }

    @Test
    fun xray_errorBeforeConnected_clearsLeftoverSession() {
        var state = VpnSessionCyclePolicy.beginXrayConnect(VpnSessionCyclePolicy.CycleState()).second
        state = walk(state, xray, "CONNECTING", "ERROR")
        state = VpnSessionCyclePolicy.teardownEngine(state, VpnTunnelSessionStore.OWNER_XRAY)
        assertEquals(VpnSessionPhase.ERROR, state.phase)
        assertNull(state.monitorOwner)
        assertNull(state.storeOwner)
    }

    @Test
    fun xray_tunSetupFailed_isTerminalThenRetry() {
        var state = VpnSessionCyclePolicy.beginXrayConnect(VpnSessionCyclePolicy.CycleState()).second
        state = walk(state, xray, "CONNECTING", "TUN_SETUP_FAILED")
        assertEquals(VpnSessionPhase.TUN_SETUP_FAILED, state.phase)
        assertTrue(
            VpnSessionCyclePolicy.isAllowedTransition(
                VpnSessionPhase.TUN_SETUP_FAILED,
                VpnSessionPhase.CONNECTING,
                xray,
            ),
        )
        state = walk(state, xray, "CONNECTING", "CONNECTED")
        assertTrue(state.ui.connected)
    }

    @Test
    fun engineSwitch_openVpnToXray_lateOpenVpnTeardownKeepsXrayResources() {
        var state = connected(openVpn)
        val (xraySession, switched) = VpnSessionCyclePolicy.beginXrayConnect(state)
        state = switched
        assertEquals(xray, state.activeEngine)
        assertTrue(XrayConnectSessionPolicy.isCurrent(xraySession, state.xrayGeneration, false))

        state = walk(state, xray, "CONNECTING", "CONNECTED")
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.monitorOwner)
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.storeOwner)

        val peer = VpnSessionCyclePolicy.applyBroadcast(state, "DISCONNECTED", openVpn)
        assertTrue(peer.ignored)
        assertEquals(VpnSessionPhase.CONNECTED, peer.phase)

        state = VpnSessionCyclePolicy.teardownEngine(peer, VpnTunnelSessionStore.OWNER_OPENVPN)
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.monitorOwner)
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.storeOwner)
        assertTrue(state.ui.connected)
    }

    @Test
    fun engineSwitch_xrayToOpenVpn_lateXrayTeardownKeepsOpenVpnResources() {
        var state = VpnSessionCyclePolicy.beginXrayConnect(VpnSessionCyclePolicy.CycleState()).second
        state = walk(state, xray, "CONNECTING", "CONNECTED")
        state = VpnSessionCyclePolicy.markActiveEngine(state, openVpn)
        state = walk(state, openVpn, "CONNECTING", "CONNECTED")
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.monitorOwner)

        val peer = VpnSessionCyclePolicy.applyBroadcast(state, "DISCONNECTED", xray)
        assertTrue(peer.ignored)
        state = VpnSessionCyclePolicy.teardownEngine(peer, VpnTunnelSessionStore.OWNER_XRAY)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.monitorOwner)
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.storeOwner)
    }

    @Test
    fun userDisconnect_clearsBothEnginesAndActiveEnginePref() {
        val state = VpnSessionCyclePolicy.userDisconnect(connected(openVpn))
        assertNull(state.activeEngine)
        assertNull(state.monitorOwner)
        assertNull(state.storeOwner)
        assertEquals(VpnSessionPhase.DISCONNECTED, state.phase)
    }

    @Test
    fun idleQueryDisconnected_ignoredWhileConnectRequested() {
        var state = VpnSessionCyclePolicy.markActiveEngine(VpnSessionCyclePolicy.CycleState(), openVpn)
        state = walk(state, openVpn, "CONNECTING")
        val afterQuery = VpnSessionCyclePolicy.applyBroadcast(
            state,
            eventName = "DISCONNECTED",
            eventEngine = openVpn,
            fromQuery = true,
        )
        assertTrue(afterQuery.ignored)
        assertEquals(VpnSessionPhase.CONNECTING, afterQuery.phase)
        assertTrue(afterQuery.ui.connectRequested)
    }

    @Test
    fun processDeath_activePhasesRestoreToDisconnected() {
        val active = VpnSessionPhase.catalog.filter(VpnSessionCyclePolicy::diesWithProcess)
        assertEquals(
            setOf(
                VpnSessionPhase.CONNECTED,
                VpnSessionPhase.CONNECTING,
                VpnSessionPhase.DISCONNECTING,
                VpnSessionPhase.PAUSED,
                VpnSessionPhase.RESUMED,
                VpnSessionPhase.RECONNECTING,
                VpnSessionPhase.WAITING_NETWORK,
            ),
            active.toSet(),
        )
        for (phase in VpnSessionPhase.catalog) {
            val restored = OpenVpnRuntimePolicy.restoreCachedStatus(phase.eventName, "cached")
            if (VpnSessionCyclePolicy.diesWithProcess(phase)) {
                assertEquals(phase.name, "DISCONNECTED", restored.eventName)
                assertTrue(phase.name, restored.shouldPersist)
            } else {
                assertEquals(phase.name, phase.eventName, restored.eventName)
                assertFalse(phase.name, restored.shouldPersist)
            }
        }
    }

    @Test
    fun sameEngineReconnect_replacesMonitorAndStore() {
        var state = connected(openVpn)
        state = walk(state, openVpn, "CONNECTING", "CONNECTED")
        assertEquals(VpnTunnelSessionStore.OWNER_OPENVPN, state.monitorOwner)
        assertEquals(VpnSessionPhase.CONNECTED, state.phase)
    }

    @Test
    fun xray_disconnectThenImmediateConnect_staleStopDoesNotWin() {
        var state = VpnSessionCyclePolicy.beginXrayConnect(VpnSessionCyclePolicy.CycleState()).second
        state = walk(state, xray, "CONNECTING")
        val connectingSession = state.xrayGeneration

        val (stopSession, stopping) = VpnSessionCyclePolicy.beginXrayStop(state)
        state = stopping
        val (newSession, restarted) = VpnSessionCyclePolicy.beginXrayConnect(state)
        state = restarted

        assertFalse(
            XrayConnectSessionPolicy.isCurrent(
                connectingSession,
                state.xrayGeneration,
                state.xrayStopping,
            ),
        )
        assertFalse(XrayConnectSessionPolicy.shouldApplyStop(stopSession, state.xrayGeneration))
        assertTrue(
            XrayConnectSessionPolicy.isCurrent(newSession, state.xrayGeneration, state.xrayStopping),
        )

        state = walk(state, xray, "CONNECTED")
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.monitorOwner)
    }

    @Test
    fun xray_twoOverlappingConnects_onlyNewerSessionClaimsTunnel() {
        var state = VpnSessionCyclePolicy.CycleState()
        val (first, afterFirst) = VpnSessionCyclePolicy.beginXrayConnect(state)
        state = afterFirst
        val (second, afterSecond) = VpnSessionCyclePolicy.beginXrayConnect(state)
        state = afterSecond

        assertFalse(XrayConnectSessionPolicy.isCurrent(first, state.xrayGeneration, false))
        assertTrue(XrayConnectSessionPolicy.isCurrent(second, state.xrayGeneration, false))
        state = walk(state, xray, "CONNECTING", "CONNECTED")
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.storeOwner)
    }

    @Test
    fun openVpn_fullProgramCycle_includingAppReopenQuery() {
        var state = VpnSessionCyclePolicy.markActiveEngine(VpnSessionCyclePolicy.CycleState(), openVpn)
        state = walk(
            state,
            openVpn,
            "SELECTING_SERVER",
            "SELECTED_SERVER",
            "DOWNLOADING_CONFIG",
            "CONFIG_RECEIVED",
            "CONNECTING",
            "CONNECTED",
            "PAUSED",
            "RESUMED",
            "CONNECTED",
            "WAITING_NETWORK",
            "CONNECTING",
            "CONNECTED",
            "DISCONNECTING",
            "DISCONNECTED",
        )
        assertEquals(VpnSessionPhase.DISCONNECTED, state.phase)
        assertNull(state.monitorOwner)

        val afterDeath = OpenVpnRuntimePolicy.restoreCachedStatus("CONNECTED", "Session active")
        assertEquals("DISCONNECTED", afterDeath.eventName)

        val queryAfterDeath = VpnSessionCyclePolicy.applyBroadcast(
            VpnSessionCyclePolicy.CycleState(),
            eventName = "DISCONNECTED",
            eventEngine = openVpn,
            fromQuery = true,
        )
        assertFalse(queryAfterDeath.ignored)
        assertEquals(VpnSessionPhase.DISCONNECTED, queryAfterDeath.phase)
    }

    @Test
    fun xray_fullProgramCycle_preconnectThenTunnelThenErrorRetry() {
        var state = VpnSessionCyclePolicy.beginXrayConnect(VpnSessionCyclePolicy.CycleState()).second
        state = walk(
            state,
            xray,
            "SELECTING_SERVER",
            "DOWNLOADING_CONFIG",
            "CONNECTING",
            "CONNECTED",
            "DISCONNECTING",
            "DISCONNECTED",
            "CONNECTING",
            "ERROR",
            "CONNECTING",
            "CONNECTED",
        )
        assertTrue(state.ui.connected)
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.monitorOwner)
        state = VpnSessionCyclePolicy.userDisconnect(state)
        assertEquals(VpnSessionPhase.DISCONNECTED, state.phase)
        assertNull(state.monitorOwner)
    }

    @Test
    fun allowedTransitions_coverHappyAndFailureEdges() {
        val edges = listOf(
            Triple(VpnSessionPhase.DISCONNECTED, VpnSessionPhase.CONNECTING, openVpn),
            Triple(VpnSessionPhase.CONNECTING, VpnSessionPhase.CONNECTED, openVpn),
            Triple(VpnSessionPhase.CONNECTED, VpnSessionPhase.DISCONNECTING, openVpn),
            Triple(VpnSessionPhase.DISCONNECTING, VpnSessionPhase.DISCONNECTED, openVpn),
            Triple(VpnSessionPhase.CONNECTED, VpnSessionPhase.PAUSED, openVpn),
            Triple(VpnSessionPhase.PAUSED, VpnSessionPhase.RESUMED, openVpn),
            Triple(VpnSessionPhase.CONNECTED, VpnSessionPhase.CONNECTING, openVpn),
            Triple(VpnSessionPhase.CONNECTED, VpnSessionPhase.CONNECTING, xray),
            Triple(VpnSessionPhase.CONNECTED, VpnSessionPhase.RECONNECTING, openVpn),
            Triple(VpnSessionPhase.CONNECTING, VpnSessionPhase.WAITING_NETWORK, openVpn),
            Triple(VpnSessionPhase.CONNECTING, VpnSessionPhase.ERROR, openVpn),
            Triple(VpnSessionPhase.DISCONNECTED, VpnSessionPhase.CONNECTING, xray),
            Triple(VpnSessionPhase.CONNECTING, VpnSessionPhase.CONNECTED, xray),
            Triple(VpnSessionPhase.CONNECTED, VpnSessionPhase.DISCONNECTED, xray),
            Triple(VpnSessionPhase.CONNECTING, VpnSessionPhase.ERROR, xray),
        )
        for ((from, to, engine) in edges) {
            assertTrue("$from → $to ($engine)", VpnSessionCyclePolicy.isAllowedTransition(from, to, engine))
        }
        assertFalse(
            VpnSessionCyclePolicy.isAllowedTransition(
                VpnSessionPhase.CONNECTED,
                VpnSessionPhase.PAUSED,
                xray,
            ),
        )
        assertTrue(
            VpnSessionCyclePolicy.isAllowedTransition(
                VpnSessionPhase.CONNECTED,
                VpnSessionPhase.WAITING_NETWORK,
                xray,
            ),
        )
    }

    @Test
    fun uiFlags_matchProductSemanticsForEveryPhase() {
        for (phase in VpnSessionPhase.catalog) {
            val flags = VpnSessionCyclePolicy.uiFlags(phase)
            when (phase) {
                VpnSessionPhase.CONNECTED -> {
                    assertTrue(flags.connected)
                    assertTrue(flags.connectRequested)
                    assertFalse(flags.paused)
                }
                VpnSessionPhase.PAUSED -> {
                    assertFalse(flags.connected)
                    assertTrue(flags.connectRequested)
                    assertTrue(flags.paused)
                }
                VpnSessionPhase.DISCONNECTED,
                VpnSessionPhase.DISCONNECTING,
                VpnSessionPhase.ERROR,
                VpnSessionPhase.TUN_SETUP_FAILED -> {
                    assertFalse(flags.connected)
                    assertFalse(flags.connectRequested)
                    assertFalse(flags.paused)
                }
                else -> {
                    assertFalse(phase.name, flags.connected)
                    assertTrue(phase.name, flags.connectRequested)
                    assertFalse(phase.name, flags.paused)
                }
            }
        }
    }

    @Test
    fun xray_networkWaitThenReconnectCycle() {
        var state = VpnSessionCyclePolicy.beginXrayConnect(VpnSessionCyclePolicy.CycleState()).second
        state = walk(state, xray, "CONNECTING", "WAITING_NETWORK", "RECONNECTING", "CONNECTED")
        assertTrue(state.ui.connected)
        assertEquals(VpnTunnelSessionStore.OWNER_XRAY, state.monitorOwner)
    }

    @Test
    fun serviceError_clearsActiveEngineInControllerPolicy() {
        assertTrue(VpnActiveEnginePolicy.shouldClearActiveEngine("ERROR", fromQuery = false))
        val afterError = VpnSessionCyclePolicy.userDisconnect(
            walk(
                VpnSessionCyclePolicy.markActiveEngine(VpnSessionCyclePolicy.CycleState(), xray),
                xray,
                "CONNECTING",
                "ERROR",
            ),
        )
        assertNull(afterError.activeEngine)
    }

    @Test
    fun logoutDisconnect_matchesLifecyclePolicyAcrossCycle() {
        val connected = VpnSessionCyclePolicy.uiFlags(VpnSessionPhase.CONNECTED)
        assertTrue(
            VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
                isVpnConnected = connected.connected,
                isConnectRequested = connected.connectRequested,
                isVpnPaused = connected.paused,
            ),
        )
        val paused = VpnSessionCyclePolicy.uiFlags(VpnSessionPhase.PAUSED)
        assertTrue(
            VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
                isVpnConnected = paused.connected,
                isConnectRequested = paused.connectRequested,
                isVpnPaused = paused.paused,
            ),
        )
        val idle = VpnSessionCyclePolicy.uiFlags(VpnSessionPhase.DISCONNECTED)
        assertFalse(
            VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
                isVpnConnected = idle.connected,
                isConnectRequested = idle.connectRequested,
                isVpnPaused = idle.paused,
            ),
        )
    }

    private fun connected(engine: String): VpnSessionCyclePolicy.CycleState {
        var state = VpnSessionCyclePolicy.markActiveEngine(VpnSessionCyclePolicy.CycleState(), engine)
        return walk(state, engine, "CONNECTING", "CONNECTED")
    }

    private fun walk(
        start: VpnSessionCyclePolicy.CycleState,
        engine: String,
        vararg events: String,
    ): VpnSessionCyclePolicy.CycleState {
        var state = start
        for (event in events) {
            val previous = state.phase
            state = VpnSessionCyclePolicy.applyBroadcast(state, event, engine)
            assertFalse("ignored $event from $previous", state.ignored)
            val next = VpnSessionPhase.fromEventName(event)!!
            assertTrue(
                "illegal $previous → $next ($engine)",
                VpnSessionCyclePolicy.isAllowedTransition(previous, next, engine),
            )
        }
        return state
    }
}
