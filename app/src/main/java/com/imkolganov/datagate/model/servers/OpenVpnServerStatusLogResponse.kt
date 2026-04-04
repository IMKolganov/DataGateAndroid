package com.imkolganov.datagate.model.servers

/** Matches backend status log DTO nested under with-status responses. */
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
