package com.imkolganov.datagate.ui.screens.stats

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import androidx.compose.ui.unit.sp
import com.imkolganov.datagate.model.overview.Metric
import com.imkolganov.datagate.model.overview.OverviewRow
import com.imkolganov.datagate.util.formatBytes
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberFadingEdges
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.shape.Shape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun StatsChart(
    rows: List<OverviewRow>,
    metric: Metric,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chart_no_points),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    val labels = remember(rows) { rows.map { formatLabel(it.ts) } }
    val xValues = remember(rows) { rows.indices.map { it.toFloat() } }
    val yValues = remember(rows, metric) { rows.map { metricValue(it, metric).toFloat() } }

    LaunchedEffect(xValues, yValues) {
        modelProducer.runTransaction {
            lineSeries { series(xValues, yValues) }
        }
    }

    val trafficMetrics = remember(metric) {
        metric == Metric.TrafficTotal || metric == Metric.TrafficIn || metric == Metric.TrafficOut
    }

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outlineSoft = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    val vicoTheme = rememberM3VicoTheme(
        lineCartesianLayerColors = listOf(primary),
        lineColor = outlineSoft,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    ProvideVicoTheme(theme = vicoTheme) {
        val lineSpec = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(primary)),
            stroke = LineCartesianLayer.LineStroke.Continuous(
                thicknessDp = 3.dp.value,
                cap = Paint.Cap.ROUND
            ),
            areaFill = LineCartesianLayer.AreaFill.single(
                fill(primary.copy(alpha = 0.22f))
            ),
            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0.5f)
        )

        val lineLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(lineSpec)
        )

        val markerLabel = rememberTextComponent(
            color = onSurface,
            textSize = 12.sp
        )

        val markerGuideline = rememberLineComponent(
            fill = fill(outlineSoft),
            thickness = 1.dp
        )

        val markerValueFormatter = remember(labels, trafficMetrics) {
            DefaultCartesianMarker.ValueFormatter { _, targets ->
                buildString {
                    targets.forEachIndexed { ti, target ->
                        if (target !is LineCartesianLayerMarkerTarget) return@forEachIndexed
                        if (ti > 0) append('\n')
                        val idx = target.x.roundToInt().coerceIn(labels.indices)
                        val date = labels.getOrNull(idx).orEmpty()
                        val y = target.points.firstOrNull()?.entry?.y ?: return@forEachIndexed
                        val valueStr = if (trafficMetrics) {
                            formatBytes(y.toLong().absoluteValue)
                        } else {
                            y.roundToInt().toString()
                        }
                        if (date.isNotEmpty()) {
                            append(date)
                            append(" · ")
                        }
                        append(valueStr)
                    }
                }
            }
        }

        val marker = rememberDefaultCartesianMarker(
            label = markerLabel,
            valueFormatter = markerValueFormatter,
            labelPosition = DefaultCartesianMarker.LabelPosition.Bottom,
            guideline = markerGuideline,
            indicator = { color ->
                shapeComponent(
                    fill = fill(color),
                    shape = Shape.Rectangle,
                    margins = Insets(allDp = 1f)
                )
            },
            indicatorSize = 8.dp
        )

        val bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = remember(labels) {
                CartesianValueFormatter { _, value, _ ->
                    if (!value.isFinite()) {
                        AXIS_PLACEHOLDER
                    } else {
                        val idx = value.roundToInt().coerceIn(0, (labels.size - 1).coerceAtLeast(0))
                        labels.getOrNull(idx)?.takeIf { it.isNotBlank() } ?: AXIS_PLACEHOLDER
                    }
                }
            }
        )

        val startAxis = VerticalAxis.rememberStart(
            valueFormatter = remember(trafficMetrics) {
                CartesianValueFormatter { _, value, _ ->
                    if (!value.isFinite()) {
                        AXIS_PLACEHOLDER
                    } else if (trafficMetrics) {
                        formatBytes(value.toLong().absoluteValue)
                    } else {
                        value.roundToInt().toString().ifBlank { AXIS_PLACEHOLDER }
                    }
                }
            }
        )

        val scrollState = rememberVicoScrollState(scrollEnabled = true)
        val zoomState = rememberVicoZoomState(zoomEnabled = rows.size > 1)
        val fadingEdges = rememberFadingEdges(width = 10.dp)

        val chart = rememberCartesianChart(
            lineLayer,
            startAxis = startAxis,
            bottomAxis = bottomAxis,
            marker = marker,
            fadingEdges = fadingEdges
        )

        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            scrollState = scrollState,
            zoomState = zoomState,
            modifier = modifier
        )
    }
}

private fun metricValue(r: OverviewRow, metric: Metric): Long {
    return when (metric) {
        Metric.ActiveClients -> r.activeClients.toLong()
        Metric.TrafficTotal -> r.trafficTotalBytes
        Metric.TrafficIn -> r.trafficInBytes
        Metric.TrafficOut -> r.trafficOutBytes
    }
}

/** Vico forbids empty axis labels; use this instead of "". */
private const val AXIS_PLACEHOLDER = "—"

private fun formatLabel(ts: String): String {
    val millis = parseIsoToMillis(ts)
    if (millis == null) {
        val t = ts.trim()
        return if (t.isNotEmpty()) t.take(16) else AXIS_PLACEHOLDER
    }
    val df = SimpleDateFormat("MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return df.format(Date(millis))
}

fun parseIsoToMillis(ts: String): Long? {
    val normalized = ts
        .trim()
        .replace("+00:00", "Z")
        .let { s ->
            if (s.endsWith("Z")) s else s
        }

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
    val dot = input.indexOf('.')
    if (dot < 0) return input

    val tzIndex = run {
        val z = input.indexOf('Z', startIndex = dot)
        if (z >= 0) return@run z
        val plus = input.indexOf('+', startIndex = dot)
        if (plus >= 0) return@run plus
        val minus = input.indexOf('-', startIndex = dot + 1)
        if (minus >= 0) return@run minus
        input.length
    }

    val fraction = input.substring(dot + 1, tzIndex)
    val ms = when {
        fraction.length >= 3 -> fraction.substring(0, 3)
        fraction.isEmpty() -> "000"
        fraction.length == 1 -> fraction + "00"
        else -> fraction + "0"
    }

    return input.substring(0, dot) + "." + ms + input.substring(tzIndex)
}
