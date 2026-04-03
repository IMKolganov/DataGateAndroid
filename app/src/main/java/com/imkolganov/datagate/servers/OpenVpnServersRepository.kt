package com.imkolganov.datagate.servers

import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.model.servers.OpenVpnServerWithStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenVpnServersRepository(
    private val api: OpenVpnServersApi,
) {
    suspend fun getAllWithStatus(): List<OpenVpnServerWithStatus> {
        val response = withContext(Dispatchers.IO) {
            api.getAllWithStatus()
        }

        if (!response.success) {
            throw IllegalStateException(response.message ?: "Request failed")
        }

        return response.data?.openVpnServerWithStatuses ?: emptyList()
    }

    suspend fun pickBestServer(): BestServerResult {
        val items = getAllWithStatus()

        val candidates = items.mapNotNull { item ->
            val server = item.openVpnServerResponses?.openVpnServer ?: return@mapNotNull null

            val id = server.id ?: return@mapNotNull null
            if (server.isOnline != true) return@mapNotNull null
            if (server.isEnableWss != true) return@mapNotNull null


            BestServerResult(
                serverId = id,
                name = server.serverName?.trim().takeUnless { it.isNullOrBlank() } ?: "Server #$id",
                apiUrl = server.apiUrl,
                countConnectedClients = (item.countConnectedClients ?: 0).coerceAtLeast(0),
                isDefault = false
            )
        }

        return candidates
            .minByOrNull { it.countConnectedClients }
            ?: throw IllegalStateException("No online WSS servers available")
    }

    /**
     * Resolves a specific server by id (must be online and WSS-enabled), or throws.
     */
    suspend fun getServerByIdOrThrow(serverId: Int): BestServerResult {
        return when (val r = resolveManualConnection(serverId)) {
            is ManualServerResolve.Ok -> r.result
            is ManualServerResolve.NotAvailable ->
                throw IllegalStateException("Server #$serverId is not available or offline")
            is ManualServerResolve.RequiresExternalOpenVpn ->
                throw IllegalStateException("Server #$serverId does not support WSS in app")
        }
    }

    /**
     * Looks up a server for manual (Access tab) connect: distinguishes offline/missing vs online but non-WSS.
     */
    suspend fun resolveManualConnection(serverId: Int): ManualServerResolve {
        val items = getAllWithStatus()

        for (item in items) {
            val server = item.openVpnServerResponses?.openVpnServer ?: continue
            val id = server.id ?: continue
            if (id != serverId) continue
            if (server.isOnline != true) return ManualServerResolve.NotAvailable
            val name = server.serverName?.trim().takeUnless { it.isNullOrBlank() } ?: "Server #$id"
            if (server.isEnableWss != true) {
                return ManualServerResolve.RequiresExternalOpenVpn(name)
            }
            return ManualServerResolve.Ok(
                BestServerResult(
                    serverId = id,
                    name = name,
                    apiUrl = server.apiUrl,
                    countConnectedClients = (item.countConnectedClients ?: 0).coerceAtLeast(0),
                    isDefault = false
                )
            )
        }
        return ManualServerResolve.NotAvailable
    }
}
