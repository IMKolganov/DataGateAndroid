package com.imkolganov.datagate.ui.screens.access

import com.imkolganov.datagate.TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.getAuthInfo
import com.imkolganov.datagate.model.quota.UserQuotaPlanDto
import com.imkolganov.datagate.quota.QuotaPlanApi
import com.imkolganov.datagate.model.servers.OpenVpnServerV2Dto
import com.imkolganov.datagate.servers.OpenVpnServersRepository
import com.imkolganov.datagate.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccessRepositoryImpl(
    private val serversRepository: OpenVpnServersRepository,
    private val tokenStore: TokenStore,
    private val quotaPlanApi: QuotaPlanApi
) : AccessRepository {

    override suspend fun getServers(): List<AccessContract.ServerItem> = withContext(Dispatchers.IO) {
        val items = serversRepository.listServersWithStatus()

        items.mapNotNull { row ->
            val s = row.server
            if (s.isDeleted) return@mapNotNull null

            val status = row.openVpnServerStatusLogResponse

            AccessContract.ServerItem(
                id = s.id,
                name = s.serverName.ifBlank { "OpenVPN Server" },
                protocol = s.tags.firstOrNull(),
                isOnline = s.isOnline,
                isEnableWss = s.isEnableWss,
                uptimeText = status?.upSince,
                openVpnVersionText = status?.version,
                totalInText = formatBytes(row.totalBytesIn),
                totalOutText = formatBytes(row.totalBytesOut),
                subtitle = quotaPlanSubtitle(s),
                loadPercent = null,
                activeUsers = row.countConnectedClients,
                isAccessibleForQuotaPlan = TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS || s.isAccessibleForUserQuotaPlan
            )
        }
    }

    private fun quotaPlanSubtitle(s: OpenVpnServerV2Dto): String? {
        if (s.quotaPlanGroups.isEmpty()) return null
        return s.quotaPlanGroups.joinToString(separator = ", ") { it.name }
    }

    override suspend fun loadQuotaUi(): AccessContract.QuotaUiState = withContext(Dispatchers.IO) {
        val uid = tokenStore.getAuthInfo().userId?.toIntOrNull()
            ?: return@withContext AccessContract.QuotaUiState()

        try {
            val plansResp = quotaPlanApi.getAllQuotaPlans(includeInactive = true)
            val userResp = quotaPlanApi.getUserQuotaPlansByUserId(uid)

            if (!plansResp.success) {
                return@withContext AccessContract.QuotaUiState(
                    errorText = (plansResp.message ?: "").ifBlank { "Quota plans request failed" }
                )
            }
            if (!userResp.success) {
                return@withContext AccessContract.QuotaUiState(
                    errorText = (userResp.message ?: "").ifBlank { "User quota plans request failed" }
                )
            }

            val plans = plansResp.data?.quotaPlans.orEmpty()
            val assignments = userResp.data?.items.orEmpty()

            val active = pickActiveAssignment(assignments)
            val planName = active?.let { a ->
                plans.firstOrNull { it.id == a.quotaPlanId }?.name
                    ?: "Quota plan #${a.quotaPlanId}"
            }

            val rows = plans.map { p ->
                AccessContract.QuotaPlanRow(
                    id = p.id,
                    name = p.name.ifBlank { "—" },
                    description = p.description,
                    isActive = p.isActive,
                    isDefault = p.isDefault
                )
            }.sortedWith(compareBy({ !it.isDefault }, { it.name }))

            AccessContract.QuotaUiState(
                errorText = null,
                currentPlanName = planName,
                currentEffectiveFrom = active?.effectiveFrom,
                currentNote = active?.note,
                allPlans = rows
            )
        } catch (e: Exception) {
            AccessContract.QuotaUiState(errorText = e.message ?: "Quota load failed")
        }
    }

    /**
     * Matches backend [GetActiveByUserId]: open-ended assignment ([effectiveTo] null),
     * then latest [effectiveFrom].
     */
    private fun pickActiveAssignment(items: List<UserQuotaPlanDto>): UserQuotaPlanDto? {
        if (items.isEmpty()) return null
        val openEnded = items.filter { it.effectiveTo.isNullOrBlank() }
        val pool = if (openEnded.isNotEmpty()) openEnded else items
        return pool.maxWithOrNull(
            compareBy<UserQuotaPlanDto> { it.effectiveFrom ?: "" }
                .thenByDescending { it.id }
        )
    }

    override suspend fun getMyActiveConnections(): List<AccessContract.ActiveConnectionItem> = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken() ?: return@withContext emptyList()

        // TODO: call your backend endpoint here. Example (replace with your real API client):
        // val response = someApi.getMyActiveConnections(token)
        // return@withContext response.data.map { ... }

        emptyList()
    }
}
