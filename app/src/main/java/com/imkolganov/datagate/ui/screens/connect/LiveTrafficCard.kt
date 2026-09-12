package com.imkolganov.datagate.ui.screens.connect

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imkolganov.datagate.R
import com.imkolganov.datagate.util.formatBytes
import com.imkolganov.datagate.util.formatBytesPerSecond
import com.imkolganov.datagate.vpn.traffic.TrafficDelta
import com.imkolganov.datagate.vpn.traffic.TrafficSample
import com.imkolganov.datagate.vpn.traffic.VpnTrafficUiState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import kotlin.math.absoluteValue

/** Same pairing as DataGateMac LiveTrafficChart (system green / orange). */
private val DownloadGreen = Color(0xFF34C759)
private val UploadOrange = Color(0xFFFF9F0A)

@Composable
fun LiveTrafficCard(
    traffic: VpnTrafficUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .padding(horizontal = 8.dp)
            .widthIn(max = 520.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home_traffic),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.home_traffic_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TrafficRateLabel(
                    title = stringResource(R.string.home_traffic_download),
                    speed = formatBytesPerSecond(traffic.speedInBps),
                    session = formatBytes(traffic.sessionBytesIn),
                    color = DownloadGreen,
                    modifier = Modifier.weight(1f),
                )
                TrafficRateLabel(
                    title = stringResource(R.string.home_traffic_upload),
                    speed = formatBytesPerSecond(traffic.speedOutBps),
                    session = formatBytes(traffic.sessionBytesOut),
                    color = UploadOrange,
                    modifier = Modifier.weight(1f),
                )
            }
            LiveTrafficChart(
                samples = traffic.samples,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrafficRateLabel(
    title: String,
    speed: String,
    session: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = speed,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
        Text(
            text = session,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveTrafficChart(
    samples: List<TrafficSample>,
    modifier: Modifier = Modifier,
) {
    if (!TrafficDelta.shouldShowChart(samples.size)) {
        Box(
            modifier = modifier.height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.home_traffic_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val xValues = remember(samples) { samples.indices.map { it.toFloat() } }
    val downValues = remember(samples) { samples.map { it.speedInBps.toFloat() } }
    val upValues = remember(samples) { samples.map { it.speedOutBps.toFloat() } }

    LaunchedEffect(xValues, downValues, upValues) {
        modelProducer.runTransaction {
            lineSeries {
                series(xValues, downValues)
                series(xValues, upValues)
            }
        }
    }

    val outlineSoft = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val vicoTheme = rememberM3VicoTheme(
        lineCartesianLayerColors = listOf(DownloadGreen, UploadOrange),
        lineColor = outlineSoft,
        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    ProvideVicoTheme(theme = vicoTheme) {
        val downLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(DownloadGreen)),
            stroke = LineCartesianLayer.LineStroke.Continuous(
                thicknessDp = 2.dp.value,
                cap = Paint.Cap.ROUND,
            ),
            areaFill = LineCartesianLayer.AreaFill.single(fill(DownloadGreen.copy(alpha = 0.22f))),
            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0.5f),
        )
        val upLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(UploadOrange)),
            stroke = LineCartesianLayer.LineStroke.Continuous(
                thicknessDp = 2.dp.value,
                cap = Paint.Cap.ROUND,
            ),
            areaFill = LineCartesianLayer.AreaFill.single(fill(UploadOrange.copy(alpha = 0.22f))),
            pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0.5f),
        )
        val lineLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(downLine, upLine),
        )
        val startAxis = VerticalAxis.rememberStart(
            valueFormatter = remember {
                CartesianValueFormatter { _, value, _ ->
                    if (!value.isFinite()) AXIS_PLACEHOLDER
                    else formatBytesPerSecond(value.toLong().absoluteValue)
                }
            },
        )
        val chart = rememberCartesianChart(
            lineLayer,
            startAxis = startAxis,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CartesianChartHost(
                chart = chart,
                modelProducer = modelProducer,
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(zoomEnabled = false),
                modifier = modifier.height(160.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChartLegendSwatch(
                    color = DownloadGreen,
                    label = stringResource(R.string.home_traffic_download),
                )
                ChartLegendSwatch(
                    color = UploadOrange,
                    label = stringResource(R.string.home_traffic_upload),
                )
            }
        }
    }
}

@Composable
private fun ChartLegendSwatch(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val AXIS_PLACEHOLDER = "—"
