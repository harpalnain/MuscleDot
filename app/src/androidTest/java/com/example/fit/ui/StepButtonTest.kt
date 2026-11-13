package com.example.fit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.fit.domain.MAX_AVERAGE_N
import com.example.fit.domain.MIN_AVERAGE_N
import com.example.fit.ui.components.AverageNControl
import com.example.fit.ui.components.NumberStepper
import com.example.fit.ui.components.StepButton
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StepButtonTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tapStepsOnce() {
        var steps = 0
        rule.setContent { OverloadTheme { StepButton("+", "Increase weight", onStep = { steps++ }) } }
        rule.onNodeWithContentDescription("Increase weight").performClick()
        assertEquals(1, steps)
    }

    @Test
    fun holdingRepeatsThenSpeedsUp_andStopsOnRelease() {
        var steps = 0
        rule.setContent { OverloadTheme { StepButton("+", "Increase weight", onStep = { steps++ }) } }
        rule.mainClock.autoAdvance = false
        val button = rule.onNodeWithContentDescription("Increase weight")
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(350)
        assertEquals(0, steps) // not a tap and not yet a hold: nothing so far
        rule.mainClock.advanceTimeBy(1150)
        assertTrue("$steps", steps in 6..10) // slow repeats until 1.5 s
        rule.mainClock.advanceTimeBy(1000)
        assertTrue("$steps", steps in 22..32) // fast repeats after
        button.performTouchInput { up() }
        val atRelease = steps
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(atRelease, steps)
    }

    @Test
    fun aScrollThatStartsOnAButtonChangesNothing() {
        var steps = 0
        rule.setContent {
            OverloadTheme {
                Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(200.dp))
                    StepButton("+", "Increase weight", onStep = { steps++ })
                    Spacer(Modifier.height(1000.dp))
                }
            }
        }
        rule.onNodeWithContentDescription("Increase weight").performTouchInput {
            down(center)
            moveBy(Offset(0f, -150f)) // well past the touch slop: the list takes the finger over
            moveBy(Offset(0f, -150f))
            up()
        }
        rule.runOnIdle { assertEquals(0, steps) }
    }

    @Test
    fun holdingToTheLimitStops_andTheOtherButtonStillWorks() {
        var n by mutableIntStateOf(18)
        var changes = 0
        rule.setContent {
            OverloadTheme {
                AverageNControl(
                    n = n,
                    onChange = {
                        changes++
                        n = it.coerceIn(MIN_AVERAGE_N, MAX_AVERAGE_N)
                    },
                )
            }
        }
        rule.mainClock.autoAdvance = false
        val more = rule.onNodeWithContentDescription("More sessions")
        more.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000) // 19 at 400 ms and 20 at 550 ms: the limit, where + switches off
        assertEquals(20, n)
        val atLimit = changes
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(atLimit, changes) // the hold has ended; nothing keeps asking for 21
        more.performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Fewer sessions").performClick()
        rule.runOnIdle { assertEquals(19, n) }
    }

    @Test
    fun numberStepperReportsDirection() {
        val seen = mutableListOf<Int>()
        rule.setContent {
            OverloadTheme {
                NumberStepper("40.0", "Decrease weight", "Increase weight", onStep = { seen += it }, unit = "kg")
            }
        }
        rule.onNodeWithText("40.0").assertIsDisplayed()
        rule.onNodeWithContentDescription("Decrease weight").performClick()
        rule.onNodeWithContentDescription("Increase weight").performClick()
        assertEquals(listOf(-1, +1), seen)
    }
}
