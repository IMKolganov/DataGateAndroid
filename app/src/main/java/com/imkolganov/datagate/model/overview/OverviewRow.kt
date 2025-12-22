package com.imkolganov.datagate.model.overview

data class OverviewRow(
    val ts: String,
    val activeClients: Int,
    val trafficInBytes: Long,
    val trafficOutBytes: Long,
    val trafficTotalBytes: Long
)