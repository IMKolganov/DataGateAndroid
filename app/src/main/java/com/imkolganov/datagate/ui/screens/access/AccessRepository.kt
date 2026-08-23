package com.imkolganov.datagate.ui.screens.access

interface AccessRepository {
    suspend fun getServers(): List<AccessContract.ServerItem>
    suspend fun getMyActiveConnections(): List<AccessContract.ActiveConnectionItem>

    /**
     * Quota plan catalog + active assignment + byte limits.
     * Does **not** call overview/summary — [trafficUsedBytesForPeriod] stays -1.
     */
    suspend fun loadQuotaPlanUi(): AccessContract.QuotaUiState

    /**
     * User traffic for the current quota period via overview/summary.
     * @return used bytes, or -1 if unavailable.
     */
    suspend fun loadQuotaTrafficUsedBytes(periodIsMonthly: Boolean): Long
}
