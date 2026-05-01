package com.imkolganov.datagate.model.servers

/**
 * Matches backend [OpenVpnServerV2Dto] (GET api/v2/open-vpn-servers).
 */
data class OpenVpnServerV2Dto(
    val id: Int,
    val serverName: String,
    val isOnline: Boolean,
    val isDefault: Boolean,
    val apiUrl: String,
    val latitude: Double?,
    val longitude: Double?,
    val isEnableWss: Boolean,
    val createDate: String?,
    val lastUpdate: String?,
    val isDeleted: Boolean,
    val dcoIsEnabled: Boolean?,
    val tags: List<String>,
    val quotaPlanGroups: List<QuotaPlanGroupDto>,
    val isAccessibleForUserQuotaPlan: Boolean
)
