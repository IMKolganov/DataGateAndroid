package com.imkolganov.datagate.model.servers

data class GetAllWithStatusData(
    val openVpnServerWithStatuses: List<OpenVpnServerWithStatus>
)