package com.imkolganov.datagate.ui.screens.stats

import com.imkolganov.datagate.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Quick range presets for the statistics screen (parity with DataGateMonitor DateRangeFilter). */
enum class StatsDatePreset {
    Last7Days,
    Last30Days,
    Last2Months,
    Last3Months,
    Last6Months,
    Last1Year,
    Last2Years,
}

fun StatsDatePreset.labelRes(): Int = when (this) {
    StatsDatePreset.Last7Days -> R.string.presets_last_7
    StatsDatePreset.Last30Days -> R.string.presets_last_30
    StatsDatePreset.Last2Months -> R.string.presets_last_2_months
    StatsDatePreset.Last3Months -> R.string.presets_last_3_months
    StatsDatePreset.Last6Months -> R.string.presets_last_6_months
    StatsDatePreset.Last1Year -> R.string.presets_last_1_year
    StatsDatePreset.Last2Years -> R.string.presets_last_2_years
}

/** Short chip label for compact filter row. */
fun StatsDatePreset.chipLabelRes(): Int = when (this) {
    StatsDatePreset.Last7Days -> R.string.presets_chip_7
    StatsDatePreset.Last30Days -> R.string.presets_chip_30
    StatsDatePreset.Last2Months -> R.string.presets_chip_2m
    StatsDatePreset.Last3Months -> R.string.presets_chip_3m
    StatsDatePreset.Last6Months -> R.string.presets_chip_6m
    StatsDatePreset.Last1Year -> R.string.presets_chip_1y
    StatsDatePreset.Last2Years -> R.string.presets_chip_2y
}

private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")

fun isoRangeForStatsPreset(
    preset: StatsDatePreset,
    deviceTz: TimeZone = TimeZone.getDefault(),
): Pair<String, String> {
    val todayStart = startOfTodayMillis(deviceTz)
    val toMillis = endOfTodayMillis(deviceTz)
    val fromMillis = when (preset) {
        StatsDatePreset.Last7Days -> addDays(todayStart, -7, deviceTz)
        StatsDatePreset.Last30Days -> addDays(todayStart, -30, deviceTz)
        StatsDatePreset.Last2Months -> addMonths(todayStart, -2, deviceTz)
        StatsDatePreset.Last3Months -> addMonths(todayStart, -3, deviceTz)
        StatsDatePreset.Last6Months -> addMonths(todayStart, -6, deviceTz)
        StatsDatePreset.Last1Year -> addYears(todayStart, -1, deviceTz)
        StatsDatePreset.Last2Years -> addYears(todayStart, -2, deviceTz)
    }
    return isoUtc(fromMillis) to isoUtc(toMillis)
}

private fun isoUtc(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    fmt.timeZone = utcTz
    return fmt.format(Date(millis))
}

private fun startOfTodayMillis(tz: TimeZone): Long {
    val c = Calendar.getInstance(tz)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun endOfTodayMillis(tz: TimeZone): Long {
    val tomorrowStart = addDays(startOfTodayMillis(tz), 1, tz)
    return tomorrowStart - 1000L
}

private fun addDays(millis: Long, days: Int, tz: TimeZone): Long {
    val c = Calendar.getInstance(tz)
    c.timeInMillis = millis
    c.add(Calendar.DAY_OF_YEAR, days)
    return c.timeInMillis
}

private fun addMonths(millis: Long, months: Int, tz: TimeZone): Long {
    val c = Calendar.getInstance(tz)
    c.timeInMillis = millis
    c.add(Calendar.MONTH, months)
    return c.timeInMillis
}

private fun addYears(millis: Long, years: Int, tz: TimeZone): Long {
    val c = Calendar.getInstance(tz)
    c.timeInMillis = millis
    c.add(Calendar.YEAR, years)
    return c.timeInMillis
}
