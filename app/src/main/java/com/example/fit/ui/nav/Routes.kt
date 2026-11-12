package com.example.fit.ui.nav

import kotlinx.serialization.Serializable

@Serializable
object WelcomeRoute

@Serializable
object TodayRoute

@Serializable
object RoutinesRoute

@Serializable
object ProgressRoute

@Serializable
object HistoryRoute

@Serializable
object ProfileRoute

@Serializable
data class RoutineEditorRoute(val routineId: Long)

/** The routine or workout whose exercises the picker shows as already added. */
@Serializable
data class ExercisePickerRoute(val routineId: Long? = null, val workoutId: Long? = null)

@Serializable
data class ExerciseDetailRoute(val exerciseId: String)

@Serializable
data class WorkoutDetailRoute(val workoutId: Long)

/** The picker leaves the chosen exercise id under this key in the previous entry's SavedStateHandle. */
const val PICKED_EXERCISE_KEY = "pickedExerciseId"
