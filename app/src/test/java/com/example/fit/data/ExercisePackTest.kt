package com.example.fit.data

import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the built-in exercise list and its pictures as they are bundled into the app. */
class ExercisePackTest {
    private val list: List<BuiltInExercise> =
        Json.decodeFromString(File("src/main/assets/exercises.json").readText())
    private val dir = File("src/main/assets/exercises")

    private fun count(group: MuscleGroup) = list.count { it.group == group }

    @Test
    fun hasAbout150Exercises() = assertTrue("${list.size}", list.size in 140..160)

    @Test
    fun idsAndNamesAreUniqueAndWellFormed() {
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertEquals(list.size, list.map { it.name.lowercase() }.toSet().size)
        assertTrue(list.all { Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(it.id) })
        assertEquals(list.map { it.name }.sortedBy { it.lowercase() }, list.map { it.name })
    }

    @Test
    fun everyExerciseHasTwoSmallPictures_andNothingElseIsBundled() {
        list.forEach { e ->
            for (i in 0..1) {
                val f = File(dir, "${e.id}_$i.webp")
                assertTrue("missing ${f.name}", f.isFile)
                assertTrue("${f.name} too big", f.length() < 40_000)
            }
        }
        assertEquals(list.size * 2, dir.list()!!.size)
        assertTrue(dir.listFiles()!!.sumOf { it.length() } < 10_000_000)
    }

    @Test
    fun everyMuscleGroupIsCovered() {
        for (g in MuscleGroup.entries - MuscleGroup.CARDIO) assertTrue("$g has ${count(g)}", count(g) >= 12)
        assertTrue(count(MuscleGroup.CARDIO) >= 5)
    }

    @Test
    fun kindsFollowTheSpec() {
        assertTrue(list.all { (it.group == MuscleGroup.CARDIO) == (it.kind == ExerciseKind.CARDIO) })
        val wholeBody = Regex("pull-?up|chin-?up|dip|push-?up", RegexOption.IGNORE_CASE)
        assertTrue(list.filter { it.kind == ExerciseKind.BODY_WEIGHT }.all { wholeBody.containsMatchIn(it.name) })
        assertTrue(list.filter { it.kind == ExerciseKind.REPS_ONLY }.all { it.equipment == "body only" })
        assertTrue(list.count { it.kind == ExerciseKind.REPS_ONLY } >= 5)
        assertTrue(list.none { it.name.contains("plank", ignoreCase = true) })
    }

    @Test
    fun requiredExercisesArePresentWithTheRightKind() {
        val kinds = list.associate { it.id to it.kind }
        val weights = listOf(
            "squat", "bench-press", "bent-over-row", "dumbbell-shoulder-press", "lat-pulldown",
            "dumbbell-bicep-curl", "triceps-pushdown", "incline-dumbbell-press", "dumbbell-flyes",
            "overhead-triceps-extension", "seated-cable-row", "barbell-curl", "hammer-curl", "leg-press",
            "leg-extension", "lying-leg-curl", "side-lateral-raise",
        )
        weights.forEach { assertEquals(it, ExerciseKind.WEIGHTS, kinds[it]) }
        listOf("pull-up", "chin-up", "push-up", "dips", "chest-dips").forEach {
            assertEquals(it, ExerciseKind.BODY_WEIGHT, kinds[it])
        }
        assertEquals(ExerciseKind.REPS_ONLY, kinds["crunches"])
        listOf("treadmill", "stationary-bike").forEach { assertEquals(it, ExerciseKind.CARDIO, kinds[it]) }
    }
}
