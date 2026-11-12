package com.example.fit

import android.content.Context
import com.example.fit.data.ExerciseRepository
import com.example.fit.data.ProfileRepository
import com.example.fit.data.RoutineRepository
import com.example.fit.data.Seeder
import com.example.fit.data.StartingSets
import com.example.fit.data.TodayHolder
import com.example.fit.data.WorkoutRepository
import com.example.fit.data.backup.BackupRepository
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

/**
 * Everything the screens depend on, built once by hand. Tests build their own
 * with [inMemory] and a clock they control.
 */
class AppContainer(
    context: Context,
    inMemory: Boolean = false,
    val clock: AppClock = AppClock { LocalDateTime.now() },
) {
    private val appContext = context.applicationContext

    val db: AppDatabase = AppDatabase.create(appContext, inMemory)
    private val startingSets = StartingSets(db)
    val exercises = ExerciseRepository(db)
    val profile = ProfileRepository(db)
    val routines = RoutineRepository(db, startingSets)
    val workouts = WorkoutRepository(db, startingSets)
    val backup = BackupRepository(db)
    val seeder = Seeder(db) { appContext.assets.open(EXERCISES_ASSET).bufferedReader().use { it.readText() } }
    val today = TodayHolder(clock)

    private val isReady = MutableStateFlow(false)

    /** True once the database is seeded and past days are closed; screens wait for it. */
    val ready: StateFlow<Boolean> = isReady.asStateFlow()

    suspend fun initialize() {
        seeder.seedIfNeeded()
        onForeground()
        isReady.value = true
    }

    /** Moves to the current app day and saves workouts left over from earlier days (spec 5.6). */
    suspend fun onForeground() {
        today.refresh()
        workouts.closeDaysBefore(today.date.value)
    }

    private companion object {
        const val EXERCISES_ASSET = "exercises.json"
    }
}
