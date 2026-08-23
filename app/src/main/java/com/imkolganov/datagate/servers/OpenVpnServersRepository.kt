package com.imkolganov.datagate.servers

import com.imkolganov.datagate.model.servers.OpenVpnServerWithStatusV2Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenVpnServersRepository(
    private val api: OpenVpnServersApi,
) {
    suspend fun listServersWithStatus(): List<OpenVpnServerWithStatusV2Item> =
        withContext(Dispatchers.IO) {
            val response = api.getOpenVpnServersWithStatusV3()

            if (!response.success) {
                throw IllegalStateException(response.message ?: "Request failed")
            }

            response.data?.openVpnServerWithStatuses ?: emptyList()
        }

    suspend fun pickBestServer(): BestServerResult {
        return VpnServerConnectPolicy.pickBestServer(listServersWithStatus())
    }

    /**
     * Resolves a specific server by id (must be online OpenVPN), or throws.
     */
    suspend fun getServerByIdOrThrow(serverId: Int): BestServerResult {
        return when (val r = resolveManualConnection(serverId)) {
            is ManualServerResolve.Ok -> r.result
            is ManualServerResolve.NotAvailable ->
                throw IllegalStateException("Server #$serverId is not available or offline")
            is ManualServerResolve.RequiresUnsupportedServerType ->
                throw IllegalStateException("Server #$serverId has an unsupported type for this app")
            is ManualServerResolve.QuotaPlanBlocked ->
                throw IllegalStateException("Server #$serverId is not included in your quota plan")
        }
    }

    /**
     * Looks up a server for manual (Access tab) connect: distinguishes offline/missing vs type vs quota.
     */
    suspend fun resolveManualConnection(serverId: Int): ManualServerResolve {
        return VpnServerConnectPolicy.resolveManualConnection(listServersWithStatus(), serverId)
    }
}
