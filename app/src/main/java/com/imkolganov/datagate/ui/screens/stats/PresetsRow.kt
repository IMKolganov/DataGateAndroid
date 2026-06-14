package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R

private val presetOrder = listOf(
    StatsDatePreset.Last7Days,
    StatsDatePreset.Last30Days,
    StatsDatePreset.Last2Months,
    StatsDatePreset.Last3Months,
    StatsDatePreset.Last6Months,
    StatsDatePreset.Last1Year,
    StatsDatePreset.Last2Years,
)

@Composable
fun PresetsRow(
    selectedPreset: StatsDatePreset?,
    isLoading: Boolean,
    onSelectPreset: (StatsDatePreset) -> Unit,
    onReload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(presetOrder) { preset ->
                val fullLabel = stringResource(preset.labelRes())
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    enabled = !isLoading,
                    label = {
                        Text(
                            text = stringResource(preset.chipLabelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier
                        .height(28.dp)
                        .semantics { contentDescription = fullLabel },
                )
            }
        }

        IconButton(
            onClick = onReload,
            enabled = !isLoading,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.action_reload),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
