package com.example.fit.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.RecordType
import com.example.fit.domain.Routine
import com.example.fit.domain.SetField
import com.example.fit.domain.TodayExerciseUi
import com.example.fit.domain.TodayState
import com.example.fit.domain.WeekCount
import com.example.fit.domain.formatDayLong
import com.example.fit.domain.formatKg
import com.example.fit.domain.parseKgToHg
import com.example.fit.ui.components.ExerciseImage
import com.example.fit.ui.components.KindTag
import com.example.fit.ui.components.SetRow
import com.example.fit.ui.components.StatTile
import com.example.fit.ui.overloadViewModel
import com.example.fit.ui.theme.LocalOverloadColors

private const val ADD = "add"
private const val SWAP = "swap"
private const val REMOVE = "remove"

/**
 * The main screen. [pickedExerciseId] is what the exercise picker returned, if
 * anything; [onPickedHandled] clears it. [onPickExercise] opens the picker for the
 * stored workout (null while only a preview is shown).
 */
@Composable
fun TodayScreen(
    pickedExerciseId: String?,
    onPickedHandled: () -> Unit,
    onPickExercise: (workoutId: Long?) -> Unit,
    onShowExercise: (String) -> Unit,
) {
    val viewModel = overloadViewModel { TodayViewModel(it.workouts, it.routines, it.profile, it.today) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bodyWeightPrompt by viewModel.bodyWeightPrompt.collectAsStateWithLifecycle()
    val workoutId by viewModel.currentWorkoutId.collectAsStateWithLifecycle()
    val routineOptions by viewModel.routineOptions.collectAsStateWithLifecycle()

    var choosingRoutine by rememberSaveable { mutableStateOf(false) }
    var confirmingFinish by rememberSaveable { mutableStateOf(false) }

    // A change to today's exercises waits here for "only today" or "also the routine".
    // These survive the trip to the exercise picker and back.
    var swappingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOldExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            pendingKind = if (swappingExerciseId != null) SWAP else ADD
            pendingExerciseId = pickedExerciseId
            pendingOldExerciseId = swappingExerciseId
            swappingExerciseId = null
            onPickedHandled()
        }
    }

    val actions = remember(viewModel, onPickExercise, onShowExercise) {
        object : TodayActions {
            override fun open(exerciseId: String) = viewModel.open(exerciseId)
            override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) =
                viewModel.step(exerciseId, position, field, steps)

            override fun toggleDone(exerciseId: String, position: Int) = viewModel.toggleDone(exerciseId, position)
            override fun addSet(exerciseId: String) = viewModel.addSet(exerciseId)
            override fun removeLastSet(exerciseId: String) = viewModel.removeLastSet(exerciseId)
            override fun chooseRoutine() {
                choosingRoutine = true
            }

            override fun addExercise() {
                swappingExerciseId = null
                onPickExercise(viewModel.currentWorkoutId.value)
            }

            override fun swapExercise(exerciseId: String) {
                swappingExerciseId = exerciseId
                onPickExercise(viewModel.currentWorkoutId.value)
            }

            override fun removeExercise(exerciseId: String) {
                pendingKind = REMOVE
                pendingExerciseId = exerciseId
            }

            override fun finish() {
                val unticked = (viewModel.state.value as? TodayState.Active)?.untickedCount ?: 0
                if (unticked > 0) confirmingFinish = true else viewModel.finish()
            }

            override fun continueWorkout() = viewModel.continueWorkout()
            override fun showExercise(exerciseId: String) = onShowExercise(exerciseId)
        }
    }

    Box(Modifier.fillMaxSize().testTag("screen-today")) {
        state?.let { TodayContent(it, actions) }
    }

    if (choosingRoutine) {
        RoutineChooser(
            routines = routineOptions,
            onPick = {
                choosingRoutine = false
                viewModel.startRoutine(it)
            },
            onDismiss = { choosingRoutine = false },
        )
    }

    if (confirmingFinish) {
        FinishWarningDialog(
            untickedCount = (state as? TodayState.Active)?.untickedCount ?: 0,
            onFinish = {
                confirmingFinish = false
                viewModel.finish()
            },
            onGoBack = { confirmingFinish = false },
        )
    }

    val kind = pendingKind
    val exerciseId = pendingExerciseId
    if (kind != null && exerciseId != null) {
        val clear = {
            pendingKind = null
            pendingExerciseId = null
            pendingOldExerciseId = null
        }
        val apply = { alsoUpdateRoutine: Boolean ->
            val old = pendingOldExerciseId
            when {
                kind == ADD -> viewModel.addExercise(exerciseId, alsoUpdateRoutine)
                kind == SWAP && old != null -> viewModel.replaceExercise(old, exerciseId, alsoUpdateRoutine)
                kind == REMOVE -> viewModel.removeExercise(exerciseId, alsoUpdateRoutine)
            }
            clear()
        }
        ScopeChoiceDialog(onOnlyToday = { apply(false) }, onAlsoRoutine = { apply(true) }, onDismiss = clear)
    }

    if (bodyWeightPrompt != null) {
        BodyWeightDialog(onConfirm = viewModel::confirmBodyWeight, onDismiss = viewModel::dismissBodyWeightPrompt)
    }
}

@Composable
fun TodayContent(state: TodayState, actions: TodayActions, modifier: Modifier = Modifier) {
    when (state) {
        is TodayState.RestDay -> RestDay(state, actions, modifier)
        is TodayState.Active -> ActiveWorkout(state, actions, modifier)
        is TodayState.Finished -> FinishedWorkout(state, actions, modifier)
    }
}

@Composable
private fun ActiveWorkout(state: TodayState.Active, actions: TodayActions, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Header(state.date, state.routineName, state.week) {
            OutlinedButton(onClick = actions::chooseRoutine, enabled = state.canChangeRoutine) {
                Text(stringResource(R.string.today_change_routine), maxLines = 1)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            for (item in state.exercises) {
                if (item.exercise.id == state.openExerciseId) OpenExercise(item, actions) else ClosedExercise(item, actions)
            }
            OutlinedButton(onClick = actions::addExercise, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_exercise))
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_total_load),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (state.cardioMinutes > 0) {
                        stringResource(R.string.today_total_with_cardio, formatKg(state.totalLoadHg), state.cardioMinutes)
                    } else {
                        formatKg(state.totalLoadHg)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = actions::finish, enabled = state.canFinish) {
                Text(stringResource(R.string.today_finish))
            }
        }
    }
}

@Composable
private fun Header(date: java.time.LocalDate, title: String, week: WeekCount, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = formatDayLong(date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        WeekLine(week)
    }
}

/** "This week: 3 of 5 done", with one dot per planned day. */
@Composable
private fun WeekLine(week: WeekCount, modifier: Modifier = Modifier) {
    val doneColor = LocalOverloadColors.current.done
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier.padding(top = 2.dp)) {
        Text(
            text = if (week.planned > 0) {
                stringResource(R.string.today_week, week.done, week.planned)
            } else {
                stringResource(R.string.today_week_no_plan, week.done)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        repeat(week.planned.coerceAtMost(7)) { index ->
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (index < week.done) doneColor else MaterialTheme.colorScheme.outline),
            )
        }
    }
}

/** What the exercise has done so far, in its own measure: "740 kg", "45 reps" or "25 min". */
@Composable
private fun progressText(item: TodayExerciseUi): String = when (item.exercise.kind) {
    ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> formatKg(item.loadHg)
    ExerciseKind.REPS_ONLY -> stringResource(R.string.amount_reps, item.totalReps)
    ExerciseKind.CARDIO -> stringResource(R.string.amount_minutes, item.minutes)
}

@Composable
private fun ClosedExercise(item: TodayExerciseUi, actions: TodayActions) {
    val shape = RoundedCornerShape(12.dp)
    val allDone = item.doneCount == item.sets.size && item.sets.isNotEmpty()
    val doneColor = LocalOverloadColors.current.done
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { actions.open(item.exercise.id) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (allDone) doneColor else Color.Transparent)
                .border(2.dp, if (allDone) doneColor else MaterialTheme.colorScheme.outline, CircleShape),
        ) {
            if (allDone) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            KindTag(item.exercise.kind)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.today_sets_done, item.doneCount, item.sets.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.doneCount > 0) {
                Text(
                    text = progressText(item),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun OpenExercise(item: TodayExerciseUi, actions: TodayActions) {
    val exercise = item.exercise
    val shape = RoundedCornerShape(14.dp)
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExerciseImage(exercise, Modifier.size(60.dp).clickable { actions.showExercise(exercise.id) })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    KindTag(exercise.kind)
                }
                item.bestWeightHg?.let { best ->
                    Text(
                        text = stringResource(R.string.today_best, formatKg(best.toLong())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options_for, exercise.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_remove_last_set)) },
                        enabled = item.sets.size > 1,
                        onClick = {
                            menuOpen = false
                            actions.removeLastSet(exercise.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_swap_exercise)) },
                        onClick = {
                            menuOpen = false
                            actions.swapExercise(exercise.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_remove_exercise)) },
                        onClick = {
                            menuOpen = false
                            actions.removeExercise(exercise.id)
                        },
                    )
                }
            }
        }

        item.sets.forEachIndexed { index, set ->
            SetRow(
                number = index + 1,
                kind = exercise.kind,
                values = set.values,
                onStep = { field, steps -> actions.step(exercise.id, set.position, field, steps) },
                done = set.done,
                onToggleDone = { actions.toggleDone(exercise.id, set.position) },
                isRecord = set.isRecord,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
            if (exercise.kind != ExerciseKind.CARDIO) {
                TextButton(onClick = { actions.addSet(exercise.id) }) { Text(stringResource(R.string.add_set)) }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = when (exercise.kind) {
                    ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> stringResource(R.string.today_load, formatKg(item.loadHg))
                    else -> progressText(item)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestDay(state: TodayState.RestDay, actions: TodayActions, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Header(state.date, stringResource(R.string.rest_day), state.week)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.today_rest_explanation),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = actions::chooseRoutine) { Text(stringResource(R.string.today_start_routine)) }
        }
    }
}

@Composable
private fun FinishedWorkout(state: TodayState.Finished, actions: TodayActions, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(state.date, state.routineName, state.week)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.today_finished),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.today_summary_load), formatKg(state.summary.loadHg), null, Modifier.weight(1f))
                StatTile(stringResource(R.string.today_summary_sets), state.summary.setsDone.toString(), null, Modifier.weight(1f))
                if (state.summary.cardioMinutes > 0) {
                    StatTile(
                        stringResource(R.string.today_summary_cardio),
                        stringResource(R.string.amount_minutes, state.summary.cardioMinutes),
                        null,
                        Modifier.weight(1f),
                    )
                }
            }
            val colors = LocalOverloadColors.current
            for ((exerciseName, type) in state.recordLines) {
                Text(
                    text = stringResource(R.string.record_line, exerciseName, stringResource(recordTypeLabel(type))),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onRecordContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.recordContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.today_finished_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = actions::continueWorkout) { Text(stringResource(R.string.today_continue)) }
        }
    }
}

fun recordTypeLabel(type: RecordType): Int = when (type) {
    RecordType.MAX_WEIGHT -> R.string.record_max_weight
    RecordType.BEST_SET -> R.string.record_best_set
    RecordType.BEST_SESSION -> R.string.record_best_session
    RecordType.MOST_REPS_SET -> R.string.record_most_reps_set
    RecordType.MOST_REPS_SESSION -> R.string.record_most_reps_session
}

/** Asked when Finish is tapped while some sets are not ticked (spec 5.6). */
@Composable
fun FinishWarningDialog(untickedCount: Int, onFinish: () -> Unit, onGoBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onGoBack,
        text = { Text(pluralStringResource(R.plurals.today_finish_warning, untickedCount, untickedCount)) },
        confirmButton = { TextButton(onClick = onFinish) { Text(stringResource(R.string.today_finish_confirm)) } },
        dismissButton = { TextButton(onClick = onGoBack) { Text(stringResource(R.string.today_go_back)) } },
    )
}

/** Asked every time an exercise is added, swapped or removed during a workout (spec 5.5). */
@Composable
fun ScopeChoiceDialog(onOnlyToday: () -> Unit, onAlsoRoutine: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.today_scope_question)) },
        confirmButton = { TextButton(onClick = onAlsoRoutine) { Text(stringResource(R.string.today_scope_routine)) } },
        dismissButton = { TextButton(onClick = onOnlyToday) { Text(stringResource(R.string.today_scope_today)) } },
    )
}

@Composable
private fun RoutineChooser(routines: List<Routine>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_choose_routine)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (routines.isEmpty()) {
                    Text(stringResource(R.string.today_no_routines), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for (routine in routines) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(routine.id) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Body weight exercises count body weight, so it is asked for before the first such tick (spec 5.4). */
@Composable
private fun BodyWeightDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val weightHg = parseKgToHg(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_body_weight_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.today_body_weight_explanation))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.profile_body_weight)) },
                    suffix = { Text(stringResource(R.string.unit_kg)) },
                    isError = text.isNotBlank() && weightHg == null,
                    supportingText = if (text.isNotBlank() && weightHg == null) {
                        { Text(stringResource(R.string.profile_body_weight_hint)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("body-weight-prompt-field"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { weightHg?.let(onConfirm) }, enabled = weightHg != null) {
                Text(stringResource(R.string.today_body_weight_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
