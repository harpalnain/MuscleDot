package com.example.fit.data

import java.time.DayOfWeek

/**
 * What a new install starts with (spec section 10): five routines, each a name and
 * the ids of its built-in exercises in order. The user can edit or delete them all.
 */
val STARTER_ROUTINES: List<Pair<String, List<String>>> = listOf(
    "Full Body" to listOf(
        "squat", "bench-press", "bent-over-row", "dumbbell-shoulder-press",
        "lat-pulldown", "dumbbell-bicep-curl", "triceps-pushdown",
    ),
    "Chest + Triceps" to listOf(
        "bench-press", "incline-dumbbell-press", "dumbbell-flyes", "triceps-pushdown", "overhead-triceps-extension",
    ),
    "Back + Biceps" to listOf("lat-pulldown", "seated-cable-row", "bent-over-row", "barbell-curl", "hammer-curl"),
    "Leg + Shoulder" to listOf(
        "squat", "leg-press", "leg-extension", "lying-leg-curl", "dumbbell-shoulder-press", "side-lateral-raise",
    ),
    "Cardio" to listOf("treadmill", "stationary-bike"),
)

/** The starter weekly schedule: day to routine name, null for a rest day. */
val STARTER_SCHEDULE: Map<DayOfWeek, String?> = mapOf(
    DayOfWeek.MONDAY to "Chest + Triceps",
    DayOfWeek.TUESDAY to "Back + Biceps",
    DayOfWeek.WEDNESDAY to "Leg + Shoulder",
    DayOfWeek.THURSDAY to "Cardio",
    DayOfWeek.FRIDAY to "Full Body",
    DayOfWeek.SATURDAY to null,
    DayOfWeek.SUNDAY to null,
)
