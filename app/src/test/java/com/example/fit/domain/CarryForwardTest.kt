package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarryForwardTest {
    private fun s(hg: Int, reps: Int) = SetValues(hg, reps)
    private fun w(pos: Int, hg: Int, reps: Int, done: Boolean) = WorkoutSet(pos, s(hg, reps), done)
    private val routine = listOf(s(400, 10), s(425, 8), s(450, 6))

    @Test
    fun tickedSetsReplaceTheRoutineValues() = assertEquals(
        listOf(s(425, 10), s(425, 9), s(455, 6)),
        nextStartingSets(routine, listOf(w(0, 425, 10, true), w(1, 425, 9, true), w(2, 455, 6, true))),
    )

    @Test
    fun untickedSetKeepsItsOldRoutineValues() = assertEquals( // owner: "still 3 sets"
        listOf(s(425, 10), s(450, 8), s(450, 6)),
        nextStartingSets(routine, listOf(w(0, 425, 10, true), w(1, 450, 8, true), w(2, 999, 1, false))),
    )

    @Test
    fun addedSetNeverTickedIsDropped() = assertEquals(
        routine,
        nextStartingSets(
            routine,
            listOf(w(0, 400, 10, true), w(1, 425, 8, true), w(2, 450, 6, true), w(3, 450, 6, false)),
        ),
    )

    @Test
    fun addedSetThatWasTickedIsKept() = assertEquals(
        routine + s(460, 4),
        nextStartingSets(
            routine,
            listOf(w(0, 400, 10, true), w(1, 425, 8, true), w(2, 450, 6, true), w(3, 460, 4, true)),
        ),
    )

    @Test
    fun removedSetShortensTheRoutine() = assertEquals(
        listOf(s(400, 10), s(425, 9)),
        nextStartingSets(routine, listOf(w(0, 500, 5, false), w(1, 425, 9, true))),
    )

    @Test
    fun nothingTickedLeavesTheRoutineAlone() =
        assertNull(nextStartingSets(routine, listOf(w(0, 405, 10, false), w(1, 425, 8, false))))

    @Test
    fun cardioMinutesCarryWhenTicked() = assertEquals(
        listOf(SetValues(minutes = 25)),
        nextStartingSets(listOf(SetValues(minutes = 10)), listOf(WorkoutSet(0, SetValues(minutes = 25), true))),
    )
}
