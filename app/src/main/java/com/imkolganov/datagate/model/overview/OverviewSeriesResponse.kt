package com.imkolganov.datagate.model.overview

data class OverviewSeriesResponse(
    val meta: OverviewMeta,
    val summary: OverviewSummary,
    val overviewSeriesRows: List<OverviewRow>
)