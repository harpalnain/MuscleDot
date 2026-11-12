package com.example.fit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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
