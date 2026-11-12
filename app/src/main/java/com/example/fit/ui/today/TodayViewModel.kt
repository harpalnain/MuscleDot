package com.example.fit.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.ProfileRepository
import com.example.fit.data.RoutineRepository
import com.example.fit.data.TodayHolder
import com.example.fit.data.WorkoutRepository
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.Routine
import com.example.fit.domain.SetField
import com.example.fit.domain.TodayInput
import com.example.fit.domain.TodayState
import com.example.fit.domain.Workout
import com.example.fit.domain.WorkoutStatus
import com.example.fit.domain.buildTodayState
import com.example.fit.domain.nextOpenExercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate

/** A body weight set was ticked but no body weight is known yet (spec 5.4). */
data class BodyWeightPrompt(val exerciseId: String, val position: Int)

interface TodayActions {
    fun open(exerciseId: String)
    fun step(exerciseId: String, position: Int, field: SetField, steps: Int)
    fun toggleDone(exerciseId: String, position: Int)
    fun addSet(exerciseId: String)
    fun removeLastSet(exerciseId: String)

    /** "Change routine" and, on a rest day, "Start a routine". */
    fun chooseRoutine()
    fun addExercise()
    fun swapExercise(exerciseId: String)
    fun removeExercise(exerciseId: String)
    fun finish()
    fun continueWorkout()
    fun showExercise(exerciseId: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val workouts: WorkoutRepository,
    private val routines: RoutineRepository,
    private val profile: ProfileRepository,
    private val today: TodayHolder,
) : ViewModel() {
    private val openExerciseId = MutableStateFlow<String?>(null)
    private val prompt = MutableStateFlow<BodyWeightPrompt?>(null)

    /** Changes are applied one at a time, in the order they were made. */
    private val changes = Mutex()

    private val workoutToday: Flow<Workout?> = today.date.flatMapLatest { workouts.observeWorkoutOn(it) }

    private data class Stored(
        val date: LocalDate,
        val workout: Workout?,
        val routines: List<Routine>,
        val schedule: Map<DayOfWeek, Long?>,
        val saved: List<Workout>,
    )

    private val stored: Flow<Stored> = combine(
        today.date,
        workoutToday,
        routines.observeRoutines(),
        routines.observeSchedule(),
        workouts.observeSaved(),
    ) { date, workout, routineList, schedule, saved ->
        // Right after the day changes, the workout flow may still hold yesterday's.
        Stored(date, workout?.takeIf { it.date == date }, routineList, schedule, saved)
    }

    /** Null while loading. */
    val state: StateFlow<TodayState?> = combine(stored, openExerciseId) { data, open ->
        buildTodayState(
            TodayInput(
                date = data.date,
                workout = data.workout,
                scheduledRoutine = data.schedule[data.date.dayOfWeek]?.let { id -> data.routines.firstOrNull { it.id == id } },
                routines = data.routines,
                saved = data.saved,
                scheduledDays = data.schedule.filterValues { it != null }.keys,
                openExerciseId = open,
            ),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val bodyWeightPrompt: StateFlow<BodyWeightPrompt?> = prompt.asStateFlow()

    /** The stored workout's id, for the exercise picker; null while only a preview is shown. */
    val currentWorkoutId: StateFlow<Long?> = workoutToday.map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every routine, for choosing one for today. */
    val routineOptions: StateFlow<List<Routine>> = routines.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(exerciseId: String) {
        openExerciseId.value = exerciseId
    }

    fun step(exerciseId: String, position: Int, field: SetField, steps: Int) = change { workout ->
        workouts.adjustSet(workout.id, exerciseId, position, field, steps)
    }

    fun toggleDone(exerciseId: String, position: Int) = change { workout ->
        val exercise = workout.exercises.firstOrNull { it.exercise.id == exerciseId } ?: return@change
        val set = exercise.sets.firstOrNull { it.position == position } ?: return@change
        if (set.done) {
            workouts.setDone(workout.id, exerciseId, position, false)
        } else if (exercise.exercise.kind == ExerciseKind.BODY_WEIGHT && workout.bodyWeightHg == null) {
            // Its load needs a body weight: ask first, tick after.
            prompt.value = BodyWeightPrompt(exerciseId, position)
        } else {
            tick(workout.id, exerciseId, position)
        }
    }

    /** Stores today's body weight, then ticks the set that was waiting for it. */
    fun confirmBodyWeight(weightHg: Int) {
        val waiting = prompt.value ?: return
        prompt.value = null
        change { workout ->
            profile.setBodyWeight(today.date.value, weightHg)
            tick(workout.id, waiting.exerciseId, waiting.position)
        }
    }

    fun dismissBodyWeightPrompt() {
        prompt.value = null
    }

    fun addSet(exerciseId: String) = change { workout -> workouts.addSet(workout.id, exerciseId) }

    fun removeLastSet(exerciseId: String) = change { workout ->
        val sets = workout.exercises.firstOrNull { it.exercise.id == exerciseId }?.sets.orEmpty()
        if (sets.size > 1) workouts.removeSet(workout.id, exerciseId, sets.last().position)
    }

    /** Starts [routineId] today. Ignored once a set is ticked (spec 5.3). */
    fun startRoutine(routineId: Long) {
        viewModelScope.launch {
            changes.withLock {
                runCatching { workouts.startWorkout(today.date.value, routineId) }
                    .onSuccess { openExerciseId.value = null }
            }
        }
    }

    fun addExercise(exerciseId: String, alsoUpdateRoutine: Boolean) = change { workout ->
        workouts.addExercise(workout.id, exerciseId, alsoUpdateRoutine)
        openExerciseId.value = exerciseId
    }

    fun replaceExercise(oldExerciseId: String, newExerciseId: String, alsoUpdateRoutine: Boolean) = change { workout ->
        workouts.replaceExercise(workout.id, oldExerciseId, newExerciseId, alsoUpdateRoutine)
        openExerciseId.value = newExerciseId
    }

    fun removeExercise(exerciseId: String, alsoUpdateRoutine: Boolean) = change { workout ->
        workouts.removeExercise(workout.id, exerciseId, alsoUpdateRoutine)
    }

    fun finish() = change { workout -> workouts.finish(workout.id) }

    /** Reopens today's finished workout (spec 5.6). */
    fun continueWorkout() {
        viewModelScope.launch {
            changes.withLock {
                val workout = workouts.observeWorkoutOn(today.date.value).first() ?: return@withLock
                workouts.reopen(workout.id)
            }
        }
    }

    private suspend fun tick(workoutId: Long, exerciseId: String, position: Int) {
        workouts.setDone(workoutId, exerciseId, position, true)
        val after = workouts.observeWorkout(workoutId).first() ?: return
        // Ticking the last set of an exercise moves on to the next one with work left.
        openExerciseId.value = nextOpenExercise(after.exercises, exerciseId)
    }

    /**
     * Runs [block] on today's workout in progress. While only the scheduled routine
     * is shown, the first change stores the workout, so nothing done is ever lost.
     */
    private fun change(block: suspend (Workout) -> Unit) {
        viewModelScope.launch {
            changes.withLock {
                val workout = workoutInProgress() ?: return@withLock
                block(workout)
            }
        }
    }

    private suspend fun workoutInProgress(): Workout? {
        val date = today.date.value
        val existing = workouts.observeWorkoutOn(date).first()
        if (existing != null) return existing.takeIf { it.status == WorkoutStatus.IN_PROGRESS }
        val routineId = routines.observeSchedule().first()[date.dayOfWeek] ?: return null
        val id = workouts.ensureWorkout(date, routineId)
        return workouts.observeWorkout(id).first()
    }
}
