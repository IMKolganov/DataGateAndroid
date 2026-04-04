package com.imkolganov.datagate.servers

import com.imkolganov.datagate.TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS
import com.imkolganov.datagate.model.servers.OpenVpnServerWithStatusV2Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenVpnServersRepository(
    private val api: OpenVpnServersApi,
) {
    suspend fun listServersWithStatus(): List<OpenVpnServerWithStatusV2Item> {
        val response = withContext(Dispatchers.IO) {
            api.getOpenVpnServersWithStatusV2()
        }

        if (!response.success) {
            throw IllegalStateException(response.message ?: "Request failed")
        }

        return response.data?.openVpnServerWithStatuses ?: emptyList()
    }

    suspend fun pickBestServer(): BestServerResult {
        val items = listServersWithStatus()

        val candidates = items.mapNotNull { row ->
            val s = row.server
            if (s.isDeleted) return@mapNotNull null
            if (!TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS && !s.isAccessibleForUserQuotaPlan) return@mapNotNull null
            if (!s.isOnline) return@mapNotNull null
            if (!s.isEnableWss) return@mapNotNull null

            BestServerResult(
                serverId = s.id,
                name = s.serverName.trim().takeUnless { it.isBlank() } ?: "Server #${s.id}",
                apiUrl = s.apiUrl.takeUnless { it.isBlank() },
                countConnectedClients = (row.countConnectedClients ?: 0).coerceAtLeast(0),
                isDefault = s.isDefault
            )
        }

        if (candidates.isEmpty()) {
            throw IllegalStateException("No online WSS servers available")
        }
        return candidates.minWith(
            compareBy<BestServerResult> { it.countConnectedClients }
                .thenBy { it.serverId }
        )
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
            is ManualServerResolve.QuotaPlanBlocked ->
                throw IllegalStateException("Server #$serverId is not included in your quota plan")
        }
    }

    /**
     * Looks up a server for manual (Access tab) connect: distinguishes offline/missing vs online but non-WSS vs quota.
     */
    suspend fun resolveManualConnection(serverId: Int): ManualServerResolve {
        val items = listServersWithStatus()

        for (row in items) {
            val s = row.server
            if (s.id != serverId) continue
            if (s.isDeleted) return ManualServerResolve.NotAvailable
            if (!s.isOnline) return ManualServerResolve.NotAvailable
            val name = s.serverName.trim().takeUnless { it.isBlank() } ?: "Server #${s.id}"
            if (!TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS && !s.isAccessibleForUserQuotaPlan) {
                return ManualServerResolve.QuotaPlanBlocked(name)
            }
            if (!s.isEnableWss) {
                return ManualServerResolve.RequiresExternalOpenVpn(name)
            }
            return ManualServerResolve.Ok(
                BestServerResult(
                    serverId = s.id,
                    name = name,
                    apiUrl = s.apiUrl.takeUnless { it.isBlank() },
                    countConnectedClients = (row.countConnectedClients ?: 0).coerceAtLeast(0),
                    isDefault = s.isDefault
                )
            )
        }
        return ManualServerResolve.NotAvailable
    }
}
