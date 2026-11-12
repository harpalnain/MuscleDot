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
import kotlinx.coroutines.flow.first
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

    private val welcomePending = MutableStateFlow<Boolean?>(null)

    /**
     * Null until the app has started (database seeded, past days closed). Then true
     * if the welcome screen has not been finished yet, so the app opens on it.
     * Screens wait for this, and it is known before anything is shown.
     */
    val startOnWelcome: StateFlow<Boolean?> = welcomePending.asStateFlow()

    suspend fun initialize() {
        seeder.seedIfNeeded()
        onForeground()
        welcomePending.value = !profile.observeProfile().first().welcomeDone
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
