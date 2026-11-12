package com.example.fit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fit.R
import com.example.fit.domain.ChartPoint
import com.example.fit.domain.MAX_AVERAGE_N
import com.example.fit.domain.MIN_AVERAGE_N
import com.example.fit.domain.Measure
import com.example.fit.domain.SessionSeries
import com.example.fit.domain.formatAxis
import com.example.fit.domain.formatDayShort
import com.example.fit.domain.formatNumber
import com.example.fit.domain.niceTicks
import com.example.fit.ui.theme.LocalOverloadColors

// Charts (spec 5.10): one y-scale each, thin marks, quiet solid grid lines, values in
// ink colours and never in the series colour. Tapping a mark shows its exact value in
// the card's header. When marks do not fit they keep a minimum spacing and the plot
// scrolls sideways, starting at the newest mark; the y-axis stays put.

private val GutterWidth = 44.dp
private val AxisBand = 18.dp // date labels under the plot
private val TopPadding = 16.dp // room for a label above the tallest mark
private val EndLabelSpace = 46.dp // room for the value written after the last mark
private val SessionSpacing = 18.dp
private val ColumnSpacing = 34.dp
private val MaxColumnWidth = 24.dp
private const val MUTED_ALPHA = 0.45f
private const val WASH_ALPHA = 0.10f

/** What the drawing code needs to place marks. */
private class PlotGeometry(
    val xs: List<Float>,
    val scale: PlotScale,
    val top: Float,
    val bottom: Float,
    val measurer: TextMeasurer,
    val labelStyle: TextStyle,
    val valueStyle: TextStyle,
)

@Composable
private fun ChartCard(
    title: String,
    readout: String?,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
    emptyText: String = stringResource(R.string.chart_empty),
    legend: (@Composable () -> Unit)? = null,
    plot: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LocalOverloadColors.current.chartSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (readout != null) {
                Text(
                    text = readout,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (isEmpty) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 20.dp),
            )
        } else {
            if (legend != null) Box(Modifier.padding(start = 8.dp, top = 2.dp)) { legend() }
            plot()
        }
    }
}

/**
 * The y-axis gutter and the scrolling plot beside it. [draw] places the marks;
 * grid lines and the date labels under the first, middle and last mark are drawn here.
 */
@Composable
private fun PlotArea(
    count: Int,
    ticks: List<Double>,
    minSpacing: Dp,
    inset: Dp,
    endSpace: Dp,
    height: Dp,
    description: String,
    xLabel: (Int) -> String,
    onSelect: (Int) -> Unit,
    draw: DrawScope.(PlotGeometry) -> Unit,
) {
    val density = LocalDensity.current
    val colors = LocalOverloadColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.chartLabel)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    val scale = remember(ticks) { PlotScale(ticks) }

    Row(Modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.width(GutterWidth).fillMaxHeight()) {
            val top = TopPadding.toPx()
            val bottom = size.height - AxisBand.toPx()
            for (tick in ticks) {
                val text = measurer.measure(formatAxis(tick), labelStyle)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(size.width - text.size.width - 6.dp.toPx(), scale.y(tick, top, bottom) - text.size.height / 2f),
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewport = constraints.maxWidth.toFloat()
            val (contentPx, xs) = remember(count, viewport, density) {
                with(density) {
                    val content = contentWidth(count, viewport - endSpace.toPx(), minSpacing.toPx(), inset.toPx()) + endSpace.toPx()
                    content to markPositions(count, content - endSpace.toPx(), minSpacing.toPx(), inset.toPx())
                }
            }
            // Start at the newest mark, and return to it when marks are added.
            val scroll = rememberScrollState(initial = Int.MAX_VALUE)
            LaunchedEffect(count) { scroll.scrollTo(scroll.maxValue) }

            Box(Modifier.horizontalScroll(scroll)) {
                Canvas(
                    Modifier
                        .width(with(density) { contentPx.toDp() })
                        .fillMaxHeight()
                        .testTag("chart-plot")
                        .semantics { contentDescription = description }
                        .pointerInput(xs) {
                            detectTapGestures { tap -> nearestIndex(xs, tap.x)?.let(onSelect) }
                        },
                ) {
                    val top = TopPadding.toPx()
                    val bottom = size.height - AxisBand.toPx()
                    ticks.forEachIndexed { index, tick ->
                        val y = scale.y(tick, top, bottom)
                        drawLine(
                            color = if (index == 0) colors.chartBaseline else colors.chartGrid,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    for (index in setOf(0, (count - 1) / 2, count - 1)) {
                        if (index !in xs.indices) continue
                        val text = measurer.measure(xLabel(index), labelStyle)
                        val left = (xs[index] - text.size.width / 2f).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                        drawText(text, topLeft = Offset(left, bottom + 4.dp.toPx()))
                    }
                    draw(PlotGeometry(xs, scale, top, bottom, measurer, labelStyle, valueStyle))
                }
            }
        }
    }
}

private fun DrawScope.polyline(points: List<Offset>, color: Color, width: Dp) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
    }
    drawPath(path, color, style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A dot with a ring in the surface colour, so it stays legible where it crosses a line. */
private fun DrawScope.dot(center: Offset, color: Color, surface: Color, radius: Dp) {
    drawCircle(surface, radius.toPx() + 1.5.dp.toPx(), center)
    drawCircle(color, radius.toPx(), center)
}

@Composable
private fun LegendKey(text: String, key: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        key()
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun measureTitle(measure: Measure): String = stringResource(
    when (measure) {
        Measure.LOAD_KG -> R.string.chart_title_load
        Measure.REPS -> R.string.chart_title_reps
        Measure.MINUTES -> R.string.chart_title_minutes
    },
)

/**
 * One value per session as grey dots, with the moving average over the last [n]
 * sessions as a blue line whose latest value is written at its end.
 */
@Composable
fun SessionChart(series: SessionSeries, n: Int, modifier: Modifier = Modifier) {
    val colors = LocalOverloadColors.current
    val points = series.points
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    val readout = selected?.let { points.getOrNull(it) }?.let {
        "${formatDayShort(it.date)} · ${formatMeasure(series.measure, it.value)}"
    }

    ChartCard(
        title = measureTitle(series.measure),
        readout = readout,
        isEmpty = points.isEmpty(),
        modifier = modifier,
        legend = {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendKey(stringResource(R.string.chart_legend_session)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.chartSession))
                }
                LegendKey(stringResource(R.string.chart_legend_average, n)) {
                    Box(Modifier.width(16.dp).height(2.dp).background(colors.chartLoad))
                }
            }
        },
    ) {
        val values = points.map { it.value } + series.average
        val ticks = remember(series) { niceTicks(values.min(), values.max()) }
        val description = pluralStringResource(
            R.plurals.chart_sessions_description,
            points.size,
            points.size,
            formatMeasure(series.measure, points.last().value),
            formatMeasure(series.measure, series.average.last()),
        )
        PlotArea(
            count = points.size,
            ticks = ticks,
            minSpacing = SessionSpacing,
            inset = 10.dp,
            endSpace = EndLabelSpace,
            height = 170.dp,
            description = description,
            xLabel = { formatDayShort(points[it].date) },
            onSelect = { selected = it },
        ) { plot ->
            val sessions = points.mapIndexed { i, p -> Offset(plot.xs[i], plot.scale.y(p.value, plot.top, plot.bottom)) }
            val average = series.average.mapIndexed { i, v -> Offset(plot.xs[i], plot.scale.y(v, plot.top, plot.bottom)) }
            polyline(sessions, colors.chartBaseline, 1.5.dp)
            sessions.forEachIndexed { index, point ->
                dot(point, colors.chartSession, colors.chartSurface, if (index == selected) 5.dp else 3.5.dp)
            }
            polyline(average, colors.chartLoad, 2.dp)
            val end = average.last()
            dot(end, colors.chartLoad, colors.chartSurface, 4.dp)
            val label = plot.measurer.measure(formatNumber(series.average.last()), plot.valueStyle)
            drawText(label, topLeft = Offset(end.x + 9.dp.toPx(), end.y - label.size.height / 2f))
        }
    }
}

/**
 * One column of a [ColumnChart]. [label] goes under the column, [tooltip] is shown
 * when it is tapped. A [muted] column is drawn lighter with its [note] above it,
 * for a period that is still running.
 */
data class ColumnDatum(
    val label: String,
    val value: Double,
    val tooltip: String,
    val muted: Boolean = false,
    val note: String? = null,
)

/** Thin columns growing from a zero baseline, for totals per week. */
@Composable
fun ColumnChart(title: String, columns: List<ColumnDatum>, color: Color, modifier: Modifier = Modifier) {
    var selected by remember(columns) { mutableStateOf<Int?>(null) }
    ChartCard(
        title = title,
        readout = selected?.let { columns.getOrNull(it)?.tooltip },
        isEmpty = columns.isEmpty(),
        modifier = modifier,
    ) {
        val highest = columns.maxOf { it.value }
        val ticks = remember(columns) { niceTicks(0.0, highest.coerceAtLeast(1.0)) }
        val tallest = columns.indexOfFirst { it.value == highest }
        PlotArea(
            count = columns.size,
            ticks = ticks,
            minSpacing = ColumnSpacing,
            inset = 20.dp,
            endSpace = 0.dp,
            height = 150.dp,
            description = stringResource(R.string.chart_columns_description, title, columns.size),
            xLabel = { columns[it].label },
            onSelect = { selected = it },
        ) { plot ->
            val spacing = if (plot.xs.size > 1) plot.xs[1] - plot.xs[0] else size.width
            val width = minOf(MaxColumnWidth.toPx(), spacing * 0.6f)
            val corner = CornerRadius(4.dp.toPx())
            columns.forEachIndexed { index, column ->
                val top = plot.scale.y(column.value, plot.top, plot.bottom)
                val x = plot.xs[index]
                if (column.value > 0) {
                    // Rounded at the data end, square at the baseline.
                    val shape = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(x - width / 2, top, x + width / 2, plot.bottom),
                                topLeft = corner,
                                topRight = corner,
                            ),
                        )
                    }
                    drawPath(shape, color.copy(alpha = if (column.muted) MUTED_ALPHA else 1f))
                }
                val above = when {
                    column.note != null -> plot.measurer.measure(column.note, plot.labelStyle)
                    index == tallest && column.value > 0 -> plot.measurer.measure(formatAxis(column.value), plot.valueStyle)
                    else -> null
                }
                if (above != null) {
                    val left = (x - above.size.width / 2f).coerceIn(0f, (size.width - above.size.width).coerceAtLeast(0f))
                    drawText(above, topLeft = Offset(left, top - above.size.height - 2.dp.toPx()))
                }
            }
        }
    }
}

/** A single line over time with a light wash under it, for body weight. [emptyText] is shown while there are no points. */
@Composable
fun TrendChart(
    title: String,
    points: List<ChartPoint>,
    valueText: (Double) -> String,
    modifier: Modifier = Modifier,
    emptyText: String = stringResource(R.string.chart_empty),
) {
    val colors = LocalOverloadColors.current
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val readout = selected?.let { points.getOrNull(it) }?.let { "${formatDayShort(it.date)} · ${valueText(it.value)}" }

    ChartCard(title = title, readout = readout, isEmpty = points.isEmpty(), modifier = modifier, emptyText = emptyText) {
        val ticks = remember(points) {
            val low = points.minOf { it.value }
            val high = points.maxOf { it.value }
            // A change of a few tenths would otherwise get ticks too fine to label.
            val pad = ((MIN_TREND_SPAN - (high - low)) / 2).coerceAtLeast(0.0)
            niceTicks(low - pad, high + pad)
        }
        PlotArea(
            count = points.size,
            ticks = ticks,
            minSpacing = SessionSpacing,
            inset = 10.dp,
            endSpace = EndLabelSpace,
            height = 140.dp,
            description = stringResource(R.string.chart_trend_description, title, points.size, valueText(points.last().value)),
            xLabel = { formatDayShort(points[it].date) },
            onSelect = { selected = it },
        ) { plot ->
            val line = points.mapIndexed { i, p -> Offset(plot.xs[i], plot.scale.y(p.value, plot.top, plot.bottom)) }
            if (line.size > 1) {
                val wash = Path().apply {
                    moveTo(line.first().x, plot.bottom)
                    for (point in line) lineTo(point.x, point.y)
                    lineTo(line.last().x, plot.bottom)
                    close()
                }
                drawPath(wash, colors.chartLoad.copy(alpha = WASH_ALPHA))
            }
            polyline(line, colors.chartLoad, 2.dp)
            selected?.let { line.getOrNull(it) }?.let { dot(it, colors.chartLoad, colors.chartSurface, 3.5.dp) }
            val end = line.last()
            dot(end, colors.chartLoad, colors.chartSurface, 4.dp)
            val label = plot.measurer.measure(formatNumber(points.last().value), plot.valueStyle)
            drawText(label, topLeft = Offset(end.x + 9.dp.toPx(), end.y - label.size.height / 2f))
        }
    }
}

private const val MIN_TREND_SPAN = 1.0

/** "Average of last [−] N [+] sessions": the N of the moving average, from 2 to 20. */
@Composable
fun AverageNControl(n: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.average_of_last),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        NumberStepper(
            text = n.toString(),
            decreaseDescription = stringResource(R.string.average_fewer),
            increaseDescription = stringResource(R.string.average_more),
            onStep = { onChange(n + it) },
            canDecrease = n > MIN_AVERAGE_N,
            canIncrease = n < MAX_AVERAGE_N,
            modifier = Modifier.width(128.dp),
        )
        Text(
            text = stringResource(R.string.average_sessions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
