package com.example.fit.domain

private const val DEFAULT_SET_COUNT = 3
private const val DEFAULT_REPS = 10
private const val DEFAULT_CARDIO_MINUTES = 10

/**
 * Starting sets for an exercise that has never been done (spec 5.7, section 10).
 * Workout numbers cannot be typed, so weights start at a modest value for the
 * equipment instead of zero.
 */
fun defaultStartingSets(kind: ExerciseKind, equipment: String?): List<SetValues> = when (kind) {
    ExerciseKind.CARDIO -> listOf(SetValues(minutes = DEFAULT_CARDIO_MINUTES))
    ExerciseKind.BODY_WEIGHT, ExerciseKind.REPS_ONLY -> List(DEFAULT_SET_COUNT) { SetValues(reps = DEFAULT_REPS) }
    ExerciseKind.WEIGHTS -> List(DEFAULT_SET_COUNT) { SetValues(startingWeightHg(equipment), DEFAULT_REPS) }
}

private fun startingWeightHg(equipment: String?): Int = when (equipment) {
    "barbell" -> 200
    "dumbbell" -> 50
    "cable", "machine" -> 100
    else -> 0
}
