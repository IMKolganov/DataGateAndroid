package com.imkolganov.datagate.model.servers

data class OpenVpnServersWithStatusV2Data(
    val openVpnServerWithStatuses: List<OpenVpnServerWithStatusV2Item>,
    val userQuotaPlan: UserQuotaPlanContextDto? = null,
)
