package com.imkolganov.datagate.vpn

/**
 * Coordinates VPN UI state across app reopen, service queries, and user commands.
 * Pure logic — covered by [VpnLifecyclePolicyTest].
 */
object VpnLifecyclePolicy {

    data class CachedPrefsSnapshot(
        val selectedServerName: String? = null,
        val sessionServerId: Int? = null,
        val lastEventName: String? = null,
        val lastEventInfo: String? = null,
    )

    data class StatusBroadcast(
        val eventName: String,
        val eventInfo: String = "",
        val fromQuery: Boolean = false,
    )

    fun normalizeCachedEventForRestore(
        cachedName: String?,
        cachedInfo: String?,
    ): CachedStatusRestoreResult = OpenVpnRuntimePolicy.restoreCachedStatus(cachedName, cachedInfo)

    fun shouldAcceptStatusBroadcast(
        fromQuery: Boolean,
        eventName: String,
        current: VpnStatusUiState,
    ): Boolean = !OpenVpnRuntimePolicy.shouldIgnoreIdleQueryDisconnected(
        fromQuery = fromQuery,
        eventName = eventName,
        isConnectRequested = current.isConnectRequested,
        isVpnConnected = current.isVpnConnected,
    )

    fun restoreUiStateOnAppStart(
        current: VpnStatusUiState,
        cached: CachedPrefsSnapshot,
        mapEvent: (VpnStatusUiState, String, String) -> VpnStatusUiState,
    ): VpnStatusUiState {
        var next = current
        if (!cached.selectedServerName.isNullOrBlank() && next.selectedServerName.isNullOrBlank()) {
            next = next.copy(selectedServerName = cached.selectedServerName)
        }
        val sessionId = cached.sessionServerId
        if (sessionId != null && sessionId >= 0 && next.selectedServerId == null) {
            next = next.copy(selectedServerId = sessionId)
        }
        val normalized = normalizeCachedEventForRestore(cached.lastEventName, cached.lastEventInfo)
        val eventName = normalized.eventName
        if (!eventName.isNullOrBlank()) {
            next = applyRestoredCachedEvent(next, normalized, mapEvent)
        }
        return next
    }

    /**
     * After process death the tunnel is gone, but the last server choice should remain
     * so the user can reconnect quickly. Only explicit user disconnect clears it.
     */
    fun applyRestoredCachedEvent(
        current: VpnStatusUiState,
        normalized: CachedStatusRestoreResult,
        mapEvent: (VpnStatusUiState, String, String) -> VpnStatusUiState,
    ): VpnStatusUiState {
        val eventName = normalized.eventName ?: return current
        if (normalized.shouldPersist && eventName.equals("DISCONNECTED", ignoreCase = true)) {
            return current.copy(
                isConnectRequested = false,
                isVpnConnected = false,
                isVpnPaused = false,
                lastMessage = normalized.eventInfo?.takeIf { it.isNotBlank() }
                    ?: current.lastMessage,
            )
        }
        return mapEvent(current, eventName, normalized.eventInfo ?: "")
    }

    fun applyStatusBroadcast(
        current: VpnStatusUiState,
        broadcast: StatusBroadcast,
        mapEvent: (VpnStatusUiState, String, String) -> VpnStatusUiState,
    ): VpnStatusUiState? {
        if (!shouldAcceptStatusBroadcast(broadcast.fromQuery, broadcast.eventName, current)) {
            return null
        }
        return mapEvent(current, broadcast.eventName, broadcast.eventInfo)
    }

    fun foldStatusBroadcasts(
        initial: VpnStatusUiState,
        broadcasts: List<StatusBroadcast>,
        mapEvent: (VpnStatusUiState, String, String) -> VpnStatusUiState,
    ): VpnStatusUiState {
        var state = initial
        for (broadcast in broadcasts) {
            state = applyStatusBroadcast(state, broadcast, mapEvent) ?: state
        }
        return state
    }

    fun shouldDisconnectVpnOnLogout(
        isVpnConnected: Boolean,
        isConnectRequested: Boolean,
        isVpnPaused: Boolean,
        pendingUserCommand: VpnCommandContract.PendingUserCommand? = null,
    ): Boolean = isVpnConnected || isConnectRequested || isVpnPaused || pendingUserCommand != null
}
