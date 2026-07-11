package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration: [VpnEventMapper] + [VpnCommandContract] + [VpnLifecyclePolicy] together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VpnCommandContractIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun map(state: VpnStatusUiState, name: String, info: String = ""): VpnStatusUiState =
        VpnEventMapper.map(context.resources, state, name, info)

    @Test
    fun pendingPause_ignoresConnectingSpam_untilAuthoritativeEvent() {
        val pending = VpnCommandContract.beginPauseRequest(
            VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)
        )
        val afterWait = map(pending, "WAIT", "")
        assertEquals(pending, afterWait)
        assertFalse(afterWait.isVpnPaused)

        val afterPaused = map(pending, "PAUSED", "")
        assertTrue(afterPaused.isVpnPaused)
        assertNull(afterPaused.pendingUserCommand)
    }

    @Test
    fun pauseRejectedViaMapper_doesNotChangeTunnelFlags() {
        val connected = VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)
        val pending = VpnCommandContract.beginPauseRequest(connected)
        // Rejection handled in VpnController before mapper; mapper should not see PAUSE_REJECTED.
        // If it does, event is authoritative but we test rollback path separately in contract test.
        assertFalse(map(pending, "CONNECTING", "").isVpnPaused)
    }

    /**
     * Regression: a pause/resume tap left [VpnStatusUiState.pendingUserCommand] stuck forever if the
     * tunnel errored out (or was torn down) instead of confirming PAUSED/RESUMED — the UI would show
     * "Pausing…"/"Resuming…" indefinitely and [VpnCommandContract.canRequestPauseFromUi] /
     * [VpnCommandContract.canRequestResumeFromUi] would refuse further taps until the next
     * CONNECTED/PAUSED/RESUMED/DISCONNECTED broadcast.
     */
    @Test
    fun pendingPause_clearedWhenTunnelErrorsOutInstead() {
        val pending = VpnCommandContract.beginPauseRequest(
            VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)
        )
        val afterError = map(pending, "ERROR", "tunnel setup failed")
        assertNull("pendingUserCommand must clear on ERROR, not stay stuck", afterError.pendingUserCommand)
        assertTrue(VpnCommandContract.canRequestPauseFromUi(afterError.copy(isVpnConnected = true)))
    }

    @Test
    fun pendingPause_clearedWhenTunSetupFailedInstead() {
        val pending = VpnCommandContract.beginPauseRequest(
            VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)
        )
        val afterFailure = map(pending, "TUN_SETUP_FAILED", "")
        assertNull("pendingUserCommand must clear on TUN_SETUP_FAILED, not stay stuck", afterFailure.pendingUserCommand)
    }

    @Test
    fun pendingResume_clearedWhenDisconnectingInstead() {
        val pending = VpnCommandContract.beginResumeRequest(
            VpnStatusUiState(isConnectRequested = true, isVpnPaused = true)
        )
        val afterDisconnecting = map(pending, "DISCONNECTING", "")
        assertNull("pendingUserCommand must clear on DISCONNECTING, not stay stuck", afterDisconnecting.pendingUserCommand)
    }
}
