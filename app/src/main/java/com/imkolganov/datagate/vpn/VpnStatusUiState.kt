package com.imkolganov.datagate.vpn

data class VpnStatusUiState(
    val isConnectRequested: Boolean = false,
    val selectedServerName: String? = null,
    val lastMessage: String = "",
    val selectedServerId: Int? = null
)