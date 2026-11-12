package com.example.fit.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.RoutineRepository
import com.example.fit.data.TodayHolder
import com.example.fit.domain.Routine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** One day of the weekly schedule. No routine means a rest day. */
data class WeekDayRow(val day: DayOfWeek, val routineId: Long?, val routineName: String?, val isToday: Boolean)

data class RoutineRow(val id: Long, val name: String, val exerciseCount: Int)

data class RoutinesUiState(val week: List<WeekDayRow> = emptyList(), val routines: List<RoutineRow> = emptyList())

/** Seven days, Monday to Sunday, and the routines in list order. */
fun buildRoutinesState(routines: List<Routine>, schedule: Map<DayOfWeek, Long?>, today: LocalDate): RoutinesUiState {
    val byId = routines.associateBy { it.id }
    return RoutinesUiState(
        week = DayOfWeek.entries.map { day ->
            val routine = schedule[day]?.let(byId::get)
            WeekDayRow(day, routine?.id, routine?.name, isToday = day == today.dayOfWeek)
        },
        routines = routines.map { RoutineRow(it.id, it.name, it.exercises.size) },
    )
}

interface RoutinesActions {
    fun setDay(day: DayOfWeek, routineId: Long?)
    fun create(name: String)
    fun edit(id: Long)
    fun duplicate(id: Long)
    fun delete(id: Long)
}

class RoutinesViewModel(private val routines: RoutineRepository, today: TodayHolder) : ViewModel() {
    val state: StateFlow<RoutinesUiState> =
        combine(routines.observeRoutines(), routines.observeSchedule(), today.date, ::buildRoutinesState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    fun setDay(day: DayOfWeek, routineId: Long?) {
        viewModelScope.launch { routines.setScheduleDay(day, routineId) }
    }

    /** [onCreated] gets the new routine's id, to open it for editing. */
    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(routines.createRoutine(name)) }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch { routines.duplicateRoutine(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { routines.deleteRoutine(id) }
    }
}
