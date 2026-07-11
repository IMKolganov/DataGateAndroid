package com.imkolganov.datagate.vpn

/**
 * UI ↔ service contract for user-initiated VPN commands (pause, resume).
 *
 * Architecture rules:
 * 1. [VpnStatusUiState.isVpnPaused] / [VpnStatusUiState.isVpnConnected] change only on
 *    authoritative service broadcasts (PAUSED, RESUMED, CONNECTED, DISCONNECTED, …).
 * 2. Button taps set [VpnStatusUiState.pendingUserCommand] and keep the last authoritative
 *    snapshot for rollback when the service rejects the command.
 * 3. The service must broadcast *_REJECTED — never silently drop while UI shows pending.
 * 4. [PAUSED]/[RESUMED] UI broadcasts come from core PAUSE/RESUME events only
 *    ([OpenVpnPauseBroadcastPolicy]) — never synchronously from the command handler.
 */
object VpnCommandContract {

    enum class PendingUserCommand {
        PAUSE,
        RESUME,
    }

    data class VpnServiceSnapshot(
        val runtimeState: String,
        val hasActiveSession: Boolean,
        val vpnClientPresent: Boolean,
        val isPaused: Boolean,
    )

    sealed interface CommandDecision {
        data object Accept : CommandDecision
        data class Reject(val reason: String) : CommandDecision
    }

    fun evaluatePause(snapshot: VpnServiceSnapshot): CommandDecision {
        if (!snapshot.hasActiveSession && !snapshot.vpnClientPresent) {
            return CommandDecision.Reject("no_active_session")
        }
        if (snapshot.isPaused) {
            return CommandDecision.Reject("already_paused")
        }
        return CommandDecision.Accept
    }

    fun evaluateResume(snapshot: VpnServiceSnapshot): CommandDecision {
        if (!snapshot.isPaused) {
            return CommandDecision.Reject("not_paused")
        }
        return CommandDecision.Accept
    }

    fun canRequestPauseFromUi(state: VpnStatusUiState): Boolean =
        state.isVpnConnected &&
            !state.isVpnPaused &&
            state.pendingUserCommand == null

    fun canRequestResumeFromUi(state: VpnStatusUiState): Boolean =
        state.isVpnPaused &&
            state.pendingUserCommand == null

    /** User tapped Pause — pending only, tunnel flags unchanged until PAUSED broadcast. */
    fun beginPauseRequest(authoritative: VpnStatusUiState): VpnStatusUiState =
        authoritative.copy(
            pendingUserCommand = PendingUserCommand.PAUSE,
            lastMessage = "",
        )

    fun beginResumeRequest(authoritative: VpnStatusUiState): VpnStatusUiState =
        authoritative.copy(
            pendingUserCommand = PendingUserCommand.RESUME,
            lastMessage = "",
        )

    fun clearPending(state: VpnStatusUiState): VpnStatusUiState =
        state.copy(pendingUserCommand = null)

    fun applyCommandRejected(
        rollback: VpnStatusUiState,
        command: PendingUserCommand,
        reason: String,
    ): VpnStatusUiState = rollback.copy(
        pendingUserCommand = null,
        lastMessage = rejectionMessage(command, reason),
    )

    fun rejectionMessage(command: PendingUserCommand, reason: String): String =
        when (command) {
            PendingUserCommand.PAUSE -> "PAUSE_REJECTED:$reason"
            PendingUserCommand.RESUME -> "RESUME_REJECTED:$reason"
        }

    fun parseRejectedCommand(eventName: String): PendingUserCommand? = when (eventName.uppercase()) {
        "PAUSE_REJECTED" -> PendingUserCommand.PAUSE
        "RESUME_REJECTED" -> PendingUserCommand.RESUME
        else -> null
    }

    fun isAuthoritativeTunnelEvent(eventName: String): Boolean = when (eventName.uppercase()) {
        "PAUSED",
        "RESUMED",
        "CONNECTED",
        "DISCONNECTED",
        "DISCONNECTING",
        "ERROR",
        "TUN_SETUP_FAILED",
        "PAUSE_REJECTED",
        "RESUME_REJECTED" -> true
        else -> false
    }
}
