package com.example.fit.data

import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.Exercise
import com.example.fit.domain.SetValues
import com.example.fit.domain.defaultStartingSets

/** Starting sets for an exercise that is newly added to a routine or a workout (spec 5.7). */
class StartingSets(db: AppDatabase) {
    private val dao = db.routineDao()

    /**
     * What was last done: the ticked sets from the most recent finished workout, in
     * any routine, that has a ticked set of this exercise. If it has never been done,
     * the defaults for its kind and equipment.
     */
    suspend fun forExercise(exercise: Exercise): List<SetValues> {
        val lastDone = dao.latestFinishedSets(exercise.id)
        return if (lastDone.isEmpty()) {
            defaultStartingSets(exercise.kind, exercise.equipment)
        } else {
            lastDone.map { SetValues(it.weightHg, it.reps, it.minutes) }
        }
    }
}
