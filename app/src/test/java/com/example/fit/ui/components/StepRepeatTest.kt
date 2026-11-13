package com.example.fit.ui.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StepRepeatTest {
    @Test
    fun schedule() {
        assertNull(repeatIntervalMs(0))
        assertNull(repeatIntervalMs(399))
        assertEquals(150L, repeatIntervalMs(400))
        assertEquals(150L, repeatIntervalMs(1499))
        assertEquals(50L, repeatIntervalMs(1500))
        assertEquals(50L, repeatIntervalMs(60_000))
    }

    @Test
    fun aTapStepsOnce_whenTheFingerLifts() = runTest {
        var steps = 0
        val press = StepPress { steps++ }
        val holding = launch { press.hold() }
        advanceTimeBy(100)
        assertEquals(0, steps) // nothing yet: it could still turn into a scroll
        holding.cancel()
        press.lift()
        assertEquals(1, steps)
    }

    @Test
    fun aPressTakenAwayBeforeTheHoldStepsNothing() = runTest {
        var steps = 0
        val press = StepPress { steps++ }
        val holding = launch { press.hold() }
        advanceTimeBy(100)
        holding.cancel() // a scroll took the finger over: the press ends without a lift
        advanceTimeBy(5_000)
        assertEquals(0, steps)
    }

    @Test
    fun aHoldStepsAtTheThresholdThenRepeats_andLiftingAddsNothing() = runTest {
        var steps = 0
        val press = StepPress { steps++ }
        val holding = launch { press.hold() }
        advanceTimeBy(399)
        assertEquals(0, steps)
        advanceTimeBy(2)
        assertEquals(1, steps) // the hold starts at 400 ms
        advanceTimeBy(1_099) // now at 1.5 s: 400, 550, 700, 850, 1000, 1150, 1300, 1450
        assertEquals(8, steps)
        advanceTimeBy(1_000) // fast repeats, every 50 ms
        assertTrue("$steps", steps in 26..30)
        val atRelease = steps
        holding.cancel()
        press.lift()
        advanceTimeBy(5_000)
        assertEquals(atRelease, steps)
    }

    @Test
    fun endingAHoldStopsTheRepeats() = runTest {
        var steps = 0
        val press = StepPress { steps++ }
        val holding = launch { press.hold() }
        advanceTimeBy(1_000)
        val before = steps
        assertTrue("$before", before >= 4)
        holding.cancel() // the button was disabled or left the screen while held
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(before, steps)
    }
}
