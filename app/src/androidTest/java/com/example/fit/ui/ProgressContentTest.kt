package com.example.fit.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.fit.domain.BestSet
import com.example.fit.domain.ChartPoint
import com.example.fit.domain.Dated
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.ExerciseRecords
import com.example.fit.domain.Measure
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.SessionSeries
import com.example.fit.domain.WeekTotal
import com.example.fit.ui.exercise.ExerciseDetailContent
import com.example.fit.ui.exercise.ExerciseDetailUiState
import com.example.fit.ui.exercise.RoutineFilter
import com.example.fit.ui.progress.ExerciseProgressRow
import com.example.fit.ui.progress.ProgressActions
import com.example.fit.ui.progress.ProgressContent
import com.example.fit.ui.progress.ProgressSection
import com.example.fit.ui.progress.ProgressUiState
import com.example.fit.ui.progress.RoutineProgress
import com.example.fit.ui.progress.WeeklyProgress
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ProgressContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : ProgressActions {
        val calls = mutableListOf<String>()
        override fun setSection(section: ProgressSection) {
            calls += "section $section"
        }

        override fun setQuery(query: String) {
            calls += "query $query"
        }

        override fun selectRoutine(routineId: Long) {
            calls += "routine $routineId"
        }

        override fun changeN(n: Int) {
            calls += "n $n"
        }

        override fun showExercise(exerciseId: String) {
            calls += "show $exerciseId"
        }
    }

    private val actions = Recorder()

    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    private val bench = Exercise(
        "bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS,
        imageBase = "exercises/bench-press",
    )
    private val series = SessionSeries(
        Measure.LOAD_KG,
        listOf(ChartPoint(d(2), 740.0), ChartPoint(d(9), 1010.0)),
        listOf(740.0, 875.0),
    )
    private val rows = listOf(ExerciseProgressRow(bench, "Max 45 kg"))

    @Test
    fun emptyHistory_showsAHint() {
        rule.setContent { OverloadTheme { ProgressContent(ProgressUiState(), actions) } }
        rule.onNodeWithText("No workouts yet. Finish a workout to see your progress here.").assertIsDisplayed()
    }

    @Test
    fun exercisesSectionListsRowsAndOpensOne() {
        rule.setContent { OverloadTheme { ProgressContent(ProgressUiState(exerciseRows = rows), actions) } }
        rule.onNodeWithText("Max 45 kg").assertIsDisplayed()
        rule.onNodeWithText("Bench Press").performClick()
        rule.onNodeWithText("Weekly").performClick()
        assertEquals(listOf("show bench-press", "section WEEKLY"), actions.calls)
    }

    @Test
    fun routinesSectionShowsTilesAndChart() {
        val state = ProgressUiState(
            section = ProgressSection.ROUTINES,
            exerciseRows = rows,
            routineOptions = listOf(RoutineFilter(1, "Chest")),
            routine = RoutineProgress(
                1, "Chest", series,
                best = ChartPoint(d(9), 1010.0), last = ChartPoint(d(9), 1010.0), exercises = rows,
            ),
        )
        rule.setContent { OverloadTheme { ProgressContent(state, actions) } }
        rule.onNodeWithText("Best session").assertIsDisplayed()
        rule.onNodeWithText("Last session").assertIsDisplayed()
        rule.onAllNodesWithText("1,010 kg").assertCountEquals(2)
        rule.onNodeWithText("Load per session (kg)").assertExists()
        rule.onNodeWithContentDescription("More sessions").performScrollTo().performClick()
        assertEquals(listOf("n 11"), actions.calls)
    }

    @Test
    fun weeklySectionShowsBothCharts() {
        val weeks = listOf(WeekTotal(d(2), 14100, 45, 4), WeekTotal(d(9), 5000, 20, 1))
        val state = ProgressUiState(section = ProgressSection.WEEKLY, weekly = WeeklyProgress(weeks, weeks[1], weeks[0]))
        rule.setContent { OverloadTheme { ProgressContent(state, actions) } }
        rule.onNodeWithText("This week so far").assertIsDisplayed()
        rule.onNodeWithText("500 kg").assertIsDisplayed()
        rule.onNodeWithText("Best week").assertIsDisplayed()
        rule.onNodeWithText("2 – 8 Nov").assertIsDisplayed()
        rule.onNodeWithText("Total load per week (kg)").assertExists()
        rule.onNodeWithText("Cardio per week (minutes)").assertExists()
    }

    @Test
    fun bodySectionWithoutWeights_saysSo() {
        rule.setContent { OverloadTheme { ProgressContent(ProgressUiState(section = ProgressSection.BODY), actions) } }
        rule.onNodeWithText("Body weight (kg)").assertIsDisplayed()
        rule.onNodeWithText("No body weight entered yet").assertIsDisplayed()
        rule.onAllNodesWithText("No workouts yet").assertCountEquals(0)
    }

    @Test
    fun weeklyTilesAreOneItemEachAndEquallyTall() {
        val weeks = listOf(WeekTotal(d(9), 0, 20, 1))
        val state = ProgressUiState(section = ProgressSection.WEEKLY, weekly = WeeklyProgress(weeks, weeks[0], bestWeek = null))
        rule.setContent { OverloadTheme { ProgressContent(state, actions) } }
        // A tile is read as one item: its label, value and note together.
        val thisWeek = rule.onNode(hasText("This week so far") and hasText("0 kg") and hasText("1 workout"))
            .getUnclippedBoundsInRoot()
        val best = rule.onNode(hasText("Best week") and hasText("—")).getUnclippedBoundsInRoot()
        assertEquals(thisWeek.height, best.height)
    }

    private val detailState = ExerciseDetailUiState(
        bench,
        ExerciseRecords(Dated(450, d(5)), BestSet(4000, 400, 10, d(2)), Dated(10100L, d(5))),
        listOf(RoutineFilter(1, "Chest"), RoutineFilter(2, "Full Body")),
        selectedRoutineId = null,
        series = series,
        n = 10,
    )

    @Test
    fun exerciseDetailPictureHasTheShapeOfThePhotos() {
        rule.setContent {
            OverloadTheme { ExerciseDetailContent(detailState, onSelectRoutine = {}, onChangeN = {}, onBack = {}) }
        }
        // The photos are 3:2; a frame of another shape would leave empty bands beside them.
        val picture = rule.onNodeWithTag("exercise-image").getUnclippedBoundsInRoot()
        assertEquals(1.5f, picture.width / picture.height, 0.02f)
    }

    @Test
    fun exerciseDetailShowsRecordsFiltersAndChart() {
        val state = detailState
        val picked = mutableListOf<Long?>()
        rule.setContent {
            OverloadTheme { ExerciseDetailContent(state, onSelectRoutine = { picked += it }, onChangeN = {}, onBack = {}) }
        }
        rule.onNodeWithText("Max weight").assertIsDisplayed()
        rule.onNodeWithText("45 kg").assertIsDisplayed()
        rule.onNodeWithText("Best set").assertIsDisplayed()
        rule.onNodeWithText("400 kg").assertIsDisplayed()
        rule.onNodeWithText("40 × 10").assertIsDisplayed()
        rule.onNodeWithText("Best session").assertIsDisplayed()
        rule.onNodeWithText("1,010 kg").assertIsDisplayed()
        rule.onNodeWithText("Full Body").performClick()
        rule.onNodeWithText("All routines").performClick()
        assertEquals(listOf<Long?>(2, null), picked)
    }
}
