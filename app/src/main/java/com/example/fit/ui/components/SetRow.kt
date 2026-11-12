package com.example.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fit.R
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.domain.formatStepWeight
import com.example.fit.ui.theme.LocalOverloadColors

/**
 * One set: its number, its figures with − and + (weight and reps, reps alone for a
 * reps only exercise, minutes for cardio), and at the end a done tick or a remove
 * button. Used on Today, in the routine editor and when editing a saved workout.
 *
 * [onStep] gets the field and -1 or +1. The tick is shown when [done] and
 * [onToggleDone] are both given; the remove button when [onRemove] is given.
 */
@Composable
fun SetRow(
    number: Int,
    kind: ExerciseKind,
    values: SetValues,
    onStep: (SetField, Int) -> Unit,
    modifier: Modifier = Modifier,
    done: Boolean? = null,
    onToggleDone: (() -> Unit)? = null,
    isRecord: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (kind == ExerciseKind.CARDIO) "" else number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(16.dp),
            )
            when (kind) {
                ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> {
                    NumberStepper(
                        text = formatStepWeight(values.weightHg),
                        unit = stringResource(if (kind == ExerciseKind.BODY_WEIGHT) R.string.unit_extra_kg else R.string.unit_kg),
                        decreaseDescription = stringResource(R.string.set_decrease_weight, number),
                        increaseDescription = stringResource(R.string.set_increase_weight, number),
                        onStep = { onStep(SetField.WEIGHT, it) },
                        modifier = Modifier.weight(1.2f),
                    )
                    RepsStepper(number, values.reps, onStep, Modifier.weight(1f))
                }

                ExerciseKind.REPS_ONLY -> {
                    RepsStepper(number, values.reps, onStep, Modifier.weight(1f))
                    Spacer(Modifier.weight(1.2f))
                }

                ExerciseKind.CARDIO -> {
                    NumberStepper(
                        text = values.minutes.toString(),
                        unit = stringResource(R.string.unit_min),
                        decreaseDescription = stringResource(R.string.set_decrease_minutes),
                        increaseDescription = stringResource(R.string.set_increase_minutes),
                        onStep = { onStep(SetField.MINUTES, it) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.weight(1.2f))
                }
            }
            if (done != null && onToggleDone != null) {
                DoneTick(number, done, onToggleDone)
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.set_remove, number),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isRecord) {
            val colors = LocalOverloadColors.current
            Text(
                text = stringResource(R.string.new_record),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onRecordContainer,
                modifier = Modifier
                    .padding(start = 22.dp, top = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.recordContainer)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun RepsStepper(number: Int, reps: Int, onStep: (SetField, Int) -> Unit, modifier: Modifier) {
    NumberStepper(
        text = reps.toString(),
        unit = stringResource(R.string.unit_reps),
        decreaseDescription = stringResource(R.string.set_decrease_reps, number),
        increaseDescription = stringResource(R.string.set_increase_reps, number),
        onStep = { onStep(SetField.REPS, it) },
        modifier = modifier,
    )
}

/** The "done" tick of a set: one tap, with a touch target of 48 dp. */
@Composable
private fun DoneTick(number: Int, done: Boolean, onToggle: () -> Unit) {
    val description = stringResource(R.string.set_done, number)
    val doneColor = LocalOverloadColors.current.done
    val shape = RoundedCornerShape(9.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(shape)
                .background(if (done) doneColor else Color.Transparent)
                .border(2.dp, if (done) doneColor else MaterialTheme.colorScheme.outline, shape),
        ) {
            if (done) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
