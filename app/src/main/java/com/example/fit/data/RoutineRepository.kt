package com.example.fit.data

import androidx.room.withTransaction
import com.example.fit.data.db.AppDatabase
import com.example.fit.data.db.RoutineDao
import com.example.fit.data.db.RoutineEntity
import com.example.fit.data.db.RoutineExerciseEntity
import com.example.fit.data.db.RoutineSetEntity
import com.example.fit.data.db.RoutineWithExercises
import com.example.fit.data.db.ScheduleDayEntity
import com.example.fit.data.db.toDomain
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.Routine
import com.example.fit.domain.RoutineExercise
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.domain.stepped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

/** Routines, their starting sets, and the weekly schedule (spec 5.2). */
class RoutineRepository(private val db: AppDatabase, private val startingSets: StartingSets) {
    private val dao = db.routineDao()
    private val exerciseDao = db.exerciseDao()

    /** In list order, each with its exercises and sets in order. */
    fun observeRoutines(): Flow<List<Routine>> = dao.observeRoutines().map { list -> list.map { it.toDomain() } }

    fun observeRoutine(id: Long): Flow<Routine?> = dao.observeRoutine(id).map { it?.toDomain() }

    suspend fun getRoutine(id: Long): Routine? = dao.getRoutine(id)?.toDomain()

    /** Added at the end of the list. Returns the new routine's id. */
    suspend fun createRoutine(name: String): Long = db.withTransaction {
        dao.insertRoutine(RoutineEntity(name = name.trim(), position = dao.nextRoutinePosition()))
    }

    suspend fun renameRoutine(id: Long, name: String) = dao.renameRoutine(id, name.trim())

    /** A copy named "<name> copy" with the same exercises and starting sets, at the end of the list. */
    suspend fun duplicateRoutine(id: Long): Long = db.withTransaction {
        val source = requireNotNull(dao.getRoutine(id)) { "No routine $id" }.toDomain()
        val copyId = dao.insertRoutine(RoutineEntity(name = "${source.name} copy", position = dao.nextRoutinePosition()))
        for (exercise in source.exercises) {
            dao.insertExerciseWithSets(copyId, exercise.exercise.id, exercise.sets)
        }
        copyId
    }

    /** Its days in the schedule become rest days; its saved workouts stay (foreign keys set null). */
    suspend fun deleteRoutine(id: Long) = dao.deleteRoutine(id)

    /** Adds the exercise at the end with its starting sets. Does nothing if it is already there. */
    suspend fun addExercise(routineId: Long, exerciseId: String) = db.withTransaction {
        if (dao.findExercise(routineId, exerciseId) != null) return@withTransaction
        val exercise = exerciseDao.get(exerciseId)?.toDomain() ?: return@withTransaction
        dao.insertExerciseWithSets(routineId, exerciseId, startingSets.forExercise(exercise))
    }

    suspend fun removeExercise(routineId: Long, exerciseId: String) = db.withTransaction {
        dao.removeExerciseAndRenumber(routineId, exerciseId)
        Unit
    }

    /** Moves the exercise at [fromIndex] so that it ends up at [toIndex]. */
    suspend fun moveExercise(routineId: Long, fromIndex: Int, toIndex: Int) = db.withTransaction {
        val links = dao.exercisesOf(routineId).toMutableList()
        if (fromIndex !in links.indices || toIndex !in links.indices) return@withTransaction
        links.add(toIndex, links.removeAt(fromIndex))
        dao.updateExercises(links.mapIndexed { index, link -> link.copy(position = index) })
    }

    /** One transaction per step, so quick repeated presses never lose a step. */
    suspend fun adjustSet(routineId: Long, exerciseId: String, position: Int, field: SetField, steps: Int) =
        db.withTransaction {
            val link = dao.findExercise(routineId, exerciseId) ?: return@withTransaction
            val set = dao.setsOf(link.id).getOrNull(position) ?: return@withTransaction
            val stepped = SetValues(set.weightHg, set.reps, set.minutes).stepped(field, steps)
            dao.updateSets(listOf(set.copy(weightHg = stepped.weightHg, reps = stepped.reps, minutes = stepped.minutes)))
        }

    /** Copies the last set. Cardio keeps its single row. */
    suspend fun addSet(routineId: Long, exerciseId: String) = db.withTransaction {
        val link = dao.findExercise(routineId, exerciseId) ?: return@withTransaction
        if (exerciseDao.get(exerciseId)?.kind == ExerciseKind.CARDIO) return@withTransaction
        val sets = dao.setsOf(link.id)
        val last = sets.lastOrNull() ?: return@withTransaction
        dao.insertSets(listOf(last.copy(id = 0, position = sets.size)))
    }

    /** Removes the set at [position]. The last remaining set cannot be removed. */
    suspend fun removeSet(routineId: Long, exerciseId: String, position: Int) = db.withTransaction {
        val link = dao.findExercise(routineId, exerciseId) ?: return@withTransaction
        val sets = dao.setsOf(link.id)
        if (sets.size <= 1 || position !in sets.indices) return@withTransaction
        dao.deleteSet(sets[position].id)
        dao.updateSets((sets - sets[position]).mapIndexed { index, set -> set.copy(position = index) })
    }

    /** Always all seven days; a null routine is a rest day. */
    fun observeSchedule(): Flow<Map<DayOfWeek, Long?>> = dao.observeSchedule().map { rows ->
        DayOfWeek.entries.associateWith { day -> rows.firstOrNull { it.dayOfWeek == day.value }?.routineId }
    }

    suspend fun setScheduleDay(day: DayOfWeek, routineId: Long?) =
        dao.upsertScheduleDay(ScheduleDayEntity(day.value, routineId))
}

internal fun RoutineWithExercises.toDomain() = Routine(
    id = routine.id,
    name = routine.name,
    exercises = exercises.sortedWith(compareBy({ it.link.position }, { it.link.id })).map { item ->
        RoutineExercise(
            exercise = item.exercise.toDomain(),
            sets = item.sets.sortedWith(compareBy({ it.position }, { it.id })).map { SetValues(it.weightHg, it.reps, it.minutes) },
        )
    },
)

private fun List<SetValues>.toRoutineSets(routineExerciseId: Long) = mapIndexed { index, values ->
    RoutineSetEntity(
        routineExerciseId = routineExerciseId,
        position = index,
        weightHg = values.weightHg,
        reps = values.reps,
        minutes = values.minutes,
    )
}

// The helpers below are shared with WorkoutRepository, which changes routines when a
// workout is finished or when the user picks "Also update the routine". They leave
// positions gap-free and must be called inside a transaction.

/** Inserts the exercise at [atIndex] (the end when null), shifting later exercises down. */
internal suspend fun RoutineDao.insertExerciseWithSets(
    routineId: Long,
    exerciseId: String,
    sets: List<SetValues>,
    atIndex: Int? = null,
) {
    val links = exercisesOf(routineId)
    val index = (atIndex ?: links.size).coerceIn(0, links.size)
    updateExercises(links.mapIndexed { i, link -> link.copy(position = if (i < index) i else i + 1) })
    val linkId = insertExercise(RoutineExerciseEntity(routineId = routineId, exerciseId = exerciseId, position = index))
    insertSets(sets.toRoutineSets(linkId))
}

/** Removes the exercise and closes the gap. Returns the index it had, or null if it was not there. */
internal suspend fun RoutineDao.removeExerciseAndRenumber(routineId: Long, exerciseId: String): Int? {
    val links = exercisesOf(routineId)
    val index = links.indexOfFirst { it.exerciseId == exerciseId }
    if (index < 0) return null
    deleteExercise(links[index].id)
    updateExercises((links - links[index]).mapIndexed { i, link -> link.copy(position = i) })
    return index
}

/** Replaces all starting sets of one routine exercise. */
internal suspend fun RoutineDao.replaceSets(routineExerciseId: Long, sets: List<SetValues>) {
    deleteSetsOf(routineExerciseId)
    insertSets(sets.toRoutineSets(routineExerciseId))
}
