package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests: UI must not claim pause/resume until the service confirms.
 * These tests encode the architecture — if someone reintroduces optimistic
 * isVpnPaused without a broadcast, add a test here and make it fail.
 */
class VpnCommandContractTest {

    private val connected = VpnStatusUiState(
        isConnectRequested = true,
        isVpnConnected = true,
        selectedServerName = "Frankfurt",
        selectedServerId = 3,
    )

    private val paused = connected.copy(
        isVpnConnected = false,
        isVpnPaused = true,
    )

    @Test
    fun evaluatePause_rejectsWhenNoActiveSessionAndNoClient() {
        val decision = VpnCommandContract.evaluatePause(
            VpnCommandContract.VpnServiceSnapshot(
                hasActiveSession = false,
                vpnClientPresent = false,
                isPaused = false,
            )
        )
        assertTrue(decision is VpnCommandContract.CommandDecision.Reject)
        assertEquals("no_active_session", (decision as VpnCommandContract.CommandDecision.Reject).reason)
    }

    @Test
    fun evaluatePause_acceptsWhenSessionOrClientPresent() {
        val withSession = VpnCommandContract.evaluatePause(
            VpnCommandContract.VpnServiceSnapshot(hasActiveSession = true, vpnClientPresent = false, isPaused = false)
        )
        assertTrue(withSession is VpnCommandContract.CommandDecision.Accept)

        val withClient = VpnCommandContract.evaluatePause(
            VpnCommandContract.VpnServiceSnapshot(hasActiveSession = false, vpnClientPresent = true, isPaused = false)
        )
        assertTrue(withClient is VpnCommandContract.CommandDecision.Accept)
    }

    @Test
    fun evaluateResume_rejectsWhenNotPaused() {
        val decision = VpnCommandContract.evaluateResume(
            VpnCommandContract.VpnServiceSnapshot(hasActiveSession = true, vpnClientPresent = true, isPaused = false)
        )
        assertTrue(decision is VpnCommandContract.CommandDecision.Reject)
        assertEquals("not_paused", (decision as VpnCommandContract.CommandDecision.Reject).reason)
    }

    @Test
    fun beginPauseRequest_doesNotSetIsVpnPaused_usesPendingOnly() {
        val pending = VpnCommandContract.beginPauseRequest(connected)
        assertEquals(VpnCommandContract.PendingUserCommand.PAUSE, pending.pendingUserCommand)
        assertTrue(pending.isVpnConnected)
        assertFalse(pending.isVpnPaused)
    }

    @Test
    fun beginResumeRequest_doesNotClearPauseUntilBroadcast() {
        val pending = VpnCommandContract.beginResumeRequest(paused)
        assertEquals(VpnCommandContract.PendingUserCommand.RESUME, pending.pendingUserCommand)
        assertTrue(pending.isVpnPaused)
        assertFalse(pending.isVpnConnected)
    }

    @Test
    fun pauseRejected_rollsBackToAuthoritativeConnectedState() {
        val pending = VpnCommandContract.beginPauseRequest(connected)
        val rolledBack = VpnCommandContract.applyCommandRejected(
            rollback = connected,
            command = VpnCommandContract.PendingUserCommand.PAUSE,
            reason = "no_active_session",
        )
        assertNull(rolledBack.pendingUserCommand)
        assertTrue(rolledBack.isVpnConnected)
        assertFalse(rolledBack.isVpnPaused)
        assertTrue(rolledBack.lastMessage.contains("no_active_session"))
        assertEquals(connected.selectedServerName, rolledBack.selectedServerName)
        // Pending state must not have lied about pause
        assertFalse(pending.isVpnPaused)
    }

    @Test
    fun fullPauseContract_userTapThenServiceRejects_uiStaysConnected() {
        var ui = connected
        val rollback = ui
        ui = VpnCommandContract.beginPauseRequest(ui)
        assertFalse(ui.isVpnPaused)

        val decision = VpnCommandContract.evaluatePause(
            VpnCommandContract.VpnServiceSnapshot(false, false, false)
        )
        assertTrue(decision is VpnCommandContract.CommandDecision.Reject)

        ui = VpnCommandContract.applyCommandRejected(
            rollback,
            VpnCommandContract.PendingUserCommand.PAUSE,
            (decision as VpnCommandContract.CommandDecision.Reject).reason,
        )
        assertTrue(ui.isVpnConnected)
        assertFalse(ui.isVpnPaused)
        assertNull(ui.pendingUserCommand)
    }

    /**
     * Documents the gap that let pause ship broken: UI contract tests passed because we
     * simulated a PAUSED broadcast, but never verified the tunnel actually stopped (2ip.ru).
     * See [OpenVpnNativePauseResumeSchedulingTest].
     */
    @Test
    fun pauseContract_pausedBroadcastAlone_doesNotProveTunnelStopped() {
        val afterBroadcastOnly = VpnCommandContract.beginPauseRequest(connected).copy(
            isVpnConnected = false,
            isVpnPaused = true,
            pendingUserCommand = null,
        )
        assertTrue("UI can show Paused after PAUSED broadcast", afterBroadcastOnly.isVpnPaused)
        assertTrue(TunnelPauseEvidence.uiClaimsPaused(afterBroadcastOnly))
        assertFalse(
            "PAUSED broadcast from OpenVpn3Service does NOT imply native pause ran — " +
                "tunnel may still route traffic (2ip.ru still VPN IP).",
            TunnelPauseEvidence.tunnelStopped(nativePauseExecuted = false, ui = afterBroadcastOnly),
        )
    }

    @Test
    fun pendingBlocksDuplicatePauseRequest() {
        val pending = VpnCommandContract.beginPauseRequest(connected)
        assertFalse(VpnCommandContract.canRequestPauseFromUi(pending))
    }

    @Test
    fun nonAuthoritativeEvents_ignoredWhilePending() {
        val pending = VpnCommandContract.beginPauseRequest(connected)
        assertFalse(VpnCommandContract.isAuthoritativeTunnelEvent("WAIT"))
        assertFalse(VpnCommandContract.isAuthoritativeTunnelEvent("CONNECTING"))
        assertTrue(VpnCommandContract.isAuthoritativeTunnelEvent("PAUSED"))
        assertTrue(VpnCommandContract.isAuthoritativeTunnelEvent("PAUSE_REJECTED"))
        assertTrue(pending.pendingUserCommand != null)
    }

    @Test
    fun parseRejectedCommand_mapsServiceBroadcastNames() {
        assertEquals(VpnCommandContract.PendingUserCommand.PAUSE, VpnCommandContract.parseRejectedCommand("PAUSE_REJECTED"))
        assertEquals(VpnCommandContract.PendingUserCommand.RESUME, VpnCommandContract.parseRejectedCommand("RESUME_REJECTED"))
        assertNull(VpnCommandContract.parseRejectedCommand("PAUSED"))
    }
}
