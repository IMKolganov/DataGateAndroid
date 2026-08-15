package com.imkolganov.datagate.servers

import com.imkolganov.datagate.TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS
import com.imkolganov.datagate.model.servers.OpenVpnServerWithStatusV2Item
import com.imkolganov.datagate.model.servers.VpnServerType

/**
 * Pure client-side rules for which VPN servers may be auto-picked or manually connected.
 * Backend create/download still enforces quota; this keeps users from hitting that 400.
 */
object VpnServerConnectPolicy {

    fun pickBestServer(
        items: List<OpenVpnServerWithStatusV2Item>,
        ignoreQuotaPlanChecks: Boolean = TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS,
    ): BestServerResult {
        val candidates = items.mapNotNull { row -> candidateOrNull(row, ignoreQuotaPlanChecks) }
        if (candidates.isEmpty()) {
            throw IllegalStateException("No online servers available")
        }
        return candidates.minWith(
            compareBy<BestServerResult> { it.countConnectedClients }
                .thenBy { it.serverId }
        )
    }

    fun resolveManualConnection(
        items: List<OpenVpnServerWithStatusV2Item>,
        serverId: Int,
        ignoreQuotaPlanChecks: Boolean = TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS,
    ): ManualServerResolve {
        for (row in items) {
            val s = row.server
            if (s.id != serverId) continue
            if (s.isDeleted) return ManualServerResolve.NotAvailable
            if (!s.isOnline) return ManualServerResolve.NotAvailable
            val name = s.serverName.trim().takeUnless { it.isBlank() } ?: "Server #${s.id}"
            if (!ignoreQuotaPlanChecks && !s.isAccessibleForUserQuotaPlan) {
                return ManualServerResolve.QuotaPlanBlocked(name)
            }
            return when (s.serverType) {
                VpnServerType.OpenVpn -> ManualServerResolve.Ok(
                    BestServerResult(
                        serverId = s.id,
                        name = name,
                        apiUrl = s.apiUrl.takeUnless { it.isBlank() },
                        countConnectedClients = (row.countConnectedClients ?: 0).coerceAtLeast(0),
                        isDefault = s.isDefault,
                        useWss = s.isEnableWss,
                        serverType = VpnServerType.OpenVpn,
                    )
                )
                VpnServerType.Xray -> ManualServerResolve.Ok(
                    BestServerResult(
                        serverId = s.id,
                        name = name,
                        apiUrl = s.apiUrl.takeUnless { it.isBlank() },
                        countConnectedClients = (row.countConnectedClients ?: 0).coerceAtLeast(0),
                        isDefault = s.isDefault,
                        useWss = false,
                        serverType = VpnServerType.Xray,
                    )
                )
                VpnServerType.Unknown -> ManualServerResolve.RequiresUnsupportedServerType(name)
            }
        }
        return ManualServerResolve.NotAvailable
    }

    private fun candidateOrNull(
        row: OpenVpnServerWithStatusV2Item,
        ignoreQuotaPlanChecks: Boolean,
    ): BestServerResult? {
        val s = row.server
        if (s.isDeleted) return null
        if (!ignoreQuotaPlanChecks && !s.isAccessibleForUserQuotaPlan) return null
        if (!s.isOnline) return null
        val name = s.serverName.trim().takeUnless { it.isBlank() } ?: "Server #${s.id}"
        val clients = (row.countConnectedClients ?: 0).coerceAtLeast(0)
        return when (s.serverType) {
            VpnServerType.OpenVpn -> {
                // Auto-pick still prefers WSS-capable OpenVPN servers only.
                if (!s.isEnableWss) return null
                BestServerResult(
                    serverId = s.id,
                    name = name,
                    apiUrl = s.apiUrl.takeUnless { it.isBlank() },
                    countConnectedClients = clients,
                    isDefault = s.isDefault,
                    useWss = true,
                    serverType = VpnServerType.OpenVpn,
                )
            }
            VpnServerType.Xray -> BestServerResult(
                serverId = s.id,
                name = name,
                apiUrl = s.apiUrl.takeUnless { it.isBlank() },
                countConnectedClients = clients,
                isDefault = s.isDefault,
                useWss = false,
                serverType = VpnServerType.Xray,
            )
            VpnServerType.Unknown -> null
        }
    }
}
