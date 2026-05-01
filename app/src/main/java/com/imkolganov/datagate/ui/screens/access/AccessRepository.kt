package com.imkolganov.datagate.ui.screens.access

interface AccessRepository {
    suspend fun getServers(): List<AccessContract.ServerItem>
    suspend fun getMyActiveConnections(): List<AccessContract.ActiveConnectionItem>
    suspend fun loadQuotaUi(): AccessContract.QuotaUiState
}
