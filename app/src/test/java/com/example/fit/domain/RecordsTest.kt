package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecordsTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun s(hg: Int, reps: Int) = SetValues(hg, reps)

    private fun session(
        id: Long,
        day: Int,
        vararg sets: SetValues,
        kind: ExerciseKind = ExerciseKind.WEIGHTS,
        routineId: Long? = 1,
        bw: Int? = null,
    ) = ExerciseSession(id, d(day), routineId, kind, bw, sets.toList())

    private val nov2 = session(1, 2, s(400, 10), s(425, 8)) // max 425, best set 4000, session 7400
    private val nov5 = session(2, 5, s(400, 10), s(425, 8), s(450, 6)) // max 450, best set 4000, session 10100
    private val nov9 = session(3, 9, s(400, 10), s(425, 8), s(450, 5)) // session 9650

    @Test
    fun recordsCarryTheFirstDateTheyWereReached() {
        val r = exerciseRecords(listOf(nov2, nov5, nov9))
        assertEquals(Dated(450, d(5)), r.maxWeightHg)
        assertEquals(BestSet(4000, 400, 10, d(2)), r.bestSet)
        assertEquals(Dated(10100L, d(5)), r.bestSessionHg)
        assertNull(r.mostRepsSet)
        assertNull(r.longestMinutes)
    }

    @Test
    fun emptyHistory_hasNoRecords() = assertEquals(ExerciseRecords(), exerciseRecords(emptyList()))

    @Test
    fun aTieIsNotANewRecord() =
        assertEquals(setOf(RecordType.MAX_WEIGHT, RecordType.BEST_SESSION), recordsBroken(listOf(nov2), nov5))

    @Test
    fun firstSessionBreaksNothing() = assertEquals(emptySet<RecordType>(), recordsBroken(emptyList(), nov2))

    @Test
    fun bodyWeightExerciseUsesEffectiveWeight() {
        val r = exerciseRecords(listOf(session(1, 2, s(0, 10), kind = ExerciseKind.BODY_WEIGHT, bw = 700)))
        assertEquals(700, r.maxWeightHg?.value)
        assertEquals(7000L, r.bestSet?.loadHg)
    }

    @Test
    fun repsOnlyAndCardioRecords() {
        val reps = exerciseRecords(
            listOf(
                session(1, 2, s(0, 20), s(0, 15), kind = ExerciseKind.REPS_ONLY),
                session(2, 5, s(0, 25), s(0, 5), kind = ExerciseKind.REPS_ONLY),
            ),
        )
        assertEquals(Dated(25, d(5)), reps.mostRepsSet)
        assertEquals(Dated(35, d(2)), reps.mostRepsSession)
        val cardio = exerciseRecords(
            listOf(
                session(1, 2, SetValues(minutes = 20), kind = ExerciseKind.CARDIO),
                session(2, 5, SetValues(minutes = 35), kind = ExerciseKind.CARDIO),
            ),
        )
        assertEquals(Dated(35, d(5)), cardio.longestMinutes)
        assertEquals(
            emptySet<RecordType>(),
            recordsBroken(
                listOf(session(1, 2, SetValues(minutes = 20), kind = ExerciseKind.CARDIO)),
                session(2, 5, SetValues(minutes = 35), kind = ExerciseKind.CARDIO),
            ),
        )
    }

    @Test
    fun badgeGoesToEachSetThatBeatsTheBestSoFar() {
        fun w(pos: Int, hg: Int, reps: Int, done: Boolean = true) = WorkoutSet(pos, s(hg, reps), done)
        assertEquals(
            setOf(2),
            recordSetPositions(listOf(nov2), ExerciseKind.WEIGHTS, null, listOf(w(0, 400, 10), w(1, 425, 8), w(2, 450, 6))),
        )
        assertEquals(
            setOf(0, 2),
            recordSetPositions(listOf(nov2), ExerciseKind.WEIGHTS, null, listOf(w(0, 430, 10), w(1, 425, 8), w(2, 450, 6))),
        )
        assertEquals(
            emptySet<Int>(),
            recordSetPositions(
                listOf(nov2), ExerciseKind.WEIGHTS, null,
                listOf(w(0, 400, 10), w(1, 425, 8), w(2, 450, 6, done = false)),
            ),
        )
        assertEquals(
            emptySet<Int>(),
            recordSetPositions(emptyList(), ExerciseKind.WEIGHTS, null, listOf(w(0, 500, 10))),
        )
    }

    @Test
    fun recordCountsPerWorkout() {
        val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
        fun workout(id: Long, day: Int, vararg sets: SetValues) = Workout(
            id, d(day), 1, "Chest", WorkoutStatus.FINISHED, null,
            listOf(WorkoutExercise(bench, sets.mapIndexed { i, v -> WorkoutSet(i, v, true) })),
        )
        val saved = listOf(
            workout(1, 2, s(400, 10), s(425, 8)),
            workout(2, 5, s(400, 10), s(425, 8), s(450, 6)),
            workout(3, 9, s(400, 10), s(425, 8), s(450, 5)),
        )
        assertEquals(mapOf(1L to 0, 2L to 2, 3L to 0), recordCounts(saved))
        assertEquals(
            listOf(RecordBroken("bench-press", RecordType.MAX_WEIGHT), RecordBroken("bench-press", RecordType.BEST_SESSION)),
            workoutRecords(saved, saved[1]).sortedBy { it.type },
        )
        assertEquals(listOf(nov2, nov5, nov9), saved.sessionsOf("bench-press"))
    }
}
