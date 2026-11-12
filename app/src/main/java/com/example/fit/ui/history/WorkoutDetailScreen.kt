package com.example.fit.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.SetField
import com.example.fit.domain.Workout
import com.example.fit.domain.WorkoutExercise
import com.example.fit.domain.cardioMinutes
import com.example.fit.domain.doneSetCount
import com.example.fit.domain.formatDayLong
import com.example.fit.domain.formatKg
import com.example.fit.domain.loadHg
import com.example.fit.ui.components.ExerciseImage
import com.example.fit.ui.components.KindTag
import com.example.fit.ui.components.ScreenHeader
import com.example.fit.ui.components.SetRow
import com.example.fit.ui.components.StatTile
import com.example.fit.ui.overloadViewModel

/** One saved workout: its ticked sets, which can be corrected, added to, removed or deleted with it. */
@Composable
fun WorkoutDetailScreen(workoutId: Long, onBack: () -> Unit) {
    val viewModel = overloadViewModel(key = "workout-$workoutId") { WorkoutDetailViewModel(workoutId, it.workouts) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack) {
        object : WorkoutDetailActions {
            override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) =
                viewModel.step(exerciseId, position, field, steps)

            override fun removeSet(exerciseId: String, position: Int) = viewModel.removeSet(exerciseId, position)
            override fun addSet(exerciseId: String) = viewModel.addSet(exerciseId)
            override fun deleteWorkout() = viewModel.deleteWorkout()
            override fun back() = onBack()
        }
    }
    // Once the workout is deleted, here or by removing its last set, there is nothing to show.
    val gone = state is WorkoutDetailState.Gone
    LaunchedEffect(gone) { if (gone) onBack() }
    Box(Modifier.fillMaxSize().testTag("screen-workout-detail")) {
        (state as? WorkoutDetailState.Showing)?.let { WorkoutDetailContent(it.workout, actions) }
    }
}

/** [workout] holds its ticked sets only. */
@Composable
fun WorkoutDetailContent(workout: Workout, actions: WorkoutDetailActions, modifier: Modifier = Modifier) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val setCount = workout.doneSetCount()

    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = formatDayLong(workout.date), onBack = actions::back)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        ) {
            Text(
                text = workout.routineName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.today_summary_load), formatKg(workout.loadHg()), null, Modifier.weight(1f))
                StatTile(stringResource(R.string.today_summary_sets), setCount.toString(), null, Modifier.weight(1f))
                val cardio = workout.cardioMinutes()
                if (cardio > 0) {
                    StatTile(
                        label = stringResource(R.string.today_summary_cardio),
                        value = stringResource(R.string.amount_minutes, cardio),
                        note = null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            for (item in workout.exercises) {
                SavedExercise(
                    item = item,
                    onStep = { position, field, steps -> actions.step(item.exercise.id, position, field, steps) },
                    onRemoveSet = { position ->
                        // Without its only set the workout would be empty: that is deleting it, so ask first.
                        if (setCount == 1) confirmingDelete = true else actions.removeSet(item.exercise.id, position)
                    },
                    onAddSet = { actions.addSet(item.exercise.id) },
                )
            }
            OutlinedButton(
                onClick = { confirmingDelete = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.history_delete_workout))
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            text = { Text(stringResource(R.string.history_delete_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    actions.deleteWorkout()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** An exercise of a saved workout: sets are numbered 1, 2, 3 as shown but edited by their stored position. */
@Composable
private fun SavedExercise(
    item: WorkoutExercise,
    onStep: (position: Int, field: SetField, steps: Int) -> Unit,
    onRemoveSet: (position: Int) -> Unit,
    onAddSet: () -> Unit,
) {
    val exercise = item.exercise
    val shape = RoundedCornerShape(14.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExerciseImage(exercise, Modifier.size(40.dp))
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            KindTag(exercise.kind)
        }
        item.sets.forEachIndexed { index, set ->
            SetRow(
                number = index + 1,
                kind = exercise.kind,
                values = set.values,
                onStep = { field, steps -> onStep(set.position, field, steps) },
                onRemove = { onRemoveSet(set.position) },
            )
        }
        // Cardio keeps its single row of minutes.
        if (exercise.kind != ExerciseKind.CARDIO) {
            TextButton(onClick = onAddSet) { Text(stringResource(R.string.add_set)) }
        }
    }
}
