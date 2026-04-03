package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R

@Composable
fun PresetsRow(
    selectedDays: Int?,
    isLoading: Boolean,
    onSelectDays: (Int) -> Unit,
    onReload: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterChip(
            selected = selectedDays == 7,
            onClick = { onSelectDays(7) },
            label = { Text(stringResource(R.string.presets_last_7)) }
        )

        FilterChip(
            selected = selectedDays == 30,
            onClick = { onSelectDays(30) },
            label = { Text(stringResource(R.string.presets_last_30)) }
        )

        IconButton(
            onClick = onReload,
            enabled = !isLoading
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reload))
        }
    }
}
