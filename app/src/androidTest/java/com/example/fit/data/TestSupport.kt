package com.example.fit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.fit.data.db.AppDatabase
import com.example.fit.data.db.toEntity
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()

/** One exercise of each kind, enough for repository tests. */
val TEST_EXERCISES = listOf(
    Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS, imageBase = "exercises/bench-press"),
    Exercise("pull-up", "Pull-up", MuscleGroup.BACK, "Lats", "body only", ExerciseKind.BODY_WEIGHT, imageBase = "exercises/pull-up"),
    Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abdominals", "body only", ExerciseKind.REPS_ONLY, imageBase = "exercises/crunches"),
    Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO, imageBase = "exercises/treadmill"),
)

suspend fun seedTestExercises(db: AppDatabase) = db.exerciseDao().upsertAll(TEST_EXERCISES.map { it.toEntity() })
