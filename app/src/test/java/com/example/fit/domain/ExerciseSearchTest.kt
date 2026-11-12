package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseSearchTest {
    private fun e(name: String, group: MuscleGroup, hidden: Boolean = false) =
        Exercise(name.lowercase().replace(' ', '-'), name, group, null, null, ExerciseKind.WEIGHTS, isHidden = hidden)

    private val all = listOf(
        e("Leg Press", MuscleGroup.LEGS),
        e("Bench Press", MuscleGroup.CHEST),
        e("Incline Dumbbell Press", MuscleGroup.CHEST),
        e("Dumbbell Shoulder Press", MuscleGroup.SHOULDERS),
        e("Barbell Curl", MuscleGroup.ARMS),
        e("Old Press", MuscleGroup.CHEST, hidden = true),
    )

    @Test
    fun matchesAnyPartOfTheNameIgnoringCase_sortedByName() = assertEquals(
        listOf("Bench Press", "Dumbbell Shoulder Press", "Incline Dumbbell Press", "Leg Press"),
        filterExercises(all, "PRESS", null).map { it.name },
    )

    @Test
    fun everyTermMustMatch() = assertEquals(
        listOf("Dumbbell Shoulder Press", "Incline Dumbbell Press"),
        filterExercises(all, "dumb pre", null).map { it.name },
    )

    @Test
    fun groupFilterCombinesWithQuery() = assertEquals(
        listOf("Bench Press", "Incline Dumbbell Press"),
        filterExercises(all, "press", MuscleGroup.CHEST).map { it.name },
    )

    @Test
    fun blankQueryListsEverythingVisible() = assertEquals(5, filterExercises(all, "  ", null).size)
}
