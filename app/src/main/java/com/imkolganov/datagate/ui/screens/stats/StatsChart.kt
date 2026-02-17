package com.imkolganov.datagate.ui.screens.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.imkolganov.datagate.model.overview.Metric
import com.imkolganov.datagate.model.overview.OverviewRow
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Composable
fun StatsChart(
    rows: List<OverviewRow>,
    metric: Metric,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val labels = remember(rows) { rows.map { formatLabel(it.ts) } }
    val xValues = remember(rows) { rows.indices.map { it.toFloat() } }
    val yValues = remember(rows, metric) { rows.map { metricValue(it, metric).toFloat() } }

    LaunchedEffect(xValues, yValues) {
        modelProducer.runTransaction {
            lineSeries { series(xValues, yValues) }
        }
    }

    val bottomAxis = HorizontalAxis.rememberBottom(
        valueFormatter = { _, value, _ ->
            if (!value.isFinite()) "" else labels.getOrNull(value.roundToInt()).orEmpty()
        }
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = bottomAxis
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}

private fun metricValue(r: OverviewRow, metric: Metric): Long {
    return when (metric) {
        Metric.ActiveClients -> r.activeClients.toLong()
        Metric.TrafficTotal -> r.trafficTotalBytes
        Metric.TrafficIn -> r.trafficInBytes
        Metric.TrafficOut -> r.trafficOutBytes
    }
}

private fun formatLabel(ts: String): String {
    val millis = parseIsoToMillis(ts) ?: return ""
    val df = SimpleDateFormat("MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return df.format(Date(millis))
}

fun parseIsoToMillis(ts: String): Long? {
    // Supports:
    // 2025-12-14T11:31:23.73311+00:00
    // 2025-12-14T11:31:23Z
    // 2025-12-14T11:31:23.733Z
    // 2025-12-14T11:31:23+00:00
    val normalized = ts
        .trim()
        .replace("+00:00", "Z")
        .let { s ->
            if (s.endsWith("Z")) s else s
        }

    // If there's fractional seconds, SimpleDateFormat needs exactly 3 digits for SSS.
    // So we cut/pad fractional part to 3 digits.
    val fixedMillis = normalizeFractionToMillis(normalized)

    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX"
    )

    for (p in patterns) {
        try {
            val df = SimpleDateFormat(p, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val d = df.parse(fixedMillis) ?: continue
            return d.time
        } catch (_: Throwable) {
            // try next
        }
    }

    return null
}

private fun normalizeFractionToMillis(input: String): String {
    // If format is ...ss.<fraction>Z or ...ss.<fraction>+hh:mm
    val dot = input.indexOf('.')
    if (dot < 0) return input

    val tzIndex = run {
        val z = input.indexOf('Z', startIndex = dot)
        if (z >= 0) return@run z
        val plus = input.indexOf('+', startIndex = dot)
        if (plus >= 0) return@run plus
        val minus = input.indexOf('-', startIndex = dot + 1) // timezone "-hh:mm" (avoid date part)
        if (minus >= 0) return@run minus
        input.length
    }

    val fraction = input.substring(dot + 1, tzIndex)
    val ms = when {
        fraction.length >= 3 -> fraction.substring(0, 3)
        fraction.isEmpty() -> "000"
        fraction.length == 1 -> fraction + "00"
        else -> fraction + "0" // length == 2
    }

    return input.substring(0, dot) + "." + ms + input.substring(tzIndex)
}
