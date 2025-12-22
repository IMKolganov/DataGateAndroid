package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrettyDateRangePicker(
    fromIso: String,
    toIso: String,
    onFromIsoChange: (String) -> Unit,
    onToIsoChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    val fromLabel = isoToHumanDate(fromIso) ?: fromIso
    val toLabel = isoToHumanDate(toIso) ?: toIso

    FilledTonalButton(
        onClick = { open = true },
        modifier = modifier
    ) {
        Icon(Icons.Filled.DateRange, contentDescription = null)
        Text("  ")
        Text(
            text = "Date range",
            fontWeight = FontWeight.SemiBold
        )
        Text("  ")
        Text(
            text = "$fromLabel → $toLabel",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    if (open) {
        val initialStart = parseIsoToMillis(fromIso)
        val initialEnd = parseIsoToMillis(toIso)

        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialStart,
            initialSelectedEndDateMillis = initialEnd
        )

        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            onFromIsoChange(isoUtc(startOfDayUtcMillis(start)))
                            onToIsoChange(isoUtc(endOfDayUtcMillis(end)))
                        }
                        open = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState)
        }
    }
}

private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")

private fun isoUtc(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = utcTz
    return fmt.format(Date(millis))
}

private fun isoToHumanDate(iso: String): String? {
    val ms = parseIsoToMillis(iso) ?: return null
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fmt.timeZone = utcTz
    return fmt.format(Date(ms))
}

private fun startOfDayUtcMillis(anyUtcMillisInThatDay: Long): Long {
    val c = Calendar.getInstance(utcTz)
    c.timeInMillis = anyUtcMillisInThatDay
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun endOfDayUtcMillis(anyUtcMillisInThatDay: Long): Long {
    val start = startOfDayUtcMillis(anyUtcMillisInThatDay)
    val c = Calendar.getInstance(utcTz)
    c.timeInMillis = start
    c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis - 1000L
}
