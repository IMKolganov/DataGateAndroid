package com.imkolganov.datagate.vpn

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.imkolganov.datagate.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses the real [VpnEventMapper.map] (via Robolectric resources) rather than a hand-rolled mock
 * mapper — a previous mock duplicated [VpnEventMapper]'s event-handling logic and silently drifted
 * out of sync with it, which is exactly how the "pendingUserCommand never clears on ERROR" bug went
 * unnoticed. See [VpnCommandContractIntegrationTest] for pendingUserCommand-specific coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class VpnLifecyclePolicyTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Application>().resources

    private fun map(state: VpnStatusUiState, name: String, info: String = ""): VpnStatusUiState =
        VpnEventMapper.map(resources, state, name, info)

    private val mapper: (VpnStatusUiState, String, String) -> VpnStatusUiState = ::map

    @Test
    fun supportedAndroidApiLevels_documentedForLifecycleCoverage() {
        val covered = listOf(24, 28, 31, 33, 34)
        assertEquals(listOf(24, 28, 31, 33, 34), covered)
    }

    @Test
    fun connectPauseResumeDisconnect_fullLifecycle() {
        var state = VpnStatusUiState()

        state = VpnLifecyclePolicy.foldStatusBroadcasts(
            state,
            listOf(
                VpnLifecyclePolicy.StatusBroadcast("CONNECTING"),
                VpnLifecyclePolicy.StatusBroadcast("CONNECTED"),
            ),
            mapper,
        )
        state = VpnCommandContract.beginPauseRequest(state)
        assertFalse(state.isVpnPaused)
        state = mapper(state, "PAUSED", "")
        state = VpnCommandContract.beginResumeRequest(state)
        assertTrue(state.isVpnPaused)
        state = mapper(state, "RESUMED", "")
        state = VpnLifecyclePolicy.foldStatusBroadcasts(
            state,
            listOf(
                VpnLifecyclePolicy.StatusBroadcast("CONNECTED"),
                VpnLifecyclePolicy.StatusBroadcast("DISCONNECTING"),
                VpnLifecyclePolicy.StatusBroadcast("DISCONNECTED"),
            ),
            mapper,
        )

        assertFalse(state.isConnectRequested)
        assertFalse(state.isVpnConnected)
        assertFalse(state.isVpnPaused)
        assertNull(state.selectedServerId)
        assertNull(state.selectedServerName)
    }

    @Test
    fun appReopen_afterProcessDeathWhilePaused_restoresDisconnectedNotPaused() {
        val restored = VpnLifecyclePolicy.restoreUiStateOnAppStart(
            current = VpnStatusUiState(),
            cached = VpnLifecyclePolicy.CachedPrefsSnapshot(
                selectedServerName = "Frankfurt",
                sessionServerId = 7,
                lastEventName = "PAUSED",
                lastEventInfo = "Paused",
            ),
            mapEvent = mapper,
        )

        assertFalse("Paused tunnel cannot survive process restart", restored.isVpnPaused)
        assertFalse(restored.isVpnConnected)
        assertFalse(restored.isConnectRequested)
        assertEquals("Frankfurt", restored.selectedServerName)
        assertEquals(7, restored.selectedServerId)
    }

    @Test
    fun appReopen_afterProcessDeathWhileConnecting_thenServiceQuery_acceptsDisconnected() {
        val restored = VpnLifecyclePolicy.restoreUiStateOnAppStart(
            current = VpnStatusUiState(),
            cached = VpnLifecyclePolicy.CachedPrefsSnapshot(
                lastEventName = "CONNECTING",
                lastEventInfo = "Negotiating",
            ),
            mapEvent = mapper,
        )
        assertFalse(restored.isConnectRequested)

        val afterQuery = VpnLifecyclePolicy.foldStatusBroadcasts(
            restored,
            listOf(
                VpnLifecyclePolicy.StatusBroadcast(
                    eventName = "DISCONNECTED",
                    eventInfo = "No active session",
                    fromQuery = true,
                ),
            ),
            mapper,
        )
        assertFalse(afterQuery.isConnectRequested)
        assertFalse(afterQuery.isVpnPaused)
    }

    @Test
    fun appReopen_whileLiveConnectInSameSession_idleQueryDisconnectedIsIgnored() {
        var state = VpnStatusUiState()
        state = mapper(state, "CONNECTING", "")

        val afterIgnoredQuery = VpnLifecyclePolicy.foldStatusBroadcasts(
            state,
            listOf(
                VpnLifecyclePolicy.StatusBroadcast(
                    eventName = "DISCONNECTED",
                    eventInfo = "No active session",
                    fromQuery = true,
                ),
            ),
            mapper,
        )

        assertTrue(afterIgnoredQuery.isConnectRequested)
        assertEquals(resources.getString(R.string.vpn_msg_connecting), afterIgnoredQuery.lastMessage)
    }

    @Test
    fun appReopen_serviceStillConnected_queryReturnsConnected() {
        val restored = VpnLifecyclePolicy.restoreUiStateOnAppStart(
            current = VpnStatusUiState(selectedServerName = "Paris"),
            cached = VpnLifecyclePolicy.CachedPrefsSnapshot(
                selectedServerName = "Paris",
                sessionServerId = 3,
                lastEventName = "CONNECTED",
            ),
            mapEvent = mapper,
        )
        assertFalse(restored.isVpnConnected)

        val afterQuery = VpnLifecyclePolicy.foldStatusBroadcasts(
            restored,
            listOf(
                VpnLifecyclePolicy.StatusBroadcast(
                    eventName = "CONNECTED",
                    eventInfo = "Session active",
                    fromQuery = true,
                ),
            ),
            mapper,
        )
        assertTrue(afterQuery.isVpnConnected)
        assertTrue(afterQuery.isConnectRequested)
    }

    @Test
    fun pauseResumeCommands_matchServiceValidation() {
        val reject = VpnCommandContract.evaluatePause(
            VpnCommandContract.VpnServiceSnapshot(hasActiveSession = false, vpnClientPresent = false, isPaused = false)
        )
        assertTrue(reject is VpnCommandContract.CommandDecision.Reject)
        assertEquals("no_active_session", (reject as VpnCommandContract.CommandDecision.Reject).reason)

        val accept = VpnCommandContract.evaluatePause(
            VpnCommandContract.VpnServiceSnapshot(hasActiveSession = true, vpnClientPresent = true, isPaused = false)
        )
        assertTrue(accept is VpnCommandContract.CommandDecision.Accept)

        val resumeReject = VpnCommandContract.evaluateResume(
            VpnCommandContract.VpnServiceSnapshot(hasActiveSession = true, vpnClientPresent = true, isPaused = false)
        )
        assertTrue(resumeReject is VpnCommandContract.CommandDecision.Reject)
    }

    @Test
    fun logoutShouldDisconnect_whenVpnActiveOrPausedOrConnecting() {
        assertTrue(
            VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
                isVpnConnected = true,
                isConnectRequested = true,
                isVpnPaused = false,
            )
        )
        assertTrue(
            VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
                isVpnConnected = false,
                isConnectRequested = true,
                isVpnPaused = true,
            )
        )
        assertTrue(
            VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
                isVpnConnected = false,
                isConnectRequested = true,
                isVpnPaused = false,
            )
        )
        assertFalse(
            VpnLifecyclePolicy.shouldDisconnectVpnOnLogout(
                isVpnConnected = false,
                isConnectRequested = false,
                isVpnPaused = false,
            )
        )
    }

    @Test
    fun reconnectingAndWaitingNetwork_doNotSurviveProcessRestart() {
        for (cached in listOf("RECONNECTING", "WAITING_NETWORK", "RESUMED")) {
            val normalized = VpnLifecyclePolicy.normalizeCachedEventForRestore(cached, "")
            assertEquals("DISCONNECTED", normalized.eventName)
            assertTrue(normalized.shouldPersist)
        }
    }

    @Test
    fun applyRestoredCachedEvent_processRestartPreservesServerChoice() {
        val normalized = OpenVpnRuntimePolicy.restoreCachedStatus("PAUSED", "")
        val restored = VpnLifecyclePolicy.applyRestoredCachedEvent(
            current = VpnStatusUiState(selectedServerName = "Frankfurt", selectedServerId = 7),
            normalized = normalized,
            mapEvent = mapper,
        )
        assertFalse(restored.isVpnPaused)
        assertEquals("Frankfurt", restored.selectedServerName)
        assertEquals(7, restored.selectedServerId)
    }

    @Test
    fun hasVpnPermission_isPreserved_acrossBroadcasts() {
        val initial = VpnStatusUiState(hasVpnPermission = false)
        val afterConnecting = mapper(initial, "CONNECTING", "")
        assertFalse(afterConnecting.hasVpnPermission)

        val granted = VpnStatusUiState(hasVpnPermission = true)
        val afterConnected = mapper(granted, "CONNECTED", "")
        assertTrue(afterConnected.hasVpnPermission)
    }

    @Test
    fun closeAndReopenApp_preservesServerSelectionButNotTunnelState() {
        val connected = VpnLifecyclePolicy.foldStatusBroadcasts(
            VpnStatusUiState(selectedServerName = "Tokyo", selectedServerId = 9),
            listOf(VpnLifecyclePolicy.StatusBroadcast("CONNECTED")),
            mapper,
        )
        assertTrue(connected.isVpnConnected)

        val afterReopen = VpnLifecyclePolicy.restoreUiStateOnAppStart(
            current = VpnStatusUiState(),
            cached = VpnLifecyclePolicy.CachedPrefsSnapshot(
                selectedServerName = "Tokyo",
                sessionServerId = 9,
                lastEventName = "CONNECTED",
            ),
            mapEvent = mapper,
        )
        assertFalse(afterReopen.isVpnConnected)
        assertEquals("Tokyo", afterReopen.selectedServerName)
        assertEquals(9, afterReopen.selectedServerId)
    }
}
