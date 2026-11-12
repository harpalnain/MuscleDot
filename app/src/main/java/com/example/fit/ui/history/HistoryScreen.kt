package com.example.fit.ui.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.domain.formatMonth
import com.example.fit.domain.formatWeekdayShort
import com.example.fit.ui.components.SectionLabel
import com.example.fit.ui.overloadViewModel
import com.example.fit.ui.theme.LocalOverloadColors

/** The History tab: every saved workout, newest first (spec 5.11). */
@Composable
fun HistoryScreen(onOpen: (Long) -> Unit) {
    val viewModel = overloadViewModel { HistoryViewModel(it.workouts) }
    val months by viewModel.months.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().testTag("screen-history")) {
        // Nothing is drawn until the saved workouts are read, so "No workouts yet" never flashes.
        months?.let { HistoryContent(it, onOpen) }
    }
}

@Composable
fun HistoryContent(months: List<HistoryMonth>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 12.dp)) {
        Text(
            text = stringResource(R.string.tab_history),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (months.isEmpty()) {
            Text(
                text = stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            for (month in months) {
                item(key = "month-${month.month}") {
                    SectionLabel(formatMonth(month.month), Modifier.padding(top = 8.dp))
                }
                items(month.rows, key = { it.workoutId }) { row ->
                    HistoryRowItem(row, onClick = { onOpen(row.workoutId) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRowItem(row: HistoryRow, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(34.dp)) {
            Text(
                text = row.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatWeekdayShort(row.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.routineName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.records > 0) RecordBadge(row.records)
            }
            val exercises = pluralStringResource(R.plurals.exercise_count, row.exerciseCount, row.exerciseCount)
            Text(
                // Cardio has no sets, so a cardio-only workout shows its exercises alone.
                text = if (row.setCount == 0) {
                    exercises
                } else {
                    stringResource(
                        R.string.history_counts,
                        exercises,
                        pluralStringResource(R.plurals.set_count, row.setCount, row.setCount),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = row.metric,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecordBadge(records: Int) {
    val colors = LocalOverloadColors.current
    Text(
        text = pluralStringResource(R.plurals.record_count, records, records),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = colors.onRecordContainer,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.recordContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
