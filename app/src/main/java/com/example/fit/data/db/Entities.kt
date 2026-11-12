package com.example.fit.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.Profile
import com.example.fit.domain.WorkoutStatus
import java.time.LocalDate

// Every table of schema version 1. Weights are hectograms (1 hg = 0.1 kg), dates are
// app days stored as ISO text, enums are stored by name.

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val muscle: String?,
    val equipment: String?,
    val kind: ExerciseKind,
    val isCustom: Boolean,
    val isHidden: Boolean,
    val imageBase: String?,
)

fun Exercise.toEntity() = ExerciseEntity(id, name, group, muscle, equipment, kind, isCustom, isHidden, imageBase)

fun ExerciseEntity.toDomain() = Exercise(id, name, muscleGroup, muscle, equipment, kind, isCustom, isHidden, imageBase)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index(value = ["routineId", "exerciseId"], unique = true), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: String,
    val position: Int,
)

/** One starting set of an exercise in a routine. */
@Entity(
    tableName = "routine_set",
    foreignKeys = [
        ForeignKey(
            entity = RoutineExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineExerciseId")],
)
data class RoutineSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineExerciseId: Long,
    val position: Int,
    val weightHg: Int,
    val reps: Int,
    val minutes: Int,
)

/** The weekly schedule: [dayOfWeek] is 1 (Monday) to 7 (Sunday); no routine means a rest day. */
@Entity(
    tableName = "schedule_day",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId")],
)
data class ScheduleDayEntity(
    @PrimaryKey val dayOfWeek: Int,
    val routineId: Long?,
)

/** At most one workout per app day. It outlives its routine, keeping the routine's name. */
@Entity(
    tableName = "workout",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["date"], unique = true), Index("routineId")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val routineId: Long?,
    val routineName: String,
    val status: WorkoutStatus,
    val bodyWeightHg: Int?,
)

@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index(value = ["workoutId", "exerciseId"], unique = true), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val position: Int,
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val position: Int,
    val weightHg: Int,
    val reps: Int,
    val minutes: Int,
    val done: Boolean,
)

/** A single row, always id 1. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = PROFILE_ID,
    val name: String,
    val dateOfBirth: LocalDate?,
    val heightFeet: Int?,
    val heightInches: Int?,
    val welcomeDone: Boolean,
)

const val PROFILE_ID = 1

fun Profile.toEntity() = ProfileEntity(PROFILE_ID, name, dateOfBirth, heightFeet, heightInches, welcomeDone)

fun ProfileEntity.toDomain() = Profile(name, dateOfBirth, heightFeet, heightInches, welcomeDone)

/** One body weight per app day. */
@Entity(tableName = "body_weight")
data class BodyWeightEntity(
    @PrimaryKey val date: LocalDate,
    val weightHg: Int,
)

@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
