package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Filters")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GroupingDropdown(
                            value = state.filters.grouping,
                            onChange = viewModel::setGrouping,
                            modifier = Modifier.weight(1f)
                        )

                        MetricDropdown(
                            value = state.metric,
                            onChange = viewModel::setMetric,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    PresetsRow(
                        selectedDays = null,
                        isLoading = state.isLoading,
                        onSelectDays = { days ->
                            viewModel.setLastDays(days.toLong())
                            viewModel.load()
                        },
                        onReload = viewModel::load
                    )

                    PrettyDateRangePicker(
                        fromIso = state.filters.fromIso,
                        toIso = state.filters.toIso,
                        onFromIsoChange = viewModel::setFromIso,
                        onToIsoChange = viewModel::setToIso,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (state.isLoading) {
            item {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Error: ${state.error}",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            val resp = state.response
            if (resp != null) {
                item {
                    SummaryRow(resp.summary)
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Overview")
                            StatsChart(
                                rows = resp.overviewSeriesRows,
                                metric = state.metric,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .height(260.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
