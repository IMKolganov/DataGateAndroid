package com.imkolganov.datagate.vpn

data class VpnStatusUiState(
    val isConnectRequested: Boolean = false,
    val isVpnConnected: Boolean = false,
    val isVpnPaused: Boolean = false,
    /** Set on button tap; cleared only by authoritative service broadcast or rejection. */
    val pendingUserCommand: VpnCommandContract.PendingUserCommand? = null,
    val selectedServerName: String? = null,
    val lastMessage: String = "",
    val selectedServerId: Int? = null,
    val hasVpnPermission: Boolean = false,
    /** RECONNECTING cycles since this user connection started (reset on disconnect). */
    val reconnectCount: Int = 0,
    /** True between RECONNECTING and the next CONNECTED/PAUSED (dedupes spam). */
    val reconnectCycleActive: Boolean = false,
)