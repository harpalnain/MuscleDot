package com.example.fit.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.fit.AppContainer
import com.example.fit.MainActivity
import com.example.fit.OverloadApp
import com.example.fit.domain.AppClock
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

/**
 * Waits until something with [tag] is on screen. Screens load their content from
 * the database in the background, which the Compose test clock does not wait for.
 */
fun ComposeTestRule.awaitTag(tag: String, timeoutMillis: Long = 10_000) {
    waitUntil(timeoutMillis) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}

/** Waits until [text] is on screen; see [awaitTag]. */
fun ComposeTestRule.awaitText(text: String, timeoutMillis: Long = 10_000) {
    waitUntil(timeoutMillis) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

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
