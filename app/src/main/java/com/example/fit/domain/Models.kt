package com.example.fit.domain

import java.time.LocalDate

/** How an exercise is logged and whether it counts toward load (spec 5.1). */
enum class ExerciseKind { WEIGHTS, BODY_WEIGHT, REPS_ONLY, CARDIO }

enum class MuscleGroup { CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO }

data class Exercise(
    val id: String,
    val name: String,
    val group: MuscleGroup,
    /** Specific muscle such as "Triceps"; null for custom exercises. */
    val muscle: String?,
    /** "barbell", "dumbbell", "cable", "machine", "body only", "other"; null for custom exercises. */
    val equipment: String?,
    val kind: ExerciseKind,
    val isCustom: Boolean = false,
    val isHidden: Boolean = false,
    /** Asset path stem such as "exercises/bench-press"; null means the muscle-group icon is shown. */
    val imageBase: String? = null,
)

/**
 * The numbers of one set. [weightHg] is in hectograms (1 hg = 0.1 kg); for a
 * [ExerciseKind.BODY_WEIGHT] exercise it is the extra weight. Unused fields stay 0.
 */
data class SetValues(val weightHg: Int = 0, val reps: Int = 0, val minutes: Int = 0)

data class RoutineExercise(val exercise: Exercise, val sets: List<SetValues>)

data class Routine(val id: Long, val name: String, val exercises: List<RoutineExercise>)

data class WorkoutSet(val position: Int, val values: SetValues, val done: Boolean)

data class WorkoutExercise(val exercise: Exercise, val sets: List<WorkoutSet>)

enum class WorkoutStatus { IN_PROGRESS, FINISHED }

data class Workout(
    val id: Long,
    /** The app day the workout belongs to. */
    val date: LocalDate,
    /** Null once the routine has been deleted. */
    val routineId: Long?,
    /** The routine's name when the workout was created. */
    val routineName: String,
    val status: WorkoutStatus,
    /** Body weight that applied on the workout's day, used for body weight exercises. */
    val bodyWeightHg: Int?,
    val exercises: List<WorkoutExercise>,
)

data class Profile(
    val name: String = "",
    val dateOfBirth: LocalDate? = null,
    val heightFeet: Int? = null,
    val heightInches: Int? = null,
    val welcomeDone: Boolean = false,
)

data class BodyWeightEntry(val date: LocalDate, val weightHg: Int)
