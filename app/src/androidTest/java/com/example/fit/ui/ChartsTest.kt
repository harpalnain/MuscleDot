package com.example.fit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.fit.domain.ChartPoint
import com.example.fit.domain.Measure
import com.example.fit.domain.SessionSeries
import com.example.fit.ui.components.AverageNControl
import com.example.fit.ui.components.ColumnChart
import com.example.fit.ui.components.ColumnDatum
import com.example.fit.ui.components.SessionChart
import com.example.fit.ui.components.TrendChart
import com.example.fit.ui.theme.LocalOverloadColors
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ChartsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    private val series = SessionSeries(
        Measure.LOAD_KG,
        listOf(ChartPoint(d(2), 740.0), ChartPoint(d(5), 1010.0), ChartPoint(d(9), 965.0)),
        listOf(740.0, 875.0, 987.5),
    )

    @Test
    fun emptyHistory_showsAMessageInsteadOfAPlot() {
        rule.setContent {
            OverloadTheme {
                Column {
                    SessionChart(SessionSeries(Measure.LOAD_KG, emptyList(), emptyList()), n = 10)
                    ColumnChart("Total load per week (kg)", emptyList(), Color.Blue)
                    TrendChart("Body weight (kg)", emptyList(), valueText = { "$it" })
                }
            }
        }
        rule.onAllNodesWithText("No workouts yet").assertCountEquals(3)
        rule.onNodeWithTag("chart-plot").assertDoesNotExist()
    }

    @Test
    fun sessionChartShowsTitleLegendAndSummary() {
        rule.setContent { OverloadTheme { SessionChart(series, n = 10) } }
        rule.onNodeWithText("Load per session (kg)").assertIsDisplayed()
        rule.onNodeWithText("Each session").assertIsDisplayed()
        rule.onNodeWithText("Average of last 10").assertIsDisplayed()
        rule.onNodeWithTag("chart-plot").assert(hasContentDescription("3 sessions, latest 965 kg, average 987.5 kg"))
    }

    @Test
    fun tappingTheNewestMarkShowsItsReadout() {
        rule.setContent { OverloadTheme { SessionChart(series, n = 10) } }
        rule.onNodeWithTag("chart-plot").performTouchInput { click(centerRight) }
        rule.onNodeWithText("9 Nov · 965 kg").assertIsDisplayed()
    }

    @Test
    fun columnAndTrendChartsShowReadouts() {
        val columns = listOf(
            ColumnDatum("2 Nov", 1410.0, "Week of 2 Nov · 1,410 kg"),
            ColumnDatum("9 Nov", 500.0, "This week so far · 500 kg", muted = true, note = "so far"),
        )
        rule.setContent {
            OverloadTheme {
                Column {
                    ColumnChart("Total load per week (kg)", columns, LocalOverloadColors.current.chartLoad, Modifier.testTag("columns"))
                    TrendChart(
                        "Body weight (kg)",
                        listOf(ChartPoint(d(9), 72.5), ChartPoint(d(10), 72.3)),
                        valueText = { "$it kg" },
                        Modifier.testTag("trend"),
                    )
                }
            }
        }
        rule.onNode(hasTestTag("chart-plot") and hasAnyAncestor(hasTestTag("columns"))).performTouchInput { click(centerRight) }
        rule.onNodeWithText("This week so far · 500 kg").assertIsDisplayed()
        rule.onNode(hasTestTag("chart-plot") and hasAnyAncestor(hasTestTag("trend"))).performTouchInput { click(centerRight) }
        rule.onNodeWithText("10 Nov · 72.3 kg").assertIsDisplayed()
    }

    @Test
    fun averageControlStaysBetweenTwoAndTwenty() {
        val changes = mutableListOf<Int>()
        var n by mutableStateOf(19)
        rule.setContent { OverloadTheme { AverageNControl(n, onChange = { changes += it; n = it }) } }
        repeat(2) { rule.onNodeWithContentDescription("More sessions").performClick() }
        rule.onNodeWithContentDescription("Fewer sessions").performClick()
        assertEquals(listOf(20, 19), changes)
    }
}
