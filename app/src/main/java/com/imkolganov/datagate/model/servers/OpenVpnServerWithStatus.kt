package com.imkolganov.datagate.model.servers

data class OpenVpnServerWithStatus(
    val openVpnServerResponses: OpenVpnServerResponses?,
    val openVpnServerStatusLogResponse: OpenVpnServerStatusLogResponse?,
    val countConnectedClients: Int?,
    val countSessions: Int?,
    val totalBytesIn: Long?,
    val totalBytesOut: Long?
)