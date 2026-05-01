package com.imkolganov.datagate.ui.screens.access

import com.imkolganov.datagate.vpn.ServerSelectionMode

interface AccessContract {

    data class UiState(
        val isLoading: Boolean = false,
        val errorText: String? = null,

        val servers: List<ServerItem> = emptyList(),
        val activeConnections: List<ActiveConnectionItem> = emptyList(),

        val serverSelectionMode: ServerSelectionMode = ServerSelectionMode.AUTO,
        val selectedServerId: Int? = null,

        /** Quota plan summary + full list; loaded together with servers on refresh. */
        val quota: QuotaUiState = QuotaUiState()
    )

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

    data class QuotaPlanRow(
        val id: Int,
        val name: String,
        val description: String?,
        val isActive: Boolean,
        val isDefault: Boolean
    )

    data class ServerItem(
        val id: Int,
        val name: String,
        val protocol: String?,
        val isOnline: Boolean,
        /** In-app VPN requires WSS; if false, show dialog and use OpenVPN Connect instead. */
        val isEnableWss: Boolean,

        val uptimeText: String?,
        val openVpnVersionText: String?,
        val totalInText: String?,
        val totalOutText: String?,

        val subtitle: String? = null,
        val loadPercent: Int? = null,
        val activeUsers: Int? = null,
        /** False when backend says this server is not in the user's quota plan. */
        val isAccessibleForQuotaPlan: Boolean = true
    )

    data class ActiveConnectionItem(
        val id: String,
        val serverId: Int,
        val serverTitle: String,
        val connectedSinceText: String?,
        val virtualIpText: String?
    )

    sealed interface UiEvent {
        data object Refresh : UiEvent
        data class SetServerSelectionMode(val mode: ServerSelectionMode) : UiEvent
        data class SelectServer(val serverId: Int) : UiEvent
        data object ClearError : UiEvent
    }
}
