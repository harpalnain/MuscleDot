package com.example.fit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.WorkoutRepository
import com.example.fit.domain.SetField
import com.example.fit.domain.Workout
import com.example.fit.domain.tickedOnly
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Edits to one saved workout. Sets are addressed by their own stored position. */
interface WorkoutDetailActions {
    fun step(exerciseId: String, position: Int, field: SetField, steps: Int)
    fun removeSet(exerciseId: String, position: Int)
    fun addSet(exerciseId: String)
    fun deleteWorkout()
    fun back()
}

sealed interface WorkoutDetailState {
    data object Loading : WorkoutDetailState

    /** [workout] holds its ticked sets only. */
    data class Showing(val workout: Workout) : WorkoutDetailState

    /** Deleted, or nothing ticked is left: the page closes. */
    data object Gone : WorkoutDetailState
}

/**
 * One saved workout, open for editing (spec 5.11). Edits change the stored workout
 * only, never the routine's starting values (spec 5.7); charts and records follow
 * because they are worked out from the saved workouts.
 */
class WorkoutDetailViewModel(private val workoutId: Long, private val workouts: WorkoutRepository) : ViewModel() {
    val state: StateFlow<WorkoutDetailState> = workouts.observeWorkout(workoutId)
        .map { stored ->
            val ticked = stored?.tickedOnly()
            if (ticked == null || ticked.exercises.isEmpty()) WorkoutDetailState.Gone else WorkoutDetailState.Showing(ticked)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailState.Loading)

    fun step(exerciseId: String, position: Int, field: SetField, steps: Int) {
        viewModelScope.launch { workouts.adjustSet(workoutId, exerciseId, position, field, steps) }
    }

    /** Removing the last ticked set deletes the workout. */
    fun removeSet(exerciseId: String, position: Int) {
        viewModelScope.launch { workouts.removeSavedSet(workoutId, exerciseId, position) }
    }

    fun addSet(exerciseId: String) {
        viewModelScope.launch { workouts.addSet(workoutId, exerciseId, done = true) }
    }

    fun deleteWorkout() {
        viewModelScope.launch { workouts.deleteWorkout(workoutId) }
    }
}
