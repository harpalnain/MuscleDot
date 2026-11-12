package com.example.fit.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.fit.AppContainer
import com.example.fit.MainActivity
import com.example.fit.OverloadApp
import com.example.fit.domain.AppClock
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

/** A clock tests can move. */
class MutableClock(var now: LocalDateTime) : AppClock {
    override fun now() = now
}

/**
 * Swaps in an in-memory container with a controllable clock, then launches the
 * real activity. [prepare] runs after seeding and before the app starts.
 */
fun launchApp(clock: MutableClock, prepare: suspend (AppContainer) -> Unit = {}): ActivityScenario<MainActivity> {
    val app = ApplicationProvider.getApplicationContext<OverloadApp>()
    val container = AppContainer(app, inMemory = true, clock = clock)
    runBlocking {
        container.seeder.seedIfNeeded()
        prepare(container)
        container.initialize()
    }
    app.container = container
    return ActivityScenario.launch(MainActivity::class.java)
}
