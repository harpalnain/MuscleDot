package com.example.fit.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.fit.domain.ChartPoint
import com.example.fit.domain.Measure
import com.example.fit.domain.SessionSeries
import com.example.fit.domain.formatDayShort
import com.example.fit.domain.formatKg
import com.example.fit.domain.movingAverage
import com.example.fit.ui.theme.LocalOverloadColors
import com.example.fit.ui.theme.OverloadTheme
import java.time.LocalDate

// The data drawn in the approved mockup (mockups/03-progress-history-profile), for
// previews in Android Studio and for the screenshot gallery test.

private val sessionLoads = listOf(720, 760, 740, 800, 780, 840, 820, 880, 860, 900, 940, 920, 1010, 980)

val previewSessionSeries: SessionSeries = run {
    val first = LocalDate.of(2026, 9, 28)
    val points = sessionLoads.mapIndexed { index, load -> ChartPoint(first.plusDays(index * 3L + index / 2), load.toDouble()) }
    SessionSeries(Measure.LOAD_KG, points, movingAverage(points.map { it.value }, 10))
}

private val weekStarts = List(8) { LocalDate.of(2026, 9, 21).plusWeeks(it.toLong()) }
private val weekLoads = listOf(14200, 15100, 13800, 15900, 16400, 15600, 17200, 9300)
private val weekCardio = listOf(30, 45, 20, 40, 50, 35, 45, 20)

private fun weekColumns(values: List<Int>, unit: (Int) -> String) = values.mapIndexed { index, value ->
    val current = index == values.lastIndex
    val day = formatDayShort(weekStarts[index])
    ColumnDatum(
        label = day,
        value = value.toDouble(),
        tooltip = (if (current) "This week so far" else "Week of $day") + " · " + unit(value),
        muted = current,
        note = if (current) "so far" else null,
    )
}

val previewLoadColumns = weekColumns(weekLoads) { formatKg(it * 10L) }
val previewCardioColumns = weekColumns(weekCardio) { "$it min" }

val previewBodyWeights = listOf(74.5, 74.0, 74.2, 73.5, 73.2, 73.0, 72.5).mapIndexed { index, kg ->
    ChartPoint(LocalDate.of(2026, 8, 10).plusWeeks(index * 2L), kg)
}

@Composable
fun ChartGallery(darkTheme: Boolean) {
    OverloadTheme(darkTheme = darkTheme) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp),
        ) {
            SessionChart(previewSessionSeries, n = 10)
            AverageNControl(n = 10, onChange = {})
            ColumnChart("Total load per week (kg)", previewLoadColumns, LocalOverloadColors.current.chartLoad)
            ColumnChart("Cardio per week (minutes)", previewCardioColumns, LocalOverloadColors.current.chartCardio)
            TrendChart("Body weight (kg)", previewBodyWeights, valueText = { formatKg(Math.round(it * 10)) })
        }
    }
}

@Preview(name = "Charts, light", widthDp = 380, heightDp = 900)
@Composable
private fun ChartsLightPreview() = ChartGallery(darkTheme = false)

@Preview(name = "Charts, dark", widthDp = 380, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChartsDarkPreview() = ChartGallery(darkTheme = true)
