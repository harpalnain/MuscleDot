package com.example.fit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.WorkoutRepository
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.Workout
import com.example.fit.domain.cardioMinutes
import com.example.fit.domain.doneSets
import com.example.fit.domain.formatKg
import com.example.fit.domain.loadHg
import com.example.fit.domain.recordCounts
import com.example.fit.domain.totalReps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/**
 * One saved workout in the history list. [setCount] leaves out cardio, which is
 * measured in minutes and has no sets. [records] is how many records it broke.
 */
data class HistoryRow(
    val workoutId: Long,
    val date: LocalDate,
    val routineName: String,
    val exerciseCount: Int,
    val setCount: Int,
    val metric: String,
    val records: Int,
)

data class HistoryMonth(val month: YearMonth, val rows: List<HistoryRow>)

/** Load ("6,240 kg") when above zero, else cardio minutes ("35 min"), else reps ("120 reps"). */
private fun metricOf(workout: Workout): String {
    val load = workout.loadHg()
    val minutes = workout.cardioMinutes()
    return when {
        load > 0 -> formatKg(load)
        minutes > 0 -> "$minutes min"
        else -> "${workout.exercises.sumOf { it.totalReps() }} reps"
    }
}

/** The saved workouts as a list: newest month first, newest day first. */
fun buildHistory(saved: List<Workout>): List<HistoryMonth> {
    val records = recordCounts(saved)
    return saved.sortedByDescending { it.date }
        .groupBy { YearMonth.from(it.date) }
        .map { (month, workouts) ->
            HistoryMonth(
                month,
                workouts.map { workout ->
                    val done = workout.exercises.filter { it.doneSets().isNotEmpty() }
                    HistoryRow(
                        workoutId = workout.id,
                        date = workout.date,
                        routineName = workout.routineName,
                        exerciseCount = done.size,
                        setCount = done.filter { it.exercise.kind != ExerciseKind.CARDIO }.sumOf { it.doneSets().size },
                        metric = metricOf(workout),
                        records = records[workout.id] ?: 0,
                    )
                },
            )
        }
}

class HistoryViewModel(workouts: WorkoutRepository) : ViewModel() {
    /** Null until the saved workouts have been read. */
    val months: StateFlow<List<HistoryMonth>?> = workouts.observeSaved()
        .map<List<Workout>, List<HistoryMonth>?> { buildHistory(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
