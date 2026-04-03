package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imkolganov.datagate.ui.components.AppCards

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppCards.shape,
                    colors = AppCards.defaultColors(),
                    elevation = AppCards.defaultElevation()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.stats_filters))
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
                        selectedDays = state.selectedPresetDays,
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

            if (state.error != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppCards.shape,
                        colors = AppCards.defaultColors(),
                        elevation = AppCards.defaultElevation()
                    ) {
                        Text(
                            text = stringResource(R.string.stats_error_prefix, state.error ?: ""),
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppCards.shape,
                            colors = AppCards.defaultColors(),
                            elevation = AppCards.defaultElevation()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.stats_overview))
                                Text(
                                    text = stringResource(R.string.stats_chart_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                StatsChart(
                                    rows = resp.overviewSeriesRows,
                                    metric = state.metric,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .height(280.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (state.isLoading) {
            Dialog(
                onDismissRequest = { viewModel.cancelLoad() },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = AppCards.shape,
                    colors = AppCards.defaultColors(),
                    elevation = AppCards.defaultElevation()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.stats_loading_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { viewModel.cancelLoad() },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
    }
}
