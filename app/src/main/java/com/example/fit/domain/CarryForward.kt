package com.example.fit.domain

/**
 * What a routine shows for an exercise the next time, given what was just done (spec 5.7).
 *
 * [workoutSets] are the exercise's sets in the workout, in order, ticked or not.
 * Set by set: a ticked set carries its numbers; an unticked set keeps the routine's
 * old numbers for that position; an unticked set the routine never had is dropped.
 *
 * Returns null when nothing was ticked, meaning the routine is left alone.
 */
fun nextStartingSets(routineSets: List<SetValues>, workoutSets: List<WorkoutSet>): List<SetValues>? {
    if (workoutSets.none { it.done }) return null
    return workoutSets.mapIndexedNotNull { index, set ->
        if (set.done) set.values else routineSets.getOrNull(index)
    }
}
