package com.imkolganov.datagate.ui.screens.access

import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.servers.OpenVpnServersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccessRepositoryImpl(
    private val serversRepository: OpenVpnServersRepository,
    private val tokenStore: TokenStore
) : AccessRepository {

    override suspend fun getServers(): List<AccessContract.ServerItem> = withContext(Dispatchers.IO) {
        val items = serversRepository.getAllWithStatus()

        items.mapNotNull { item ->
            val server = item.openVpnServerResponses?.openVpnServer ?: return@mapNotNull null
            val status = item.openVpnServerStatusLogResponse;
            val totalStatus = item;

            AccessContract.ServerItem(
                id = server.id ?: return@mapNotNull null,
                name = server.serverName ?: "OpenVPN Server",
                protocol = "unknow",
                isOnline = server.isOnline == true,

                uptimeText = status?.upSince,
                openVpnVersionText = status?.version,
                totalInText = totalStatus.totalBytesIn.toString(),
                totalOutText = totalStatus.totalBytesOut.toString(),

                subtitle = null,
                loadPercent = 0,
                activeUsers = totalStatus.countConnectedClients
            )
        }
    }

    override suspend fun getMyActiveConnections(): List<AccessContract.ActiveConnectionItem> = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken() ?: return@withContext emptyList()

        // TODO: call your backend endpoint here. Example (replace with your real API client):
        // val response = someApi.getMyActiveConnections(token)
        // return@withContext response.data.map { ... }

        emptyList()
    }
}
