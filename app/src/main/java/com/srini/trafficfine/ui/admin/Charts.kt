package com.srini.trafficfine.ui.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srini.trafficfine.domain.model.DailyPoint
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** One labelled value in a chart legend. */
data class ChartSlice(val label: String, val value: Float, val color: Color)

private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Stacked daily bar chart: the full bar is the amount issued, the darker inner bar the amount
 * collected. Hand-drawn with Canvas so it needs no charting dependency.
 */
@Composable
fun DailyBarChart(
    points: List<DailyPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    issuedColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    collectedColor: Color = MaterialTheme.colorScheme.primary,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxAmount = max(points.maxOfOrNull { it.amount } ?: 0L, 1L).toFloat()
    val total = points.sumOf { it.amount }
    // Show at most ~7 x-axis labels regardless of the window length.
    val labelEvery = max(1, (points.size + 6) / 7)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Daily fines chart, total ₹$total over ${points.size} days" },
    ) {
        if (points.isEmpty()) return@Canvas
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slot = size.width / points.size
        val barWidth = (slot * 0.7f).coerceAtLeast(1f)

        for (i in 1..3) {
            val y = chartHeight * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 2f)

        points.forEachIndexed { index, point ->
            val left = index * slot + (slot - barWidth) / 2f
            val issuedH = chartHeight * (point.amount / maxAmount)
            val collectedH = chartHeight * (point.collected / maxAmount)
            val radius = CornerRadius(barWidth / 4f, barWidth / 4f)
            if (issuedH > 0f) {
                drawRoundRect(issuedColor, Offset(left, chartHeight - issuedH), Size(barWidth, issuedH), radius)
            }
            if (collectedH > 0f) {
                drawRoundRect(collectedColor, Offset(left, chartHeight - collectedH), Size(barWidth, collectedH), radius)
            }
            if (index % labelEvery == 0 || index == points.lastIndex) {
                val layout = measurer.measure(shortDay.format(point.date), labelStyle)
                val x = (index * slot + slot / 2f - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, chartHeight + 2.dp.toPx()))
            }
        }
    }
}

/** Donut chart with a centred caption, e.g. challans by status. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerLabel: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 140.dp,
    thickness: Dp = 22.dp,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .size(diameter)
            .semantics { contentDescription = slices.joinToString { "${it.label} ${it.value.toInt()}" } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter)) {
            val stroke = thickness.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            if (total <= 0f) {
                drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            slices.filter { it.value > 0f }.forEach { slice ->
                val sweep = 360f * slice.value / total
                drawArc(slice.color, start, sweep - 1f, false, topLeft, arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Text(centerLabel, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** Horizontal bars with labels — used for offence categories and officer leaderboards. */
@Composable
fun HorizontalBars(
    items: List<ChartSlice>,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
) {
    val maxValue = max(items.maxOfOrNull { it.value } ?: 0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(item.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(valueFormatter(item.value), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val radius = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(track, cornerRadius = radius)
                    drawRoundRect(item.color, size = Size(size.width * item.value / maxValue, size.height), cornerRadius = radius)
                }
            }
        }
    }
}

@Composable
fun Legend(slices: List<ChartSlice>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(slice.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("${slice.label} · ${slice.value.toInt()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
