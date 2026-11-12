package com.example.fit.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExerciseDao {
    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insert(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercise WHERE isHidden = 0 ORDER BY name COLLATE NOCASE")
    fun observeVisible(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("UPDATE exercise SET name = :name WHERE id = :id AND isCustom = 1")
    suspend fun renameCustom(id: String, name: String)

    @Query("DELETE FROM routine_exercise WHERE exerciseId = :id")
    suspend fun removeFromRoutines(id: String)

    @Query("SELECT COUNT(*) FROM workout_exercise WHERE exerciseId = :id")
    suspend fun workoutUseCount(id: String): Int

    @Query("UPDATE exercise SET isHidden = 1 WHERE id = :id")
    suspend fun hide(id: String)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Removes a custom exercise from every routine. If a workout still refers to it,
     * it is only hidden, so history, charts and records keep it (spec 5.1).
     */
    @Transaction
    suspend fun deleteCustom(id: String) {
        val exercise = get(id) ?: return
        if (!exercise.isCustom) return
        removeFromRoutines(id)
        if (workoutUseCount(id) > 0) hide(id) else delete(id)
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = $PROFILE_ID")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = $PROFILE_ID")
    suspend fun getProfile(): ProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM body_weight ORDER BY date")
    fun observeBodyWeights(): Flow<List<BodyWeightEntity>>

    @Query("SELECT weightHg FROM body_weight ORDER BY date DESC LIMIT 1")
    suspend fun latestBodyWeightHg(): Int?

    @Upsert
    suspend fun upsertBodyWeight(entry: BodyWeightEntity)

    @Query("UPDATE workout SET bodyWeightHg = :weightHg WHERE date = :date")
    suspend fun setWorkoutBodyWeight(date: LocalDate, weightHg: Int)

    /**
     * One entry per day. A workout on that day takes the new body weight too, so
     * the body weight that applied on its day is what its loads use (spec 5.4).
     */
    @Transaction
    suspend fun setBodyWeight(date: LocalDate, weightHg: Int) {
        upsertBodyWeight(BodyWeightEntity(date, weightHg))
        setWorkoutBodyWeight(date, weightHg)
    }

    @Query("SELECT value FROM setting WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Query("SELECT value FROM setting WHERE `key` = :key")
    fun observeSetting(key: String): Flow<String?>

    @Upsert
    suspend fun upsertSetting(setting: SettingEntity)

    suspend fun putSetting(key: String, value: String) = upsertSetting(SettingEntity(key, value))
}

/** One exercise of a routine with its exercise row and its starting sets. Lists are not ordered. */
data class RoutineExerciseWithSets(
    @Embedded val link: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "routineExerciseId") val sets: List<RoutineSetEntity>,
)

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(entity = RoutineExerciseEntity::class, parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExerciseWithSets>,
)

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routine ORDER BY position, id")
    fun observeRoutines(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    fun observeRoutine(id: Long): Flow<RoutineWithExercises?>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutine(id: Long): RoutineWithExercises?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM routine")
    suspend fun nextRoutinePosition(): Int

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Query("UPDATE routine SET name = :name WHERE id = :id")
    suspend fun renameRoutine(id: Long, name: String)

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY position, id")
    suspend fun exercisesOf(routineId: Long): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun findExercise(routineId: Long, exerciseId: String): RoutineExerciseEntity?

    @Insert
    suspend fun insertExercise(link: RoutineExerciseEntity): Long

    @Update
    suspend fun updateExercises(links: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("SELECT * FROM routine_set WHERE routineExerciseId = :routineExerciseId ORDER BY position, id")
    suspend fun setsOf(routineExerciseId: Long): List<RoutineSetEntity>

    @Insert
    suspend fun insertSets(sets: List<RoutineSetEntity>)

    @Update
    suspend fun updateSets(sets: List<RoutineSetEntity>)

    @Query("DELETE FROM routine_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM routine_set WHERE routineExerciseId = :routineExerciseId")
    suspend fun deleteSetsOf(routineExerciseId: Long)

    @Query("SELECT * FROM schedule_day")
    fun observeSchedule(): Flow<List<ScheduleDayEntity>>

    @Upsert
    suspend fun upsertScheduleDay(day: ScheduleDayEntity)

    /**
     * The ticked sets of [exerciseId] from the most recent finished workout in which
     * it has a ticked set, in order. Empty if it has never been done.
     */
    @Query(
        """
        SELECT s.* FROM workout_set s
        WHERE s.done = 1 AND s.workoutExerciseId = (
            SELECT we.id FROM workout_exercise we
            JOIN workout w ON w.id = we.workoutId
            WHERE we.exerciseId = :exerciseId AND w.status = 'FINISHED'
              AND EXISTS (SELECT 1 FROM workout_set t WHERE t.workoutExerciseId = we.id AND t.done = 1)
            ORDER BY w.date DESC LIMIT 1
        )
        ORDER BY s.position, s.id
        """,
    )
    suspend fun latestFinishedSets(exerciseId: String): List<WorkoutSetEntity>
}

/** One exercise of a workout with its exercise row and its sets. Lists are not ordered. */
data class WorkoutExerciseWithSets(
    @Embedded val link: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId") val sets: List<WorkoutSetEntity>,
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(entity = WorkoutExerciseEntity::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseWithSets>,
)

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout WHERE date = :date")
    fun observeOn(date: LocalDate): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun get(id: Long): WorkoutWithExercises?

    @Query("SELECT * FROM workout WHERE date = :date")
    suspend fun findOn(date: LocalDate): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun findRow(id: Long): WorkoutEntity?

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position, id")
    suspend fun exercisesOf(workoutId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun findExercise(workoutId: Long, exerciseId: String): WorkoutExerciseEntity?

    @Insert
    suspend fun insertExercise(link: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateExercises(links: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY position, id")
    suspend fun setsOf(workoutExerciseId: Long): List<WorkoutSetEntity>

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSets(sets: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query(
        """
        SELECT COUNT(*) FROM workout_set s
        JOIN workout_exercise we ON we.id = s.workoutExerciseId
        WHERE we.workoutId = :workoutId AND s.done = 1
        """,
    )
    suspend fun doneSetCount(workoutId: Long): Int

    @Transaction
    @Query("SELECT * FROM workout WHERE status = 'FINISHED' ORDER BY date")
    fun observeFinished(): Flow<List<WorkoutWithExercises>>

    /**
     * Workouts dated before [date] that the day close still has work to do on: in
     * progress, holding an unticked set, or left without any exercise.
     */
    @Query(
        """
        SELECT * FROM workout w
        WHERE w.date < :date AND (
            w.status = 'IN_PROGRESS'
            OR EXISTS (
                SELECT 1 FROM workout_set s JOIN workout_exercise we ON we.id = s.workoutExerciseId
                WHERE we.workoutId = w.id AND s.done = 0
            )
            OR NOT EXISTS (SELECT 1 FROM workout_exercise we WHERE we.workoutId = w.id)
        )
        ORDER BY w.date
        """,
    )
    suspend fun needingCloseBefore(date: LocalDate): List<WorkoutEntity>

    @Query(
        """
        DELETE FROM workout_set
        WHERE done = 0 AND workoutExerciseId IN (SELECT id FROM workout_exercise WHERE workoutId = :workoutId)
        """,
    )
    suspend fun deleteUntickedSets(workoutId: Long)
}

/** Whole-database reads and wipes for backup export and import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM body_weight ORDER BY date")
    suspend fun bodyWeights(): List<BodyWeightEntity>

    @Query("SELECT * FROM setting ORDER BY `key`")
    suspend fun settings(): List<SettingEntity>

    @Query(
        """
        SELECT * FROM exercise
        WHERE isCustom = 1
           OR id IN (SELECT exerciseId FROM routine_exercise)
           OR id IN (SELECT exerciseId FROM workout_exercise)
        ORDER BY id
        """,
    )
    suspend fun exercisesToBackUp(): List<ExerciseEntity>

    @Transaction
    @Query("SELECT * FROM routine ORDER BY position, id")
    suspend fun routines(): List<RoutineWithExercises>

    @Query("SELECT * FROM schedule_day ORDER BY dayOfWeek")
    suspend fun schedule(): List<ScheduleDayEntity>

    @Transaction
    @Query("SELECT * FROM workout ORDER BY date")
    suspend fun workouts(): List<WorkoutWithExercises>

    @Query("SELECT id FROM exercise")
    suspend fun exerciseIds(): List<String>

    @Query("DELETE FROM workout")
    suspend fun deleteWorkouts()

    @Query("DELETE FROM schedule_day")
    suspend fun deleteSchedule()

    @Query("DELETE FROM routine")
    suspend fun deleteRoutines()

    @Query("DELETE FROM body_weight")
    suspend fun deleteBodyWeights()

    @Query("DELETE FROM profile")
    suspend fun deleteProfile()

    @Query("DELETE FROM setting WHERE `key` != :keep")
    suspend fun deleteSettingsExcept(keep: String)

    @Query("DELETE FROM exercise WHERE isCustom = 1")
    suspend fun deleteCustomExercises()

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insertBodyWeights(entries: List<BodyWeightEntity>)

    @Insert
    suspend fun insertSettings(settings: List<SettingEntity>)

    @Insert
    suspend fun insertSchedule(days: List<ScheduleDayEntity>)
}
