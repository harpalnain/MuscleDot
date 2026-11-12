package com.example.fit.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.RoutineRepository
import com.example.fit.domain.RoutineExercise
import com.example.fit.domain.SetField
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class RoutineEditorUiState(
    val routineId: Long,
    val name: String = "",
    val exercises: List<RoutineExercise> = emptyList(),
)

interface RoutineEditorActions {
    fun rename(name: String)
    fun step(exerciseId: String, position: Int, field: SetField, steps: Int)
    fun addSet(exerciseId: String)
    fun removeSet(exerciseId: String, position: Int)
    fun removeExercise(exerciseId: String)
    fun move(fromIndex: Int, toIndex: Int)
    fun addExercise()
    fun done()
}

/**
 * The index a dragged row lands on: where it started, moved by as many rows as it
 * was dragged, kept inside the list.
 */
fun dropIndex(startIndex: Int, dragOffsetPx: Float, rowHeightPx: Float, count: Int): Int =
    (startIndex + (dragOffsetPx / rowHeightPx).roundToInt()).coerceIn(0, count - 1)

/** Edits one routine. Every change is stored at once; there is nothing to save at the end. */
class RoutineEditorViewModel(
    private val routineId: Long,
    private val routines: RoutineRepository,
) : ViewModel() {
    /** Null while loading, and if the routine no longer exists. */
    val state: StateFlow<RoutineEditorUiState?> = routines.observeRoutine(routineId)
        .map { routine -> routine?.let { RoutineEditorUiState(it.id, it.name, it.exercises) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A blank name is not stored; the routine keeps the name it had. */
    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { routines.renameRoutine(routineId, name) }
    }

    fun step(exerciseId: String, position: Int, field: SetField, steps: Int) {
        viewModelScope.launch { routines.adjustSet(routineId, exerciseId, position, field, steps) }
    }

    fun addSet(exerciseId: String) {
        viewModelScope.launch { routines.addSet(routineId, exerciseId) }
    }

    fun removeSet(exerciseId: String, position: Int) {
        viewModelScope.launch { routines.removeSet(routineId, exerciseId, position) }
    }

    fun addExercise(exerciseId: String) {
        viewModelScope.launch { routines.addExercise(routineId, exerciseId) }
    }

    fun removeExercise(exerciseId: String) {
        viewModelScope.launch { routines.removeExercise(routineId, exerciseId) }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        viewModelScope.launch { routines.moveExercise(routineId, fromIndex, toIndex) }
    }
}
