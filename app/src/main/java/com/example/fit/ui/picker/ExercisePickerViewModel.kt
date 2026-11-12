package com.example.fit.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.ExerciseRepository
import com.example.fit.data.RoutineRepository
import com.example.fit.data.WorkoutRepository
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.filterExercises
import com.example.fit.ui.nav.ExercisePickerRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickerUiState(
    val query: String = "",
    val group: MuscleGroup? = null,
    val results: List<Exercise> = emptyList(),
    /** Exercises already in the routine or workout being added to; they cannot be picked again. */
    val alreadyAdded: Set<String> = emptySet(),
) {
    /** Offer to create the typed name as a custom exercise, unless an exercise is already called that. */
    val offerCreate: Boolean
        get() = query.isNotBlank() && results.none { it.name.equals(query.trim(), ignoreCase = true) }
}

interface PickerActions {
    fun setQuery(query: String)
    fun setGroup(group: MuscleGroup?)
    fun pick(exerciseId: String)
    fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind)
    fun renameCustom(id: String, name: String)
    fun deleteCustom(id: String)
    fun back()
}

class ExercisePickerViewModel(
    route: ExercisePickerRoute,
    private val exercises: ExerciseRepository,
    routines: RoutineRepository,
    workouts: WorkoutRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow<MuscleGroup?>(null)

    private val alreadyAdded: Flow<Set<String>> = when {
        route.routineId != null -> routines.observeRoutine(route.routineId).map { routine ->
            routine?.exercises.orEmpty().map { it.exercise.id }.toSet()
        }

        route.workoutId != null -> workouts.observeWorkout(route.workoutId).map { workout ->
            workout?.exercises.orEmpty().map { it.exercise.id }.toSet()
        }

        else -> flowOf(emptySet())
    }

    val state: StateFlow<PickerUiState> =
        combine(exercises.observeAll(), query, group, alreadyAdded) { all, queryNow, groupNow, added ->
            PickerUiState(queryNow, groupNow, filterExercises(all, queryNow, groupNow), added)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun setGroup(chosen: MuscleGroup?) {
        group.value = chosen
    }

    /** [onCreated] gets the new exercise's id, so it can be picked at once. */
    fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(exercises.createCustom(name, group, kind)) }
    }

    fun renameCustom(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { exercises.renameCustom(id, name) }
    }

    fun deleteCustom(id: String) {
        viewModelScope.launch { exercises.deleteCustom(id) }
    }
}
