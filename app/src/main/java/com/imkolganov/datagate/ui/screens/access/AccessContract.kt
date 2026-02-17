package com.imkolganov.datagate.ui.screens.access

interface AccessContract {

    data class UiState(
        val isLoading: Boolean = false,
        val errorText: String? = null,

        val servers: List<ServerItem> = emptyList(),
        val activeConnections: List<ActiveConnectionItem> = emptyList(),

        val selectedServerId: Int? = null
    )

    data class ServerItem(
        val id: Int,
        val name: String,
        val protocol: String?,
        val isOnline: Boolean,

        val uptimeText: String?,
        val openVpnVersionText: String?,
        val totalInText: String?,
        val totalOutText: String?,

        val subtitle: String? = null,
        val loadPercent: Int? = null,
        val activeUsers: Int? = null
    )

    data class ActiveConnectionItem(
        val id: String,
        val serverId: Int,
        val serverTitle: String,
        val connectedSinceText: String?,
        val virtualIpText: String?
    )

    sealed interface UiEvent {
        data object Refresh : UiEvent
        data class SelectServer(val serverId: Int) : UiEvent
        data class ConnectToServer(val serverId: Int) : UiEvent
        data object Disconnect : UiEvent
        data object ClearError : UiEvent
    }
}
