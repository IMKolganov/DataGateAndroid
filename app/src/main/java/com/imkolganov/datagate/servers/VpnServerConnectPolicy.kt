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
            throw IllegalStateException("No online WSS servers available")
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
            if (s.serverType != VpnServerType.OpenVpn) {
                return when (s.serverType) {
                    VpnServerType.Xray -> ManualServerResolve.RequiresXrayClient(name)
                    VpnServerType.OpenVpn -> error("unreachable")
                    VpnServerType.Unknown -> ManualServerResolve.RequiresUnsupportedServerType(name)
                }
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

    private fun candidateOrNull(
        row: OpenVpnServerWithStatusV2Item,
        ignoreQuotaPlanChecks: Boolean,
    ): BestServerResult? {
        val s = row.server
        if (s.isDeleted) return null
        if (!ignoreQuotaPlanChecks && !s.isAccessibleForUserQuotaPlan) return null
        if (!s.isOnline) return null
        if (s.serverType != VpnServerType.OpenVpn) return null
        if (!s.isEnableWss) return null
        return BestServerResult(
            serverId = s.id,
            name = s.serverName.trim().takeUnless { it.isBlank() } ?: "Server #${s.id}",
            apiUrl = s.apiUrl.takeUnless { it.isBlank() },
            countConnectedClients = (row.countConnectedClients ?: 0).coerceAtLeast(0),
            isDefault = s.isDefault
        )
    }
}
