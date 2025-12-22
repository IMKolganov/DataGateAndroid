package com.imkolganov.datagate.model.overview

data class OverviewSummary(
    val totalTrafficInBytes: Long,
    val totalTrafficOutBytes: Long,
    val peakActiveClients: Int
)