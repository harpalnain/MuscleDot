package com.example.fit.data

import androidx.room.withTransaction
import com.example.fit.data.db.AppDatabase
import com.example.fit.data.db.ExerciseEntity
import com.example.fit.data.db.toEntity
import com.example.fit.domain.Profile
import kotlinx.serialization.json.Json

/**
 * Puts the built-in exercises into the database, and on the very first run the
 * starter routines and schedule. [readExercisesJson] returns the text of
 * `assets/exercises.json`.
 */
class Seeder(private val db: AppDatabase, private val readExercisesJson: () -> String) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() = db.withTransaction {
        val settings = db.profileDao()

        // Built-in exercises are refreshed whenever the app ships a new list. Custom
        // exercises and everything the user made are never touched.
        if (settings.getSetting(SEED_VERSION_KEY) != SEED_VERSION.toString()) {
            val builtIns: List<BuiltInExercise> = json.decodeFromString(readExercisesJson())
            db.exerciseDao().upsertAll(builtIns.map { it.toEntity() })
            settings.putSetting(SEED_VERSION_KEY, SEED_VERSION.toString())
        }

        // No profile row means this is the first run. Later runs never recreate
        // starter content the user changed or deleted.
        if (settings.getProfile() == null) {
            settings.upsertProfile(Profile().toEntity())
            seedStarterRoutines()
        }
    }

    private suspend fun seedStarterRoutines() {
        val routines = RoutineRepository(db, StartingSets(db))
        val idsByName = STARTER_ROUTINES.associate { (name, exerciseIds) ->
            val routineId = routines.createRoutine(name)
            exerciseIds.forEach { routines.addExercise(routineId, it) }
            name to routineId
        }
        for ((day, routineName) in STARTER_SCHEDULE) {
            routines.setScheduleDay(day, routineName?.let { idsByName.getValue(it) })
        }
    }

    private fun BuiltInExercise.toEntity() = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = group,
        muscle = muscle,
        equipment = equipment,
        kind = kind,
        isCustom = false,
        isHidden = false,
        imageBase = "$IMAGE_FOLDER/$id",
    )

    companion object {
        /** Raise this whenever `assets/exercises.json` changes, so installed apps pick up the new list. */
        const val SEED_VERSION = 1
        const val SEED_VERSION_KEY = "seedVersion"
        const val IMAGE_FOLDER = "exercises"
    }
}
