package com.imkolganov.datagate.model.servers

/**
 * One row from [GET api/v2/open-vpn-servers/get-all-with-status] —
 * [OpenVpnServerV2Dto] plus optional live metrics (same idea as legacy v1 with-status).
 */
data class OpenVpnServerWithStatusV2Item(
    val server: OpenVpnServerV2Dto,
    val openVpnServerStatusLogResponse: OpenVpnServerStatusLogResponse?,
    val countConnectedClients: Int?,
    val countSessions: Int?,
    val totalBytesIn: Long?,
    val totalBytesOut: Long?
)
