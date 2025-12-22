package com.imkolganov.datagate.model.servers

data class OpenVpnServerStatusLogResponse(
    val vpnServerId: Int?,
    val sessionId: String?,
    val upSince: String?,
    val serverLocalIp: String?,
    val serverRemoteIp: String?,
    val bytesIn: Long?,
    val bytesOut: Long?,
    val version: String?
)