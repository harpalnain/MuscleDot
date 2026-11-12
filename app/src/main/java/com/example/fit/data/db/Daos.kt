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
