package com.example.fit.data.backup

import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.WorkoutStatus
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Written inside every backup file and checked on restore. It keeps the app's
 * first name ("Overload") so backups made before the rename still open.
 */
const val BACKUP_FORMAT = "overload-backup"

/** Raise when the file shape changes; older files must stay readable. */
const val BACKUP_VERSION = 1

/**
 * Everything the user created (spec 5.13), as one JSON file. Dates are ISO text,
 * enums are names, weights are hectograms. Lists are in display order.
 */
@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val exportedAt: String,
    val profile: BackupProfile,
    val bodyWeights: List<BackupBodyWeight>,
    val settings: Map<String, String>,
    /** Every custom exercise, and every exercise a routine or workout refers to. */
    val exercises: List<BackupExercise>,
    val routines: List<BackupRoutine>,
    val schedule: List<BackupScheduleDay>,
    val workouts: List<BackupWorkout>,
)

@Serializable
data class BackupProfile(
    val name: String,
    val dateOfBirth: String?,
    val heightFeet: Int?,
    val heightInches: Int?,
    val welcomeDone: Boolean,
)

@Serializable
data class BackupBodyWeight(val date: String, val weightHg: Int)

@Serializable
data class BackupExercise(
    val id: String,
    val name: String,
    val group: MuscleGroup,
    val muscle: String?,
    val equipment: String?,
    val kind: ExerciseKind,
    val isCustom: Boolean,
    val isHidden: Boolean,
    val imageBase: String?,
)

/** [done] only matters for workout sets. */
@Serializable
data class BackupSet(val weightHg: Int, val reps: Int, val minutes: Int, val done: Boolean = false)

@Serializable
data class BackupRoutineExercise(val exerciseId: String, val sets: List<BackupSet>)

@Serializable
data class BackupRoutine(val id: Long, val name: String, val exercises: List<BackupRoutineExercise>)

@Serializable
data class BackupScheduleDay(val dayOfWeek: Int, val routineId: Long?)

@Serializable
data class BackupWorkoutExercise(val exerciseId: String, val sets: List<BackupSet>)

@Serializable
data class BackupWorkout(
    val id: Long,
    val date: String,
    val routineId: Long?,
    val routineName: String,
    val status: WorkoutStatus,
    val bodyWeightHg: Int?,
    val exercises: List<BackupWorkoutExercise>,
)

/** "muscledot-backup-2026-11-12.json" */
fun backupFileName(date: LocalDate): String = "muscledot-backup-$date.json"
