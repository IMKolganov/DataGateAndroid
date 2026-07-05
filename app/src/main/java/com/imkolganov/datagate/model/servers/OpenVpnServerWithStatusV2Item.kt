package com.imkolganov.datagate.model.servers

/**
 * One row from [GET api/v3/open-vpn-servers/get-all-with-status] (v2 item shape) —
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
