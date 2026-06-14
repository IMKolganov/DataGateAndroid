package com.imkolganov.datagate.ui.screens.access

object AccessSessionNetworkInfo {

    fun resolveExternalIp(
        sessionServerId: Int?,
        servers: List<AccessContract.ServerItem>
    ): String? {
        if (sessionServerId == null) return null
        return servers.find { it.id == sessionServerId }
            ?.serverRemoteIp
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}
