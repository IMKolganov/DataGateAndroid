package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.model.overview.Metric
import com.imkolganov.datagate.model.overview.OverviewSummary
import com.imkolganov.datagate.model.overview.StatsGrouping
import com.imkolganov.datagate.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupingDropdown(
    value: StatsGrouping,
    onChange: (StatsGrouping) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(
                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = true
            ),
            readOnly = true,
            value = value.name,
            onValueChange = {},
            label = { Text("Grouping") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            StatsGrouping.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricDropdown(
    value: Metric,
    onChange: (Metric) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(
                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = true
            ),
            readOnly = true,
            value = value.displayName,
            onValueChange = {},
            label = { Text("Metric") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Metric.all.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
fun SummaryRow(summary: OverviewSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryCard("Traffic In", formatBytes(summary.totalTrafficInBytes))
        SummaryCard("Traffic Out", formatBytes(summary.totalTrafficOutBytes))
//        SummaryCard("Peak Clients", summary.peakActiveClients.toString())
    }
}

@Composable
fun SummaryCard(title: String, value: String) {
    Card(Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title)
            Text(value)
        }
    }
}
