package com.example.fit.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.RoutineExercise
import com.example.fit.domain.SetField
import com.example.fit.domain.setsSummary
import com.example.fit.ui.components.ExerciseImage
import com.example.fit.ui.components.KindTag
import com.example.fit.ui.components.ScreenHeader
import com.example.fit.ui.components.SectionLabel
import com.example.fit.ui.components.SetRow
import com.example.fit.ui.overloadViewModel

private val RowHeight = 64.dp
private val RowGap = 6.dp

/**
 * [pickedExerciseId] is the exercise the picker returned, if any; it is added to
 * the routine and [onPickedHandled] is called so it is added only once.
 */
@Composable
fun RoutineEditorScreen(
    routineId: Long,
    pickedExerciseId: String?,
    onPickedHandled: () -> Unit,
    onPickExercise: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = overloadViewModel(key = "routine-$routineId") { RoutineEditorViewModel(routineId, it.routines) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            viewModel.addExercise(pickedExerciseId)
            onPickedHandled()
        }
    }

    val actions = remember(viewModel, onPickExercise, onDone) {
        object : RoutineEditorActions {
            override fun rename(name: String) = viewModel.rename(name)
            override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) =
                viewModel.step(exerciseId, position, field, steps)

            override fun addSet(exerciseId: String) = viewModel.addSet(exerciseId)
            override fun removeSet(exerciseId: String, position: Int) = viewModel.removeSet(exerciseId, position)
            override fun removeExercise(exerciseId: String) = viewModel.removeExercise(exerciseId)
            override fun move(fromIndex: Int, toIndex: Int) = viewModel.move(fromIndex, toIndex)
            override fun addExercise() = onPickExercise()
            override fun done() = onDone()
        }
    }
    Box(Modifier.fillMaxSize().testTag("screen-routine-editor")) {
        state?.let { RoutineEditorContent(it, actions) }
    }
}

@Composable
fun RoutineEditorContent(state: RoutineEditorUiState, actions: RoutineEditorActions, modifier: Modifier = Modifier) {
    // The field keeps what is being typed; the stored name follows it.
    var name by remember(state.routineId) { mutableStateOf(state.name) }
    var openExerciseId by remember { mutableStateOf<String?>(null) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowStepPx = with(LocalDensity.current) { (RowHeight + RowGap).toPx() }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.editor_title),
            onBack = actions::done,
            action = { TextButton(onClick = actions::done) { Text(stringResource(R.string.done)) } },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(RowGap),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    actions.rename(it)
                },
                label = { Text(stringResource(R.string.routine_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().testTag("routine-name-field"),
            )
            SectionLabel(stringResource(R.string.editor_exercises), Modifier.padding(top = 6.dp))

            state.exercises.forEachIndexed { index, item ->
                val dragging = draggedIndex == index
                ExerciseItem(
                    item = item,
                    open = openExerciseId == item.exercise.id && draggedIndex == null,
                    actions = actions,
                    onToggle = { openExerciseId = if (openExerciseId == item.exercise.id) null else item.exercise.id },
                    onDragStart = {
                        // Rows must all be the same height while one is dragged.
                        openExerciseId = null
                        draggedIndex = index
                        dragOffset = 0f
                    },
                    onDrag = { dragOffset += it },
                    onDragEnd = {
                        actions.move(index, dropIndex(index, dragOffset, rowStepPx, state.exercises.size))
                        draggedIndex = null
                    },
                    onDragCancel = { draggedIndex = null },
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                )
            }

            OutlinedButton(onClick = actions::addExercise, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(R.string.add_exercise))
            }
        }
    }
}

@Composable
private fun ExerciseItem(
    item: RoutineExercise,
    open: Boolean,
    actions: RoutineEditorActions,
    onToggle: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = item.exercise
    val shape = RoundedCornerShape(12.dp)
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(RowHeight).clickable(onClick = onToggle).padding(start = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start() },
                            onDrag = { change, amount ->
                                change.consume()
                                drag(amount.y)
                            },
                            onDragEnd = { end() },
                            onDragCancel = { cancel() },
                        )
                    },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(R.string.editor_drag_handle, exercise.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExerciseImage(exercise, Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Text(
                    text = setsSummary(exercise.kind, item.sets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { actions.removeExercise(exercise.id) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_named, exercise.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (open) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, bottom = 10.dp),
            ) {
                item.sets.forEachIndexed { position, values ->
                    SetRow(
                        number = position + 1,
                        kind = exercise.kind,
                        values = values,
                        onStep = { field, steps -> actions.step(exercise.id, position, field, steps) },
                        // An exercise always keeps at least one set.
                        onRemove = if (item.sets.size > 1) ({ actions.removeSet(exercise.id, position) }) else null,
                    )
                }
                if (exercise.kind != ExerciseKind.CARDIO) {
                    TextButton(onClick = { actions.addSet(exercise.id) }) { Text(stringResource(R.string.add_set)) }
                }
            }
        }
    }
}
