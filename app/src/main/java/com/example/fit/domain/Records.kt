package com.example.fit.domain

import java.time.LocalDate

/** One exercise in one saved workout. [sets] holds ticked sets only, in order. */
data class ExerciseSession(
    val workoutId: Long,
    val date: LocalDate,
    val routineId: Long?,
    val kind: ExerciseKind,
    val bodyWeightHg: Int?,
    val sets: List<SetValues>,
)

private fun Workout.sessionOf(exerciseId: String): ExerciseSession? {
    val exercise = exercises.firstOrNull { it.exercise.id == exerciseId } ?: return null
    val ticked = exercise.doneSets().map { it.values }
    if (ticked.isEmpty()) return null
    return ExerciseSession(id, date, routineId, exercise.exercise.kind, bodyWeightHg, ticked)
}

/** Ascending by date. Skips workouts in which the exercise has no ticked set. */
fun List<Workout>.sessionsOf(exerciseId: String): List<ExerciseSession> =
    sortedBy { it.date }.mapNotNull { it.sessionOf(exerciseId) }

fun ExerciseSession.loadHg(): Long = sets.sumOf { setLoadHg(kind, it, bodyWeightHg) }

fun ExerciseSession.totalReps(): Int = if (kind == ExerciseKind.CARDIO) 0 else sets.sumOf { it.reps }

fun ExerciseSession.totalMinutes(): Int = if (kind == ExerciseKind.CARDIO) sets.sumOf { it.minutes } else 0

data class Dated<T>(val value: T, val date: LocalDate)

/** [weightHg] is the effective weight (body weight included for body weight exercises). */
data class BestSet(val loadHg: Long, val weightHg: Int, val reps: Int, val date: LocalDate)

/**
 * Personal records of one exercise (spec 5.9). Which fields are set depends on the
 * exercise's kind. Each date is the first date the record value was reached.
 */
data class ExerciseRecords(
    val maxWeightHg: Dated<Int>? = null, // WEIGHTS, BODY_WEIGHT
    val bestSet: BestSet? = null, // WEIGHTS, BODY_WEIGHT
    val bestSessionHg: Dated<Long>? = null, // WEIGHTS, BODY_WEIGHT
    val mostRepsSet: Dated<Int>? = null, // REPS_ONLY
    val mostRepsSession: Dated<Int>? = null, // REPS_ONLY
    val longestMinutes: Dated<Int>? = null, // CARDIO
)

enum class RecordType { MAX_WEIGHT, BEST_SET, BEST_SESSION, MOST_REPS_SET, MOST_REPS_SESSION }

data class RecordBroken(val exerciseId: String, val type: RecordType)

/** The larger of [current] and a new value; a tie keeps [current], so the first date wins. */
private fun <T : Comparable<T>> later(current: Dated<T>?, value: T, date: LocalDate): Dated<T> =
    if (current != null && current.value >= value) current else Dated(value, date)

/** These records with one more session taken into account. */
private fun ExerciseRecords.including(session: ExerciseSession): ExerciseRecords {
    if (session.sets.isEmpty()) return this
    return when (session.kind) {
        ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> {
            var maxWeight = maxWeightHg
            var best = bestSet
            for (set in session.sets) {
                val weight = effectiveWeightHg(session.kind, set.weightHg, session.bodyWeightHg)
                val load = weight.toLong() * set.reps
                maxWeight = later(maxWeight, weight, session.date)
                if (best == null || load > best.loadHg) best = BestSet(load, weight, set.reps, session.date)
            }
            copy(
                maxWeightHg = maxWeight,
                bestSet = best,
                bestSessionHg = later(bestSessionHg, session.loadHg(), session.date),
            )
        }

        ExerciseKind.REPS_ONLY -> copy(
            mostRepsSet = later(mostRepsSet, session.sets.maxOf { it.reps }, session.date),
            mostRepsSession = later(mostRepsSession, session.totalReps(), session.date),
        )

        ExerciseKind.CARDIO -> copy(
            longestMinutes = later(longestMinutes, session.totalMinutes(), session.date),
        )
    }
}

fun exerciseRecords(sessions: List<ExerciseSession>): ExerciseRecords =
    sessions.sortedBy { it.date }.fold(ExerciseRecords()) { records, session -> records.including(session) }

/** True when [value] beats an earlier record. With no earlier record there is nothing to beat. */
private fun <T : Comparable<T>> beats(value: T?, earlier: T?): Boolean =
    value != null && earlier != null && value > earlier

private fun brokenAgainst(earlier: ExerciseRecords, session: ExerciseSession): Set<RecordType> {
    val now = ExerciseRecords().including(session)
    return buildSet {
        if (beats(now.maxWeightHg?.value, earlier.maxWeightHg?.value)) add(RecordType.MAX_WEIGHT)
        if (beats(now.bestSet?.loadHg, earlier.bestSet?.loadHg)) add(RecordType.BEST_SET)
        if (beats(now.bestSessionHg?.value, earlier.bestSessionHg?.value)) add(RecordType.BEST_SESSION)
        if (beats(now.mostRepsSet?.value, earlier.mostRepsSet?.value)) add(RecordType.MOST_REPS_SET)
        if (beats(now.mostRepsSession?.value, earlier.mostRepsSession?.value)) add(RecordType.MOST_REPS_SESSION)
    }
}

/**
 * Record types [session] beats, compared with the sessions [before] it. A type with
 * no earlier value is never "broken", so a first session breaks nothing. Cardio has
 * no record types.
 */
fun recordsBroken(before: List<ExerciseSession>, session: ExerciseSession): Set<RecordType> =
    brokenAgainst(exerciseRecords(before), session)

/**
 * Positions of ticked sets that earn the "New record" badge. Walking the sets in
 * position order, a set earns it when it beats the best max weight or best set
 * (most reps in one set for reps only) seen so far, counting [before] and the
 * earlier sets of this workout.
 */
fun recordSetPositions(
    before: List<ExerciseSession>,
    kind: ExerciseKind,
    bodyWeightHg: Int?,
    sets: List<WorkoutSet>,
): Set<Int> {
    val earlier = exerciseRecords(before)
    val ticked = sets.filter { it.done }.sortedBy { it.position }
    return when (kind) {
        ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> {
            var bestWeight = earlier.maxWeightHg?.value ?: return emptySet()
            var bestLoad = earlier.bestSet?.loadHg ?: return emptySet()
            buildSet {
                for (set in ticked) {
                    val weight = effectiveWeightHg(kind, set.values.weightHg, bodyWeightHg)
                    val load = weight.toLong() * set.values.reps
                    if (weight > bestWeight || load > bestLoad) add(set.position)
                    bestWeight = maxOf(bestWeight, weight)
                    bestLoad = maxOf(bestLoad, load)
                }
            }
        }

        ExerciseKind.REPS_ONLY -> {
            var bestReps = earlier.mostRepsSet?.value ?: return emptySet()
            buildSet {
                for (set in ticked) {
                    if (set.values.reps > bestReps) add(set.position)
                    bestReps = maxOf(bestReps, set.values.reps)
                }
            }
        }

        ExerciseKind.CARDIO -> emptySet()
    }
}

/** Records [workout] breaks against the saved workouts dated before it. */
fun workoutRecords(saved: List<Workout>, workout: Workout): List<RecordBroken> {
    val earlier = saved.filter { it.date < workout.date }
    return workout.exercises.flatMap { exercise ->
        val id = exercise.exercise.id
        val session = workout.sessionOf(id) ?: return@flatMap emptyList()
        recordsBroken(earlier.sessionsOf(id), session).map { RecordBroken(id, it) }
    }
}

/** workoutId to number of records broken, for every saved workout. One pass in date order. */
fun recordCounts(saved: List<Workout>): Map<Long, Int> {
    val soFar = mutableMapOf<String, ExerciseRecords>()
    val counts = LinkedHashMap<Long, Int>()
    for (workout in saved.sortedBy { it.date }) {
        val sessions = workout.exercises.mapNotNull { exercise ->
            workout.sessionOf(exercise.exercise.id)?.let { exercise.exercise.id to it }
        }
        counts[workout.id] = sessions.sumOf { (id, session) ->
            brokenAgainst(soFar[id] ?: ExerciseRecords(), session).size
        }
        for ((id, session) in sessions) {
            soFar[id] = (soFar[id] ?: ExerciseRecords()).including(session)
        }
    }
    return counts
}
