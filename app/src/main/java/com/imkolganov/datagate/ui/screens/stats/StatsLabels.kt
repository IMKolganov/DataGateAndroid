package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.imkolganov.datagate.R
import com.imkolganov.datagate.model.overview.Metric
import com.imkolganov.datagate.model.overview.StatsGrouping

@Composable
fun StatsGrouping.localizedName(): String = when (this) {
    StatsGrouping.Auto -> stringResource(R.string.grouping_auto)
    StatsGrouping.Hours -> stringResource(R.string.grouping_hours)
    StatsGrouping.Months -> stringResource(R.string.grouping_months)
    StatsGrouping.Years -> stringResource(R.string.grouping_years)
}

@Composable
fun Metric.localizedName(): String = when (this) {
    Metric.ActiveClients -> stringResource(R.string.metric_active_clients)
    Metric.TrafficTotal -> stringResource(R.string.metric_traffic_total)
    Metric.TrafficIn -> stringResource(R.string.metric_traffic_in)
    Metric.TrafficOut -> stringResource(R.string.metric_traffic_out)
}
