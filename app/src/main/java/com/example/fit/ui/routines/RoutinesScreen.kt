package com.example.fit.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.ui.components.SectionLabel
import com.example.fit.ui.overloadViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RoutinesScreen(onEdit: (Long) -> Unit) {
    val viewModel = overloadViewModel { RoutinesViewModel(it.routines, it.today) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onEdit) {
        object : RoutinesActions {
            override fun setDay(day: DayOfWeek, routineId: Long?) = viewModel.setDay(day, routineId)
            override fun create(name: String) = viewModel.create(name, onCreated = onEdit)
            override fun edit(id: Long) = onEdit(id)
            override fun duplicate(id: Long) = viewModel.duplicate(id)
            override fun delete(id: Long) = viewModel.delete(id)
        }
    }
    RoutinesContent(state, actions, Modifier.testTag("screen-routines"))
}

@Composable
fun RoutinesContent(state: RoutinesUiState, actions: RoutinesActions, modifier: Modifier = Modifier) {
    var choosingDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var deleting by remember { mutableStateOf<RoutineRow?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_routines),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        SectionLabel(stringResource(R.string.routines_my_week))
        for (row in state.week) {
            WeekDay(row, onClick = { choosingDay = row.day })
        }

        SectionLabel(stringResource(R.string.routines_my_routines), Modifier.padding(top = 8.dp))
        for (routine in state.routines) {
            RoutineItem(
                routine = routine,
                onEdit = { actions.edit(routine.id) },
                onDuplicate = { actions.duplicate(routine.id) },
                onDelete = { deleting = routine },
            )
        }
        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.routines_new))
        }
    }

    choosingDay?.let { day ->
        DayChooser(
            day = day,
            routines = state.routines,
            selectedId = state.week.firstOrNull { it.day == day }?.routineId,
            onPick = { routineId ->
                choosingDay = null
                actions.setDay(day, routineId)
            },
            onDismiss = { choosingDay = null },
        )
    }

    deleting?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.routines_delete_question, routine.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    actions.delete(routine.id)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (creating) {
        NewRoutineDialog(
            onCreate = { name ->
                creating = false
                actions.create(name)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun WeekDay(row: WeekDayRow, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (row.isToday) colors.primaryContainer else colors.surface)
            .border(1.dp, if (row.isToday) colors.primary else colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = row.day.getDisplayName(TextStyle.SHORT, Locale.US),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (row.isToday) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = row.routineName ?: stringResource(R.string.rest_day),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (row.routineName != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (row.routineName != null) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun RoutineItem(routine: RoutineRow, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onEdit)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.exercise_count, routine.exerciseCount, routine.exerciseCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options_for, routine.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.duplicate)) },
                    onClick = {
                        menuOpen = false
                        onDuplicate()
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
}

/** Picks the routine for one weekday, or a rest day. */
@Composable
private fun DayChooser(
    day: DayOfWeek,
    routines: List<RoutineRow>,
    selectedId: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.getDisplayName(TextStyle.FULL, Locale.US)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                for (routine in routines) {
                    ChoiceRow(routine.name, selected = routine.id == selectedId, onClick = { onPick(routine.id) })
                }
                ChoiceRow(stringResource(R.string.rest_day), selected = selectedId == null, onClick = { onPick(null) })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun NewRoutineDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.routine_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.testTag("routine-name-field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
