package com.imkolganov.datagate.ui.screens.access

import androidx.compose.runtime.Immutable
import com.imkolganov.datagate.model.servers.VpnServerType
import com.imkolganov.datagate.vpn.ServerSelectionMode

interface AccessContract {

    @Immutable
    data class UiState(
        /** Server list (v3 get-all-with-status) in flight. Independent of quota. */
        val isServersLoading: Boolean = false,
        val serversErrorText: String? = null,

        /** Quota plan catalog + assignment in flight. Independent of servers. */
        val isQuotaLoading: Boolean = false,
        /** Overview/summary traffic for the quota bar in flight. */
        val isTrafficLoading: Boolean = false,

        val servers: List<ServerItem> = emptyList(),
        val activeConnections: List<ActiveConnectionItem> = emptyList(),

        val serverSelectionMode: ServerSelectionMode = ServerSelectionMode.AUTO,
        val selectedServerId: Int? = null,

        val quota: QuotaUiState = QuotaUiState()
    ) {
        val isRefreshing: Boolean
            get() = isServersLoading || isQuotaLoading || isTrafficLoading
    }

    @Immutable
    data class QuotaUiState(
        val errorText: String? = null,
        /** Resolved name of the active quota plan (open-ended assignment), if any. */
        val currentPlanName: String? = null,
        val currentEffectiveFrom: String? = null,
        val currentNote: String? = null,
        val allPlans: List<QuotaPlanRow> = emptyList(),
        /** JWT has no OpenVPN external id — overview summary cannot attribute usage. */
        val trafficUsageNeedsExternalId: Boolean = false,
        /** Limit for current period (month or day), bytes; 0 = no cap / none. */
        val quotaLimitBytes: Long = 0L,
        /** Traffic for [externalId] in period from overview/summary; -1 = unknown / not loaded. */
        val trafficUsedBytesForPeriod: Long = -1L,
        val quotaPeriodIsMonthly: Boolean = true
    )

    @Immutable
    data class QuotaPlanRow(
        val id: Int,
        val name: String,
        val description: String?,
        val isActive: Boolean,
        val isDefault: Boolean
    )

    @Immutable
    data class ServerItem(
        val id: Int,
        val name: String,
        val protocol: String?,
        val isOnline: Boolean,
        /** When true, in-app connect uses WSS bridge; when false, direct OpenVPN. */
        val isEnableWss: Boolean,
        /** Backend server stack; OpenVPN and Xray connect in-app. */
        val serverType: VpnServerType = VpnServerType.OpenVpn,

        val uptimeText: String?,
        val openVpnVersionText: String?,
        val totalInText: String?,
        val totalOutText: String?,

        /** Public egress IP reported by the VPN server status log. */
        val serverRemoteIp: String? = null,

        val subtitle: String? = null,
        val loadPercent: Int? = null,
        val activeUsers: Int? = null,
        /** False when backend says this server is not in the user's quota plan. */
        val isAccessibleForQuotaPlan: Boolean = true
    )

    @Immutable
    data class ActiveConnectionItem(
        val id: String,
        val serverId: Int,
        val serverTitle: String,
        val connectedSinceText: String?,
        val virtualIpText: String?
    )

    sealed interface UiEvent {
        /** Reload servers and quota (login / session ready). */
        data object Refresh : UiEvent
        data object RefreshServers : UiEvent
        data object RefreshQuota : UiEvent
        data class SetServerSelectionMode(val mode: ServerSelectionMode) : UiEvent
        data class SelectServer(val serverId: Int) : UiEvent
        data object ClearError : UiEvent
    }
}
