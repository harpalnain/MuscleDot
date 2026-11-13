package com.example.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FIRST_REPEAT_MS = 400L
private const val FAST_FROM_MS = 1500L
private const val SLOW_INTERVAL_MS = 150L
private const val FAST_INTERVAL_MS = 50L

/** Height of every stepper control, and the width of its − and + buttons. */
val StepperHeight = 48.dp
val StepButtonWidth = 40.dp

/**
 * Milliseconds until the next repeat for a button that has been held [heldMs], or
 * null while it should not repeat yet. Holding repeats slowly first, then fast, so
 * large changes stay practical without typing (spec 5.4).
 */
fun repeatIntervalMs(heldMs: Long): Long? = when {
    heldMs < FIRST_REPEAT_MS -> null
    heldMs < FAST_FROM_MS -> SLOW_INTERVAL_MS
    else -> FAST_INTERVAL_MS
}

/**
 * What one press of a − or + button does, from finger down to its end.
 *
 * A tap steps once, when the finger lifts. A hold steps once it has lasted
 * [FIRST_REPEAT_MS] and then keeps stepping on the schedule of [repeatIntervalMs].
 * A press that is taken away before that (a scroll started under the finger, the
 * button was disabled or left the screen) steps nothing, so starting to scroll on a
 * button never changes a number.
 */
class StepPress(private val onStep: () -> Unit) {
    private var held = false

    /** Runs while the finger stays down. Cancel it when the press ends, however it ends. */
    suspend fun hold() {
        delay(FIRST_REPEAT_MS)
        held = true
        var heldMs = FIRST_REPEAT_MS
        while (true) {
            onStep()
            val wait = repeatIntervalMs(heldMs) ?: break
            delay(wait)
            heldMs += wait
        }
    }

    /** The finger lifted. A press that never became a hold was a tap, and steps once. */
    fun lift() {
        if (!held) onStep()
    }
}

/**
 * A − or + button. A tap steps once; holding it steps repeatedly, slowly and then
 * fast, until it is released. See [StepPress].
 */
@Composable
fun StepButton(
    symbol: String,
    contentDescription: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val currentOnStep by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    val interactions = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(StepButtonWidth)
            .height(StepperHeight)
            .background(colors.primaryContainer)
            .indication(interactions, ripple())
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (enabled) {
                    onClick {
                        currentOnStep()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pressed = PressInteraction.Press(down.position)
                    interactions.tryEmit(pressed)
                    val press = StepPress { currentOnStep() }
                    val holding = scope.launch { press.hold() }
                    var lifted = false
                    try {
                        lifted = waitForUpOrCancellation() != null
                    } finally {
                        // Reached however the press ends, also when this gesture is cut short
                        // because the button was disabled while held: the repeats must stop.
                        holding.cancel()
                        if (lifted) press.lift()
                        interactions.tryEmit(if (lifted) PressInteraction.Release(pressed) else PressInteraction.Cancel(pressed))
                    }
                }
            },
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

/**
 * A number between a − and a + button. [onStep] gets -1 or +1. The number itself
 * cannot be typed: workout numbers change with the buttons only.
 */
@Composable
fun NumberStepper(
    text: String,
    decreaseDescription: String,
    increaseDescription: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(StepperHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        StepButton("−", decreaseDescription, onStep = { onStep(-1) }, enabled = canDecrease)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        StepButton("+", increaseDescription, onStep = { onStep(+1) }, enabled = canIncrease)
    }
}
