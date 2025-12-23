package com.imkolganov.datagate.model.servers

data class OpenVpnServer(
    val id: Int?,
    val serverName: String?,
    val isOnline: Boolean?,
    val isDefault: Boolean?,
    val apiUrl: String?,
    val latitude: Double?,
    val longitude: Double?,
    val isEnableWss: Boolean?,
    val createDate: String?,
    val lastUpdate: String?
)
