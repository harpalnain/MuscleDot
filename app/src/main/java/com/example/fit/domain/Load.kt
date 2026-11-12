package com.example.fit.domain

/**
 * The weight that counts for load and records. Body weight exercises lift the
 * body plus any extra weight; reps only and cardio exercises carry no weight.
 */
fun effectiveWeightHg(kind: ExerciseKind, weightHg: Int, bodyWeightHg: Int?): Int = when (kind) {
    ExerciseKind.WEIGHTS -> weightHg
    ExerciseKind.BODY_WEIGHT -> (bodyWeightHg ?: 0) + weightHg
    ExerciseKind.REPS_ONLY, ExerciseKind.CARDIO -> 0
}

/** Load of one set: weight × reps, in hectograms. */
fun setLoadHg(kind: ExerciseKind, values: SetValues, bodyWeightHg: Int?): Long =
    effectiveWeightHg(kind, values.weightHg, bodyWeightHg).toLong() * values.reps

fun WorkoutExercise.doneSets(): List<WorkoutSet> = sets.filter { it.done }

/** Load over ticked sets only. */
fun WorkoutExercise.loadHg(bodyWeightHg: Int?): Long =
    doneSets().sumOf { setLoadHg(exercise.kind, it.values, bodyWeightHg) }

/** Reps over ticked sets only. Cardio has no reps. */
fun WorkoutExercise.totalReps(): Int =
    if (exercise.kind == ExerciseKind.CARDIO) 0 else doneSets().sumOf { it.values.reps }

/** Minutes over ticked sets only. Zero unless the exercise is cardio. */
fun WorkoutExercise.totalMinutes(): Int =
    if (exercise.kind == ExerciseKind.CARDIO) doneSets().sumOf { it.values.minutes } else 0

fun Workout.loadHg(): Long = exercises.sumOf { it.loadHg(bodyWeightHg) }

fun Workout.cardioMinutes(): Int = exercises.sumOf { it.totalMinutes() }

fun Workout.doneSetCount(): Int = exercises.sumOf { it.doneSets().size }
