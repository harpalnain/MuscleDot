package com.example.fit.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.ExerciseRepository
import com.example.fit.data.ProfileRepository
import com.example.fit.data.RoutineRepository
import com.example.fit.data.WorkoutRepository
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseRecords
import com.example.fit.domain.Routine
import com.example.fit.domain.SessionSeries
import com.example.fit.domain.Workout
import com.example.fit.domain.exerciseRecords
import com.example.fit.domain.exerciseSeries
import com.example.fit.domain.sessionsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A routine that can be chosen to narrow a chart to its sessions. */
data class RoutineFilter(val routineId: Long, val name: String)

data class ExerciseDetailUiState(
    val exercise: Exercise,
    /** Over all routines; the filter does not change them. */
    val records: ExerciseRecords,
    /** Existing routines that have at least one session of this exercise, in routine order. */
    val filters: List<RoutineFilter>,
    val selectedRoutineId: Long?,
    val series: SessionSeries,
    val n: Int,
)

fun buildExerciseDetailState(
    exercise: Exercise,
    saved: List<Workout>,
    routines: List<Routine>,
    selectedRoutineId: Long?,
    n: Int,
): ExerciseDetailUiState {
    val sessions = saved.sessionsOf(exercise.id)
    val filters = routines
        .filter { routine -> sessions.any { it.routineId == routine.id } }
        .map { RoutineFilter(it.id, it.name) }
    val selected = selectedRoutineId?.takeIf { id -> filters.any { it.routineId == id } }
    return ExerciseDetailUiState(
        exercise = exercise,
        records = exerciseRecords(sessions),
        filters = filters,
        selectedRoutineId = selected,
        series = exerciseSeries(exercise.kind, sessions, selected, n),
        n = n,
    )
}

class ExerciseDetailViewModel(
    exerciseId: String,
    exercises: ExerciseRepository,
    workouts: WorkoutRepository,
    routines: RoutineRepository,
    private val profile: ProfileRepository,
) : ViewModel() {
    private val selectedRoutineId = MutableStateFlow<Long?>(null)

    /** Null while loading, and if the exercise does not exist. */
    val state: StateFlow<ExerciseDetailUiState?> = combine(
        flow { emit(exercises.get(exerciseId)) },
        workouts.observeSaved(),
        routines.observeRoutines(),
        selectedRoutineId,
        profile.observeAverageN(),
    ) { exercise, saved, routineList, selected, n ->
        exercise?.let { buildExerciseDetailState(it, saved, routineList, selected, n) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectRoutine(routineId: Long?) {
        selectedRoutineId.value = routineId
    }

    /** N is one setting shared by every chart (spec 5.10). */
    fun changeN(n: Int) {
        viewModelScope.launch { profile.setAverageN(n) }
    }
}
