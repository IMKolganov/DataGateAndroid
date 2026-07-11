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
    val hasVpnPermission: Boolean = false
)