package com.example.fit.ui.picker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.ui.components.ExerciseImage
import com.example.fit.ui.components.KindTag
import com.example.fit.ui.components.ScreenHeader
import com.example.fit.ui.components.exerciseSubtitle
import com.example.fit.ui.components.groupLabel
import com.example.fit.ui.nav.ExercisePickerRoute
import com.example.fit.ui.overloadViewModel

/** Search and pick one exercise. [onPicked] gets its id; a newly created exercise is picked at once. */
@Composable
fun ExercisePickerScreen(route: ExercisePickerRoute, onPicked: (String) -> Unit, onBack: () -> Unit) {
    val viewModel = overloadViewModel(key = "picker-${route.routineId}-${route.workoutId}") {
        ExercisePickerViewModel(route, it.exercises, it.routines, it.workouts)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onPicked, onBack) {
        object : PickerActions {
            override fun setQuery(query: String) = viewModel.setQuery(query)
            override fun setGroup(group: MuscleGroup?) = viewModel.setGroup(group)
            override fun pick(exerciseId: String) = onPicked(exerciseId)
            override fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind) =
                viewModel.createCustom(name, group, kind, onCreated = onPicked)

            override fun renameCustom(id: String, name: String) = viewModel.renameCustom(id, name)
            override fun deleteCustom(id: String) = viewModel.deleteCustom(id)
            override fun back() = onBack()
        }
    }
    ExercisePickerContent(state, actions, Modifier.testTag("screen-exercise-picker"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExercisePickerContent(state: PickerUiState, actions: PickerActions, modifier: Modifier = Modifier) {
    // The field keeps what is being typed; the list follows it.
    var queryText by remember { mutableStateOf(state.query) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Exercise?>(null) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.add_exercise), onBack = actions::back)
        OutlinedTextField(
            value = queryText,
            onValueChange = {
                queryText = it
                actions.setQuery(it)
            },
            placeholder = { Text(stringResource(R.string.picker_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("exercise-search-field"),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            FilterChip(
                selected = state.group == null,
                onClick = { actions.setGroup(null) },
                label = { Text(stringResource(R.string.picker_all)) },
            )
            for (group in MuscleGroup.entries) {
                FilterChip(
                    selected = state.group == group,
                    onClick = { actions.setGroup(group) },
                    label = { Text(groupLabel(group)) },
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.results, key = { it.id }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    added = exercise.id in state.alreadyAdded,
                    onPick = { actions.pick(exercise.id) },
                    onRename = { renaming = exercise },
                    onDelete = { deleting = exercise },
                )
            }
            if (state.offerCreate) {
                item(key = "create") {
                    OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.picker_create, state.query.trim()), maxLines = 2)
                    }
                }
            } else if (state.results.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(R.string.picker_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }
    }

    if (creating) {
        NewExerciseDialog(
            initialName = state.query.trim(),
            initialGroup = state.group ?: MuscleGroup.CHEST,
            onCreate = { name, group, kind ->
                creating = false
                actions.createCustom(name, group, kind)
            },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { exercise ->
        var name by remember(exercise.id) { mutableStateOf(exercise.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.exercise_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        renaming = null
                        actions.renameCustom(exercise.id, name.trim())
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleting?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.picker_delete_question, exercise.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    actions.deleteCustom(exercise.id)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseRow(exercise: Exercise, added: Boolean, onPick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .combinedClickable(
                    enabled = !added || exercise.isCustom,
                    onClick = { if (!added) onPick() },
                    // Only the user's own exercises can be renamed or deleted.
                    onLongClick = if (exercise.isCustom) ({ menuOpen = true }) else null,
                )
                .heightIn(min = 60.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            ExerciseImage(exercise, Modifier.size(46.dp))
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
                    text = exerciseSubtitle(exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (added) {
                Text(
                    text = stringResource(R.string.picker_added),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** Name, muscle group and how it is counted, for an exercise the built-in list does not have. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewExerciseDialog(
    initialName: String,
    initialGroup: MuscleGroup,
    onCreate: (String, MuscleGroup, ExerciseKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var group by remember { mutableStateOf(initialGroup) }
    var kind by remember { mutableStateOf(if (initialGroup == MuscleGroup.CARDIO) ExerciseKind.CARDIO else ExerciseKind.WEIGHTS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.exercise_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Text(stringResource(R.string.muscle_group), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (option in MuscleGroup.entries) {
                        FilterChip(
                            selected = group == option,
                            onClick = {
                                group = option
                                // The Cardio group and the cardio kind go together.
                                if (option == MuscleGroup.CARDIO) {
                                    kind = ExerciseKind.CARDIO
                                } else if (kind == ExerciseKind.CARDIO) {
                                    kind = ExerciseKind.WEIGHTS
                                }
                            },
                            label = { Text(groupLabel(option)) },
                        )
                    }
                }
                Text(stringResource(R.string.kind_question), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (option in ExerciseKind.entries) {
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                if (option == ExerciseKind.CARDIO) {
                                    group = MuscleGroup.CARDIO
                                } else if (group == MuscleGroup.CARDIO) {
                                    group = MuscleGroup.CHEST
                                }
                            },
                            label = { Text(stringResource(kindChoiceLabel(option))) },
                        )
                    }
                }
                Text(
                    text = stringResource(kindExplanation(kind)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), group, kind) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun kindChoiceLabel(kind: ExerciseKind) = when (kind) {
    ExerciseKind.WEIGHTS -> R.string.kind_choice_weights
    ExerciseKind.BODY_WEIGHT -> R.string.kind_choice_body_weight
    ExerciseKind.REPS_ONLY -> R.string.kind_choice_reps_only
    ExerciseKind.CARDIO -> R.string.kind_choice_cardio
}

private fun kindExplanation(kind: ExerciseKind) = when (kind) {
    ExerciseKind.WEIGHTS -> R.string.kind_explain_weights
    ExerciseKind.BODY_WEIGHT -> R.string.kind_explain_body_weight
    ExerciseKind.REPS_ONLY -> R.string.kind_explain_reps_only
    ExerciseKind.CARDIO -> R.string.kind_explain_cardio
}
