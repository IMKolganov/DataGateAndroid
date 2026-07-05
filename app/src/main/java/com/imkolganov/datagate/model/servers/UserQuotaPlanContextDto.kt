package com.imkolganov.datagate.model.servers

/**
 * Caller quota context from [GET api/v3/open-vpn-servers/get-all-with-status].
 */
data class UserQuotaPlanContextDto(
    val isPrivileged: Boolean = false,
    val userQuotaPlanId: Int? = null,
    val quotaPlanId: Int? = null,
    val quotaPlanName: String? = null,
    val allowedVpnServerIds: List<Int>? = null,
)
