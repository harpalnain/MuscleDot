package com.example.fit.data

import androidx.room.withTransaction
import com.example.fit.data.db.AppDatabase
import com.example.fit.data.db.WorkoutEntity
import com.example.fit.data.db.WorkoutExerciseEntity
import com.example.fit.data.db.WorkoutSetEntity
import com.example.fit.data.db.WorkoutWithExercises
import com.example.fit.data.db.toDomain
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.domain.Workout
import com.example.fit.domain.WorkoutExercise
import com.example.fit.domain.WorkoutSet
import com.example.fit.domain.WorkoutStatus
import com.example.fit.domain.nextStartingSets
import com.example.fit.domain.stepped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Workouts: creating today's from a routine, ticking and changing sets, and
 * changing its exercises (spec 5.3 to 5.5). Every change is one transaction and
 * is stored at once, so nothing is lost if the app stops.
 *
 * Sets are addressed by workout, exercise and position, never by row id.
 */
class WorkoutRepository(private val db: AppDatabase, private val startingSets: StartingSets) {
    private val dao = db.workoutDao()
    private val routineDao = db.routineDao()
    private val exerciseDao = db.exerciseDao()
    private val profileDao = db.profileDao()

    /** The workout on [date] with every set, ticked or not. */
    fun observeWorkoutOn(date: LocalDate): Flow<Workout?> =
        dao.observeOn(date).map { it?.toDomain() }.distinctUntilChanged()

    fun observeWorkout(id: Long): Flow<Workout?> = dao.observe(id).map { it?.toDomain() }.distinctUntilChanged()

    /** The workout on [date], creating it in progress from the routine's starting values if there is none. */
    suspend fun ensureWorkout(date: LocalDate, routineId: Long): Long = db.withTransaction {
        dao.findOn(date)?.id ?: createFromRoutine(date, routineId)
    }

    /**
     * Starts [routineId] on [date], replacing a workout there that has no ticked set.
     * Throws IllegalStateException if that workout has a ticked set (spec 5.3).
     */
    suspend fun startWorkout(date: LocalDate, routineId: Long): Long = db.withTransaction {
        val existing = dao.findOn(date)
        if (existing != null) {
            check(dao.doneSetCount(existing.id) == 0) { "The workout on $date already has ticked sets" }
            dao.deleteWorkout(existing.id)
        }
        createFromRoutine(date, routineId)
    }

    suspend fun setDone(workoutId: Long, exerciseId: String, position: Int, done: Boolean) = db.withTransaction {
        val set = findSet(workoutId, exerciseId, position) ?: return@withTransaction
        dao.updateSets(listOf(set.copy(done = done)))
    }

    /** One transaction per step, so quick repeated presses never lose a step. */
    suspend fun adjustSet(workoutId: Long, exerciseId: String, position: Int, field: SetField, steps: Int) =
        db.withTransaction {
            val set = findSet(workoutId, exerciseId, position) ?: return@withTransaction
            val stepped = SetValues(set.weightHg, set.reps, set.minutes).stepped(field, steps)
            dao.updateSets(listOf(set.copy(weightHg = stepped.weightHg, reps = stepped.reps, minutes = stepped.minutes)))
        }

    /** Adds a set that copies the last one. Cardio keeps its single row. */
    suspend fun addSet(workoutId: Long, exerciseId: String, done: Boolean = false) = db.withTransaction {
        val link = dao.findExercise(workoutId, exerciseId) ?: return@withTransaction
        if (exerciseDao.get(exerciseId)?.kind == ExerciseKind.CARDIO) return@withTransaction
        val sets = dao.setsOf(link.id)
        val last = sets.lastOrNull() ?: return@withTransaction
        dao.insertSets(listOf(last.copy(id = 0, position = sets.size, done = done)))
    }

    /** Removes the set and renumbers the rest. Removing an exercise's last set removes the exercise. */
    suspend fun removeSet(workoutId: Long, exerciseId: String, position: Int) = db.withTransaction {
        val link = dao.findExercise(workoutId, exerciseId) ?: return@withTransaction
        val sets = dao.setsOf(link.id)
        val target = sets.firstOrNull { it.position == position } ?: return@withTransaction
        dao.deleteSet(target.id)
        val remaining = sets - target
        if (remaining.isEmpty()) {
            deleteExerciseAndRenumber(workoutId, link)
        } else {
            dao.updateSets(remaining.mapIndexed { index, set -> set.copy(position = index) })
        }
    }

    /**
     * Adds the exercise at the end with its starting sets; nothing happens if it is
     * already in the workout. With [alsoUpdateRoutine] the routine gets it too (spec 5.5).
     */
    suspend fun addExercise(workoutId: Long, exerciseId: String, alsoUpdateRoutine: Boolean) = db.withTransaction {
        if (dao.findExercise(workoutId, exerciseId) != null) return@withTransaction
        val exercise = exerciseDao.get(exerciseId)?.toDomain() ?: return@withTransaction
        val sets = startingSets.forExercise(exercise)
        insertExercise(workoutId, exerciseId, dao.exercisesOf(workoutId).size, sets)
        if (alsoUpdateRoutine) {
            val routineId = routineOf(workoutId) ?: return@withTransaction
            if (routineDao.findExercise(routineId, exerciseId) == null) {
                routineDao.insertExerciseWithSets(routineId, exerciseId, sets)
            }
        }
    }

    suspend fun removeExercise(workoutId: Long, exerciseId: String, alsoUpdateRoutine: Boolean) = db.withTransaction {
        val link = dao.findExercise(workoutId, exerciseId) ?: return@withTransaction
        deleteExerciseAndRenumber(workoutId, link)
        if (alsoUpdateRoutine) {
            routineOf(workoutId)?.let { routineDao.removeExerciseAndRenumber(it, exerciseId) }
        }
    }

    /**
     * Puts [newExerciseId] where [oldExerciseId] was. With [alsoUpdateRoutine] the new
     * exercise takes the old one's place in the routine too (or goes to its end if the
     * old one was not there), unless the routine already has the new one.
     */
    suspend fun replaceExercise(
        workoutId: Long,
        oldExerciseId: String,
        newExerciseId: String,
        alsoUpdateRoutine: Boolean,
    ) = db.withTransaction {
        val oldLink = dao.findExercise(workoutId, oldExerciseId) ?: return@withTransaction
        val newExercise = exerciseDao.get(newExerciseId)?.toDomain() ?: return@withTransaction
        val sets = startingSets.forExercise(newExercise)
        if (dao.findExercise(workoutId, newExerciseId) != null) {
            deleteExerciseAndRenumber(workoutId, oldLink)
        } else {
            dao.deleteExercise(oldLink.id)
            insertExercise(workoutId, newExerciseId, oldLink.position, sets)
        }
        if (alsoUpdateRoutine) {
            val routineId = routineOf(workoutId) ?: return@withTransaction
            val routineHasNew = routineDao.findExercise(routineId, newExerciseId) != null
            val oldIndex = routineDao.removeExerciseAndRenumber(routineId, oldExerciseId)
            if (!routineHasNew) routineDao.insertExerciseWithSets(routineId, newExerciseId, sets, atIndex = oldIndex)
        }
    }

    suspend fun deleteWorkout(workoutId: Long) = dao.deleteWorkout(workoutId)

    /**
     * Saves the workout: carries what was done into its routine (spec 5.7) and marks
     * it finished. Unticked sets stay in place so the workout can be reopened that day.
     */
    suspend fun finish(workoutId: Long) = db.withTransaction { finishInTransaction(workoutId) }

    /** "Continue workout": back to in progress (spec 5.6). */
    suspend fun reopen(workoutId: Long) = db.withTransaction {
        val row = dao.findRow(workoutId) ?: return@withTransaction
        dao.updateWorkout(row.copy(status = WorkoutStatus.IN_PROGRESS))
    }

    /**
     * Day close (spec 5.6) for every workout dated before [today]. One still in
     * progress is saved as [finish] does. Then its unticked sets are deleted,
     * exercises left without sets are deleted, and a workout left empty is deleted.
     * Running it again changes nothing.
     */
    suspend fun closeDaysBefore(today: LocalDate) = db.withTransaction {
        for (row in dao.needingCloseBefore(today)) {
            if (row.status == WorkoutStatus.IN_PROGRESS) finishInTransaction(row.id)
            dao.deleteUntickedSets(row.id)
            for (link in dao.exercisesOf(row.id)) {
                val sets = dao.setsOf(link.id)
                if (sets.isEmpty()) {
                    dao.deleteExercise(link.id)
                } else {
                    dao.updateSets(sets.mapIndexed { index, set -> set.copy(position = index) })
                }
            }
            val remaining = dao.exercisesOf(row.id)
            if (remaining.isEmpty()) {
                dao.deleteWorkout(row.id)
            } else {
                dao.updateExercises(remaining.mapIndexed { index, link -> link.copy(position = index) })
            }
        }
    }

    /**
     * History: finished workouts, ascending by date, holding ticked sets only.
     * Exercises and workouts without a ticked set are left out.
     */
    fun observeSaved(): Flow<List<Workout>> =
        dao.observeFinished().map { list -> list.mapNotNull { it.toDomain().withTickedSetsOnly() } }.distinctUntilChanged()

    private suspend fun finishInTransaction(workoutId: Long) {
        val workout = dao.get(workoutId)?.toDomain() ?: return
        carryIntoRoutine(workout)
        val row = dao.findRow(workoutId) ?: return
        dao.updateWorkout(row.copy(status = WorkoutStatus.FINISHED))
    }

    /**
     * For each exercise that has a ticked set and is still in the routine, the routine's
     * starting sets become what [nextStartingSets] says. Never adds or removes an
     * exercise, and does nothing once the routine is gone.
     */
    private suspend fun carryIntoRoutine(workout: Workout) {
        val routineId = workout.routineId ?: return
        for (exercise in workout.exercises) {
            val link = routineDao.findExercise(routineId, exercise.exercise.id) ?: continue
            val routineSets = routineDao.setsOf(link.id).map { SetValues(it.weightHg, it.reps, it.minutes) }
            val next = nextStartingSets(routineSets, exercise.sets) ?: continue
            routineDao.replaceSets(link.id, next)
        }
    }

    private fun Workout.withTickedSetsOnly(): Workout? {
        val ticked = exercises
            .map { it.copy(sets = it.sets.filter { set -> set.done }) }
            .filter { it.sets.isNotEmpty() }
        return if (ticked.isEmpty()) null else copy(exercises = ticked)
    }

    private suspend fun createFromRoutine(date: LocalDate, routineId: Long): Long {
        val routine = requireNotNull(routineDao.getRoutine(routineId)) { "No routine $routineId" }.toDomain()
        val workoutId = dao.insertWorkout(
            WorkoutEntity(
                date = date,
                routineId = routineId,
                routineName = routine.name,
                status = WorkoutStatus.IN_PROGRESS,
                bodyWeightHg = profileDao.latestBodyWeightHg(),
            ),
        )
        routine.exercises.forEachIndexed { index, exercise ->
            insertExercise(workoutId, exercise.exercise.id, index, exercise.sets)
        }
        return workoutId
    }

    private suspend fun insertExercise(workoutId: Long, exerciseId: String, position: Int, sets: List<SetValues>) {
        val linkId = dao.insertExercise(
            WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, position = position),
        )
        dao.insertSets(
            sets.mapIndexed { index, values ->
                WorkoutSetEntity(
                    workoutExerciseId = linkId,
                    position = index,
                    weightHg = values.weightHg,
                    reps = values.reps,
                    minutes = values.minutes,
                    done = false,
                )
            },
        )
    }

    private suspend fun deleteExerciseAndRenumber(workoutId: Long, link: WorkoutExerciseEntity) {
        dao.deleteExercise(link.id)
        dao.updateExercises(dao.exercisesOf(workoutId).mapIndexed { index, other -> other.copy(position = index) })
    }

    private suspend fun findSet(workoutId: Long, exerciseId: String, position: Int): WorkoutSetEntity? {
        val link = dao.findExercise(workoutId, exerciseId) ?: return null
        return dao.setsOf(link.id).firstOrNull { it.position == position }
    }

    /** The workout's routine, or null if it has been deleted. */
    private suspend fun routineOf(workoutId: Long): Long? = dao.findRow(workoutId)?.routineId
}

internal fun WorkoutWithExercises.toDomain() = Workout(
    id = workout.id,
    date = workout.date,
    routineId = workout.routineId,
    routineName = workout.routineName,
    status = workout.status,
    bodyWeightHg = workout.bodyWeightHg,
    exercises = exercises.sortedWith(compareBy({ it.link.position }, { it.link.id })).map { item ->
        WorkoutExercise(
            exercise = item.exercise.toDomain(),
            sets = item.sets.sortedWith(compareBy({ it.position }, { it.id })).map {
                WorkoutSet(it.position, SetValues(it.weightHg, it.reps, it.minutes), it.done)
            },
        )
    },
)
