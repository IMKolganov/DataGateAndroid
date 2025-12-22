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

            val name = server.serverName?.trim().orEmpty()

            BestServerResult(
                serverId = id,
                name = name.ifBlank { "Server #$id" },
                countConnectedClients = (item.countConnectedClients ?: 0).coerceAtLeast(0),
                isDefault = false
            )
        }

        if (candidates.isEmpty()) {
            throw IllegalStateException("No online servers available")
        }

        return candidates
            .sortedWith(compareBy<BestServerResult> { it.countConnectedClients }.thenBy { it.serverId })
            .first()
    }
}
