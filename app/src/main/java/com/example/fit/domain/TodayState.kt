package com.example.fit.domain

import java.time.DayOfWeek
import java.time.LocalDate

// What the Today screen shows, worked out from stored data by one pure function.

data class TodaySetUi(val position: Int, val values: SetValues, val done: Boolean, val isRecord: Boolean)

/** One exercise of today's workout. Load, reps and minutes are over ticked sets. */
data class TodayExerciseUi(
    val exercise: Exercise,
    val sets: List<TodaySetUi>,
    val doneCount: Int,
    val loadHg: Long,
    val totalReps: Int,
    val minutes: Int,
    /** The heaviest weight ever lifted in saved history; null if none, or if the exercise has no weight. */
    val bestWeightHg: Int?,
)

data class FinishSummary(val loadHg: Long, val setsDone: Int, val cardioMinutes: Int, val records: List<RecordBroken>)

sealed interface TodayState {
    val date: LocalDate
    val week: WeekCount

    /** Nothing scheduled and nothing started. */
    data class RestDay(
        override val date: LocalDate,
        override val week: WeekCount,
        val routines: List<Routine>,
    ) : TodayState

    /** A workout to do or being done. */
    data class Active(
        override val date: LocalDate,
        override val week: WeekCount,
        val routineName: String,
        /** True while nothing has been touched: the scheduled routine is shown, no workout is stored yet. */
        val isPreview: Boolean,
        val exercises: List<TodayExerciseUi>,
        val openExerciseId: String?,
        val totalLoadHg: Long,
        val cardioMinutes: Int,
        val tickedCount: Int,
        val untickedCount: Int,
    ) : TodayState {
        /** The routine can be switched only before the first tick (spec 5.3). */
        val canChangeRoutine: Boolean get() = tickedCount == 0
        val canFinish: Boolean get() = tickedCount > 0
    }

    /** Today's workout has been finished; it can still be continued. */
    data class Finished(
        override val date: LocalDate,
        override val week: WeekCount,
        val routineName: String,
        val summary: FinishSummary,
        /** Exercise name and record type, one per entry of [FinishSummary.records]. */
        val recordLines: List<Pair<String, RecordType>>,
    ) : TodayState
}

data class TodayInput(
    val date: LocalDate,
    /** The workout stored for [date], if any. */
    val workout: Workout?,
    /** The routine the weekly schedule has on [date]'s weekday, if any. */
    val scheduledRoutine: Routine?,
    val routines: List<Routine>,
    /** Saved history, see WorkoutRepository.observeSaved. */
    val saved: List<Workout>,
    val scheduledDays: Set<DayOfWeek>,
    /** The exercise the user opened; null to let the screen choose. */
    val openExerciseId: String?,
)

fun buildTodayState(input: TodayInput): TodayState {
    val week = weekCount(input.saved.map { it.date }, input.scheduledDays, input.date)
    val workout = input.workout

    if (workout == null) {
        val routine = input.scheduledRoutine ?: return TodayState.RestDay(input.date, week, input.routines)
        val planned = routine.exercises.map { item ->
            WorkoutExercise(item.exercise, item.sets.mapIndexed { index, values -> WorkoutSet(index, values, done = false) })
        }
        return active(input, week, routine.name, isPreview = true, exercises = planned, bodyWeightHg = null)
    }

    if (workout.status == WorkoutStatus.FINISHED) {
        val records = workoutRecords(input.saved, workout)
        val names = workout.exercises.associate { it.exercise.id to it.exercise.name }
        return TodayState.Finished(
            date = input.date,
            week = week,
            routineName = workout.routineName,
            summary = FinishSummary(workout.loadHg(), workout.doneSetCount(), workout.cardioMinutes(), records),
            recordLines = records.map { names.getValue(it.exerciseId) to it.type },
        )
    }

    return active(input, week, workout.routineName, isPreview = false, workout.exercises, workout.bodyWeightHg)
}

private fun active(
    input: TodayInput,
    week: WeekCount,
    routineName: String,
    isPreview: Boolean,
    exercises: List<WorkoutExercise>,
    bodyWeightHg: Int?,
): TodayState.Active {
    // Records are measured against what was saved before today.
    val earlier = input.saved.filter { it.date < input.date }
    val items = exercises.map { item ->
        val before = earlier.sessionsOf(item.exercise.id)
        val recordPositions = recordSetPositions(before, item.exercise.kind, bodyWeightHg, item.sets)
        TodayExerciseUi(
            exercise = item.exercise,
            sets = item.sets.map { TodaySetUi(it.position, it.values, it.done, it.position in recordPositions) },
            doneCount = item.doneSets().size,
            loadHg = item.loadHg(bodyWeightHg),
            totalReps = item.totalReps(),
            minutes = item.totalMinutes(),
            bestWeightHg = exerciseRecords(before).maxWeightHg?.value,
        )
    }
    val open = input.openExerciseId?.takeIf { id -> exercises.any { it.exercise.id == id } }
        ?: nextOpenExercise(exercises, null)
    val ticked = items.sumOf { it.doneCount }
    return TodayState.Active(
        date = input.date,
        week = week,
        routineName = routineName,
        isPreview = isPreview,
        exercises = items,
        openExerciseId = open,
        totalLoadHg = items.sumOf { it.loadHg },
        cardioMinutes = items.sumOf { it.minutes },
        tickedCount = ticked,
        untickedCount = items.sumOf { it.sets.size } - ticked,
    )
}

/**
 * The exercise to show open after a tick: the current one while it still has an
 * unticked set, otherwise the first exercise in order that has one, otherwise null.
 */
fun nextOpenExercise(exercises: List<WorkoutExercise>, openExerciseId: String?): String? {
    fun WorkoutExercise.hasWorkLeft() = sets.any { !it.done }
    val current = exercises.firstOrNull { it.exercise.id == openExerciseId }
    if (current != null && current.hasWorkLeft()) return openExerciseId
    return exercises.firstOrNull { it.hasWorkLeft() }?.exercise?.id
}
