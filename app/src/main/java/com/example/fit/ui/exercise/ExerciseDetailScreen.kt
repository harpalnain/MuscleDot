package com.example.fit.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.domain.Dated
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.ExerciseRecords
import com.example.fit.domain.formatDayShort
import com.example.fit.domain.formatKg
import com.example.fit.domain.formatNumber
import com.example.fit.ui.components.AverageNControl
import com.example.fit.ui.components.ExerciseImage
import com.example.fit.ui.components.KindTag
import com.example.fit.ui.components.ScreenHeader
import com.example.fit.ui.components.SessionChart
import com.example.fit.ui.components.StatTile
import com.example.fit.ui.components.exerciseSubtitle
import com.example.fit.ui.overloadViewModel

private const val NO_RECORD = "—"

/** One exercise: its picture, its personal records and its chart (spec 6.4). */
@Composable
fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit) {
    val viewModel = overloadViewModel(key = "exercise-$exerciseId") {
        ExerciseDetailViewModel(exerciseId, it.exercises, it.workouts, it.routines, it.profile)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().testTag("screen-exercise-detail")) {
        state?.let { ExerciseDetailContent(it, viewModel::selectRoutine, viewModel::changeN, onBack) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    onSelectRoutine: (Long?) -> Unit,
    onChangeN: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise = state.exercise
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = exercise.name, onBack = onBack)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
        ) {
            // The whole photo, not a crop: this is where the movement is looked at.
            ExerciseImage(exercise, Modifier.fillMaxWidth().height(210.dp), contentScale = ContentScale.Fit)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = exerciseSubtitle(exercise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KindTag(exercise.kind)
            }
            RecordTiles(exercise.kind, state.records)

            // A filter only makes sense when the exercise is done in more than one routine.
            if (state.filters.size >= 2) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.selectedRoutineId == null,
                        onClick = { onSelectRoutine(null) },
                        label = { Text(stringResource(R.string.detail_all_routines)) },
                    )
                    for (filter in state.filters) {
                        FilterChip(
                            selected = state.selectedRoutineId == filter.routineId,
                            onClick = { onSelectRoutine(filter.routineId) },
                            label = { Text(filter.name) },
                        )
                    }
                }
            }
            SessionChart(state.series, state.n)
            AverageNControl(state.n, onChangeN)
        }
    }
}

@Composable
private fun RecordTiles(kind: ExerciseKind, records: ExerciseRecords) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val tile = Modifier.weight(1f)
        when (kind) {
            ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> {
                DatedTile(stringResource(R.string.record_tile_max_weight), records.maxWeightHg, tile) { formatKg(it.toLong()) }
                val best = records.bestSet
                StatTile(
                    label = stringResource(R.string.record_tile_best_set),
                    value = best?.let { formatKg(it.loadHg) } ?: NO_RECORD,
                    note = best?.let { "${formatNumber(it.weightHg / 10.0)} × ${it.reps}" },
                    modifier = tile,
                )
                DatedTile(stringResource(R.string.record_tile_best_session), records.bestSessionHg, tile) { formatKg(it) }
            }

            ExerciseKind.REPS_ONLY -> {
                DatedTile(stringResource(R.string.record_tile_most_reps_set), records.mostRepsSet, tile) {
                    stringResource(R.string.amount_reps, it)
                }
                DatedTile(stringResource(R.string.record_tile_most_reps_session), records.mostRepsSession, tile) {
                    stringResource(R.string.amount_reps, it)
                }
            }

            ExerciseKind.CARDIO -> {
                DatedTile(stringResource(R.string.record_tile_longest), records.longestMinutes, tile) {
                    stringResource(R.string.amount_minutes, it)
                }
            }
        }
    }
}

/** A record with the date it was first reached, or a dash while there is none. */
@Composable
private fun <T> DatedTile(label: String, record: Dated<T>?, modifier: Modifier, text: @Composable (T) -> String) {
    StatTile(
        label = label,
        value = record?.let { text(it.value) } ?: NO_RECORD,
        note = record?.let { formatDayShort(it.date) },
        modifier = modifier,
    )
}
