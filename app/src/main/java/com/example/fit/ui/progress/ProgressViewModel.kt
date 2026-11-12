package com.example.fit.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.ProfileRepository
import com.example.fit.data.RoutineRepository
import com.example.fit.data.TodayHolder
import com.example.fit.data.WorkoutRepository
import com.example.fit.domain.BodyWeightEntry
import com.example.fit.domain.ChartPoint
import com.example.fit.domain.DEFAULT_AVERAGE_N
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.Routine
import com.example.fit.domain.SessionSeries
import com.example.fit.domain.WeekTotal
import com.example.fit.domain.Workout
import com.example.fit.domain.exerciseRecords
import com.example.fit.domain.filterExercises
import com.example.fit.domain.formatDayShort
import com.example.fit.domain.formatKg
import com.example.fit.domain.routineSeries
import com.example.fit.domain.sessionsOf
import com.example.fit.domain.weekStart
import com.example.fit.domain.weeklyTotals
import com.example.fit.ui.components.ColumnDatum
import com.example.fit.ui.exercise.RoutineFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class ProgressSection { EXERCISES, ROUTINES, WEEKLY, BODY }

/** An exercise with its main record in a few words: "Max 45 kg", "Most 25 reps in a set", "Longest 35 min". */
data class ExerciseProgressRow(val exercise: Exercise, val headline: String)

data class RoutineProgress(
    val routineId: Long,
    val name: String,
    val series: SessionSeries,
    /** The session with the highest value, and the most recent one. */
    val best: ChartPoint?,
    val last: ChartPoint?,
    val exercises: List<ExerciseProgressRow>,
)

data class WeeklyProgress(val weeks: List<WeekTotal>, val thisWeek: WeekTotal?, val bestWeek: WeekTotal?)

private fun headline(exercise: Exercise, saved: List<Workout>): String {
    val records = exerciseRecords(saved.sessionsOf(exercise.id))
    return when (exercise.kind) {
        ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> records.maxWeightHg?.let { "Max ${formatKg(it.value.toLong())}" }
        ExerciseKind.REPS_ONLY -> records.mostRepsSet?.let { "Most ${it.value} reps in a set" }
        ExerciseKind.CARDIO -> records.longestMinutes?.let { "Longest ${it.value} min" }
    } ?: "Not done yet"
}

/** Exercises that have at least one saved session, sorted by name. */
fun exerciseProgressRows(saved: List<Workout>): List<ExerciseProgressRow> =
    saved.flatMap { workout -> workout.exercises.filter { item -> item.sets.any { it.done } }.map { it.exercise } }
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
        .map { ExerciseProgressRow(it, headline(it, saved)) }

/** Progress of one routine, from the saved workouts that were done with it. */
fun routineProgress(routine: Routine, saved: List<Workout>, n: Int): RoutineProgress {
    val series = routineSeries(saved.filter { it.routineId == routine.id }, n)
    return RoutineProgress(
        routineId = routine.id,
        name = routine.name,
        series = series,
        best = series.points.maxByOrNull { it.value },
        last = series.points.lastOrNull(),
        exercises = routine.exercises.map { ExerciseProgressRow(it.exercise, headline(it.exercise, saved)) },
    )
}

/** The best week is the first one to reach the highest load; there is none while every week is empty. */
fun weeklyProgress(saved: List<Workout>, today: LocalDate): WeeklyProgress {
    val weeks = weeklyTotals(saved, today)
    return WeeklyProgress(
        weeks = weeks,
        thisWeek = weeks.lastOrNull { it.weekStart == weekStart(today) },
        bestWeek = weeks.filter { it.loadHg > 0 }.maxByOrNull { it.loadHg },
    )
}

/** "2 – 8 Nov", or "26 Oct – 1 Nov" when the week crosses a month. */
fun weekRangeText(weekStart: LocalDate): String {
    val end = weekStart.plusDays(6)
    val start = if (end.month == weekStart.month) weekStart.dayOfMonth.toString() else formatDayShort(weekStart)
    return "$start – ${formatDayShort(end)}"
}

/**
 * Columns for the weekly charts: load in kg, or cardio minutes. The last week is
 * the current one, still running, so it is drawn lighter and marked "so far".
 */
fun weekColumns(weeks: List<WeekTotal>, cardio: Boolean): List<ColumnDatum> = weeks.mapIndexed { index, week ->
    val current = index == weeks.lastIndex
    val day = formatDayShort(week.weekStart)
    val amount = if (cardio) "${week.cardioMinutes} min" else formatKg(week.loadHg)
    ColumnDatum(
        label = day,
        value = if (cardio) week.cardioMinutes.toDouble() else week.loadHg / 10.0,
        tooltip = (if (current) "This week so far" else "Week of $day") + " · " + amount,
        muted = current,
        note = if (current) "so far" else null,
    )
}

data class ProgressUiState(
    val section: ProgressSection = ProgressSection.EXERCISES,
    val query: String = "",
    val exerciseRows: List<ExerciseProgressRow> = emptyList(),
    val routineOptions: List<RoutineFilter> = emptyList(),
    val routine: RoutineProgress? = null,
    val weekly: WeeklyProgress = WeeklyProgress(emptyList(), null, null),
    val bodyWeights: List<BodyWeightEntry> = emptyList(),
    val n: Int = DEFAULT_AVERAGE_N,
)

interface ProgressActions {
    fun setSection(section: ProgressSection)
    fun setQuery(query: String)
    fun selectRoutine(routineId: Long)
    fun changeN(n: Int)
    fun showExercise(exerciseId: String)
}

class ProgressViewModel(
    workouts: WorkoutRepository,
    routines: RoutineRepository,
    private val profile: ProfileRepository,
    today: TodayHolder,
) : ViewModel() {
    private val section = MutableStateFlow(ProgressSection.EXERCISES)
    private val query = MutableStateFlow("")
    private val selectedRoutineId = MutableStateFlow<Long?>(null)

    private data class Choices(val section: ProgressSection, val query: String, val routineId: Long?)

    private data class Stored(
        val saved: List<Workout>,
        val routines: List<Routine>,
        val bodyWeights: List<BodyWeightEntry>,
        val n: Int,
        val today: LocalDate,
    )

    /** Null until the saved workouts have been read. */
    val state: StateFlow<ProgressUiState?> = combine(
        combine(workouts.observeSaved(), routines.observeRoutines(), profile.observeBodyWeights(), profile.observeAverageN(), today.date, ::Stored),
        combine(section, query, selectedRoutineId, ::Choices),
    ) { stored, choices ->
        val rows = exerciseProgressRows(stored.saved)
        val matching = filterExercises(rows.map { it.exercise }, choices.query, group = null).map { it.id }.toSet()
        // Until the user picks one, show the first routine that has something to show.
        val routine = stored.routines.firstOrNull { it.id == choices.routineId }
            ?: stored.routines.firstOrNull { routine -> stored.saved.any { it.routineId == routine.id } }
            ?: stored.routines.firstOrNull()
        ProgressUiState(
            section = choices.section,
            query = choices.query,
            exerciseRows = rows.filter { it.exercise.id in matching },
            routineOptions = stored.routines.map { RoutineFilter(it.id, it.name) },
            routine = routine?.let { routineProgress(it, stored.saved, stored.n) },
            weekly = weeklyProgress(stored.saved, stored.today),
            bodyWeights = stored.bodyWeights,
            n = stored.n,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSection(chosen: ProgressSection) {
        section.value = chosen
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun selectRoutine(routineId: Long) {
        selectedRoutineId.value = routineId
    }

    /** N is one setting shared by every chart (spec 5.10). */
    fun changeN(n: Int) {
        viewModelScope.launch { profile.setAverageN(n) }
    }
}
