package com.example.fit.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.domain.BodyWeightEntry
import com.example.fit.domain.ChartPoint
import com.example.fit.domain.Measure
import com.example.fit.domain.formatDayShort
import com.example.fit.domain.formatKg
import com.example.fit.ui.components.AverageNControl
import com.example.fit.ui.components.ColumnChart
import com.example.fit.ui.components.ExerciseImage
import com.example.fit.ui.components.KindTag
import com.example.fit.ui.components.SectionLabel
import com.example.fit.ui.components.SessionChart
import com.example.fit.ui.components.StatTile
import com.example.fit.ui.components.TrendChart
import com.example.fit.ui.components.formatMeasure
import com.example.fit.ui.exercise.RoutineFilter
import com.example.fit.ui.overloadViewModel
import com.example.fit.ui.theme.LocalOverloadColors

private const val NO_VALUE = "—"

/** The Progress tab: charts and records per exercise, per routine, per week, and body weight (spec 6.3). */
@Composable
fun ProgressScreen(onShowExercise: (String) -> Unit) {
    val viewModel = overloadViewModel { ProgressViewModel(it.workouts, it.routines, it.profile, it.today) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onShowExercise) {
        object : ProgressActions {
            override fun setSection(section: ProgressSection) = viewModel.setSection(section)
            override fun setQuery(query: String) = viewModel.setQuery(query)
            override fun selectRoutine(routineId: Long) = viewModel.selectRoutine(routineId)
            override fun changeN(n: Int) = viewModel.changeN(n)
            override fun showExercise(exerciseId: String) = onShowExercise(exerciseId)
        }
    }
    Box(Modifier.fillMaxSize().testTag("screen-progress")) {
        // Nothing is drawn until the saved workouts are read, so the empty hint never flashes.
        state?.let { ProgressContent(it, actions) }
    }
}

@Composable
fun ProgressContent(state: ProgressUiState, actions: ProgressActions, modifier: Modifier = Modifier) {
    // No exercise has a session and no search is hiding any: nothing has been saved yet.
    val noHistory = state.exerciseRows.isEmpty() && state.query.isBlank()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_progress),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        SectionSwitch(state.section, actions::setSection)
        if (state.section == ProgressSection.EXERCISES && !noHistory) {
            // The only section that can be long: it scrolls as a lazy list under its search field.
            ExercisesSection(state, actions, Modifier.weight(1f))
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            ) {
                when (state.section) {
                    ProgressSection.EXERCISES -> EmptyHint()
                    ProgressSection.ROUTINES -> if (noHistory) EmptyHint() else RoutinesSection(state, actions)
                    ProgressSection.WEEKLY -> WeeklySection(state.weekly)
                    ProgressSection.BODY -> BodySection(state.bodyWeights)
                }
            }
        }
    }
}

@Composable
private fun SectionSwitch(selected: ProgressSection, onSelect: (ProgressSection) -> Unit) {
    val sections = ProgressSection.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        sections.forEachIndexed { index, section ->
            SegmentedButton(
                selected = section == selected,
                onClick = { onSelect(section) },
                shape = SegmentedButtonDefaults.itemShape(index, sections.size),
                // Four labels have to fit across a narrow phone: no tick icon, tight padding.
                contentPadding = PaddingValues(horizontal = 4.dp),
                icon = {},
                label = {
                    Text(
                        text = stringResource(
                            when (section) {
                                ProgressSection.EXERCISES -> R.string.progress_exercises
                                ProgressSection.ROUTINES -> R.string.progress_routines
                                ProgressSection.WEEKLY -> R.string.progress_weekly
                                ProgressSection.BODY -> R.string.progress_body
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        text = stringResource(R.string.progress_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

@Composable
private fun ExercisesSection(state: ProgressUiState, actions: ProgressActions, modifier: Modifier = Modifier) {
    // The field keeps what is being typed; the list follows it.
    var queryText by remember { mutableStateOf(state.query) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = queryText,
            onValueChange = {
                queryText = it
                actions.setQuery(it)
            },
            placeholder = { Text(stringResource(R.string.picker_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("progress-search-field"),
        )
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.exerciseRows, key = { it.exercise.id }) { row ->
                ProgressRow(row, onClick = { actions.showExercise(row.exercise.id) })
            }
            if (state.exerciseRows.isEmpty()) {
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
}

/** An exercise with its main record under its name; a tap opens its page. */
@Composable
private fun ProgressRow(row: ExerciseProgressRow, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        ExerciseImage(row.exercise, Modifier.size(46.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                KindTag(row.exercise.kind)
            }
            Text(
                text = row.headline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoutinesSection(state: ProgressUiState, actions: ProgressActions) {
    val routine = state.routine
    if (routine == null) {
        // There is history, but every routine has since been deleted.
        Text(
            text = stringResource(R.string.today_no_routines),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    RoutineDropdown(routine.name, state.routineOptions, actions::selectRoutine)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PointTile(stringResource(R.string.record_tile_best_session), routine.best, routine.series.measure, Modifier.weight(1f))
        PointTile(stringResource(R.string.progress_last_session), routine.last, routine.series.measure, Modifier.weight(1f))
    }
    SessionChart(routine.series, state.n)
    AverageNControl(state.n, actions::changeN)
    if (routine.exercises.isNotEmpty()) {
        SectionLabel(stringResource(R.string.progress_routine_exercises))
        for (row in routine.exercises) {
            ProgressRow(row, onClick = { actions.showExercise(row.exercise.id) })
        }
    }
}

/** One session of a routine: its total, and the day under it. */
@Composable
private fun PointTile(label: String, point: ChartPoint?, measure: Measure, modifier: Modifier) {
    StatTile(
        label = label,
        value = point?.let { formatMeasure(measure, it.value) } ?: NO_VALUE,
        note = point?.let { formatDayShort(it.date) },
        modifier = modifier,
    )
}

@Composable
private fun RoutineDropdown(selectedName: String, options: List<RoutineFilter>, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(
                    onClickLabel = stringResource(R.string.progress_choose_routine),
                    role = Role.DropdownList,
                    onClick = { open = true },
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = selectedName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        open = false
                        onSelect(option.routineId)
                    },
                )
            }
        }
    }
}

@Composable
private fun WeeklySection(weekly: WeeklyProgress) {
    val colors = LocalOverloadColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val thisWeek = weekly.thisWeek
        StatTile(
            label = stringResource(R.string.progress_this_week),
            value = thisWeek?.let { formatKg(it.loadHg) } ?: NO_VALUE,
            note = thisWeek?.let { pluralStringResource(R.plurals.workout_count, it.workouts, it.workouts) },
            modifier = Modifier.weight(1f),
        )
        val best = weekly.bestWeek
        StatTile(
            label = stringResource(R.string.progress_best_week),
            value = best?.let { formatKg(it.loadHg) } ?: NO_VALUE,
            note = best?.let { weekRangeText(it.weekStart) },
            modifier = Modifier.weight(1f),
        )
    }
    ColumnChart(
        title = stringResource(R.string.progress_weekly_load),
        columns = remember(weekly.weeks) { weekColumns(weekly.weeks, cardio = false) },
        color = colors.chartLoad,
    )
    ColumnChart(
        title = stringResource(R.string.progress_weekly_cardio),
        columns = remember(weekly.weeks) { weekColumns(weekly.weeks, cardio = true) },
        color = colors.chartCardio,
    )
}

@Composable
private fun BodySection(bodyWeights: List<BodyWeightEntry>) {
    TrendChart(
        title = stringResource(R.string.body_weight_chart_title),
        points = bodyWeights.map { ChartPoint(it.date, it.weightHg / 10.0) },
        valueText = { formatKg(Math.round(it * 10)) },
        emptyText = stringResource(R.string.body_weight_chart_empty),
    )
}
