package com.imkolganov.datagate.model.overview

data class OverviewMeta(
    val from: String,
    val to: String,
    val grouping: String,
    val timezone: String,
    val trafficUnit: String
)