package com.imkolganov.datagate.model.overview

/** Totals from GET …/overview/summary (traffic only; used for quota bar). */
data class OverviewSummaryTotals(
    val trafficTotalBytes: Long
)
