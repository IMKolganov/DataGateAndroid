package com.imkolganov.datagate.ui.screens.access

import com.imkolganov.datagate.TEMP_IGNORE_QUOTA_PLAN_CLIENT_CHECKS
import com.imkolganov.datagate.auth.TokenStore
import com.imkolganov.datagate.auth.getAuthInfo
import com.imkolganov.datagate.model.quota.QuotaPlanDto
import com.imkolganov.datagate.model.quota.UserQuotaPlanDto
import com.imkolganov.datagate.model.servers.OpenVpnServerV2Dto
import com.imkolganov.datagate.quota.QuotaPlanApi
import com.imkolganov.datagate.servers.OpenVpnServersRepository
import com.imkolganov.datagate.stats.StatsApiClient
import com.imkolganov.datagate.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class AccessRepositoryImpl(
    private val serversRepository: OpenVpnServersRepository,
    private val tokenStore: TokenStore,
    private val quotaPlanApi: QuotaPlanApi,
    private val statsApi: StatsApiClient
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
                serverType = s.serverType,
                uptimeText = status?.upSince,
                openVpnVersionText = status?.version,
                totalInText = formatBytes(row.totalBytesIn),
                totalOutText = formatBytes(row.totalBytesOut),
                serverRemoteIp = status?.serverRemoteIp?.trim()?.takeIf { it.isNotEmpty() },
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

    override suspend fun loadQuotaPlanUi(): AccessContract.QuotaUiState = withContext(Dispatchers.IO) {
        val auth = tokenStore.getAuthInfo()
        val uid = auth.userId?.toIntOrNull()
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

            val active = pickActiveAssignmentValidNow(assignments)
            val matchedPlan: QuotaPlanDto? = active?.let { a ->
                plans.firstOrNull { it.id == a.quotaPlanId }
            }
            val planName = when {
                matchedPlan != null && matchedPlan.name.isNotBlank() -> matchedPlan.name
                active != null && active.quotaPlanId >= 0 -> "Quota plan #${active.quotaPlanId}"
                else -> null
            }

            var quotaLimitBytes = 0L
            var quotaPeriodIsMonthly = true
            if (matchedPlan != null) {
                val monthly = matchedPlan.monthlyQuotaBytes
                val daily = matchedPlan.dailyQuotaBytes
                when {
                    monthly != null && monthly > 0L -> {
                        quotaLimitBytes = monthly
                        quotaPeriodIsMonthly = true
                    }
                    daily != null && daily > 0L -> {
                        quotaLimitBytes = daily
                        quotaPeriodIsMonthly = false
                    }
                }
            }

            val ext = auth.externalId?.trim()?.takeIf { it.isNotEmpty() }
            val trafficUsageNeedsExternalId = quotaLimitBytes > 0L && ext.isNullOrEmpty()

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
                allPlans = rows,
                trafficUsageNeedsExternalId = trafficUsageNeedsExternalId,
                quotaLimitBytes = quotaLimitBytes,
                trafficUsedBytesForPeriod = -1L,
                quotaPeriodIsMonthly = quotaPeriodIsMonthly
            )
        } catch (e: Exception) {
            AccessContract.QuotaUiState(errorText = e.message ?: "Quota load failed")
        }
    }

    override suspend fun loadQuotaTrafficUsedBytes(periodIsMonthly: Boolean): Long =
        withContext(Dispatchers.IO) {
            val ext = tokenStore.getAuthInfo().externalId?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@withContext -1L
            val (fromIso, toIso) = quotaPeriodUtcIsoPair(periodIsMonthly)
            try {
                val sumResp = statsApi.getOverviewSummary(fromIso, toIso, ext)
                if (sumResp.success && sumResp.data != null) sumResp.data.trafficTotalBytes else -1L
            } catch (_: Exception) {
                -1L
            }
        }

    /**
     * Matches Linux [pickActiveAssignmentValidNow]: assignment valid for "now" by [effectiveFrom]/[effectiveTo],
     * then latest [effectiveFrom] (then highest id on tie).
     */
    private fun pickActiveAssignmentValidNow(items: List<UserQuotaPlanDto>): UserQuotaPlanDto? {
        if (items.isEmpty()) return null
        val now = Instant.now()
        val valid = items.filter { a ->
            val from = parseAssignmentInstant(a.effectiveFrom)
            val started = from == null || !now.isBefore(from)
            val toRaw = a.effectiveTo?.trim()
            val to = if (toRaw.isNullOrEmpty()) null else parseAssignmentInstant(toRaw)
            val notEnded = to == null || !now.isAfter(to)
            started && notEnded
        }
        if (valid.isEmpty()) return null
        return valid.maxWith(
            compareBy<UserQuotaPlanDto> { parseAssignmentInstant(it.effectiveFrom) ?: Instant.EPOCH }
                .thenBy { it.id }
        )
    }

    private fun parseAssignmentInstant(s: String?): Instant? {
        if (s.isNullOrBlank()) return null
        return try {
            java.time.OffsetDateTime.parse(s).toInstant()
        } catch (_: Exception) {
            try {
                Instant.parse(s)
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Same window as Linux: local calendar month or local day → UTC ISO instants for the API. */
    private fun quotaPeriodUtcIsoPair(monthly: Boolean): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val fromZ = if (monthly) {
            YearMonth.from(today).atDay(1).atStartOfDay(zone)
        } else {
            today.atStartOfDay(zone)
        }
        val toZ = if (monthly) {
            YearMonth.from(today).atEndOfMonth().atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(zone)
        } else {
            today.atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(zone)
        }
        return fromZ.toInstant().toString() to toZ.toInstant().toString()
    }

    override suspend fun getMyActiveConnections(): List<AccessContract.ActiveConnectionItem> = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken() ?: return@withContext emptyList()

        // TODO: call your backend endpoint here. Example (replace with your real API client):
        // val response = someApi.getMyActiveConnections(token)
        // return@withContext response.data.map { ... }

        emptyList()
    }
}
