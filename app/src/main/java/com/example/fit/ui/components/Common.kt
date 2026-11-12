package com.example.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fit.R
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup

/** A small tag saying how an exercise is counted. Weights exercises, the common case, get none. */
@Composable
fun KindTag(kind: ExerciseKind, modifier: Modifier = Modifier) {
    val label = when (kind) {
        ExerciseKind.WEIGHTS -> return
        ExerciseKind.BODY_WEIGHT -> R.string.kind_body_weight
        ExerciseKind.REPS_ONLY -> R.string.kind_reps_only
        ExerciseKind.CARDIO -> R.string.kind_cardio
    }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * A labelled figure, such as a personal record: label, value, and an optional note under it.
 * A screen reader reads the three as one item.
 */
@Composable
fun StatTile(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small heading above a group of rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

/** The name of a muscle group, as shown on filter chips and under exercise names. */
@Composable
fun groupLabel(group: MuscleGroup): String = stringResource(
    when (group) {
        MuscleGroup.CHEST -> R.string.group_chest
        MuscleGroup.BACK -> R.string.group_back
        MuscleGroup.SHOULDERS -> R.string.group_shoulders
        MuscleGroup.ARMS -> R.string.group_arms
        MuscleGroup.LEGS -> R.string.group_legs
        MuscleGroup.CORE -> R.string.group_core
        MuscleGroup.CARDIO -> R.string.group_cardio
    },
)

/** "Chest · barbell" for a built-in exercise; the muscle group alone for a custom one. */
@Composable
fun exerciseSubtitle(exercise: Exercise): String {
    val muscle = exercise.muscle ?: groupLabel(exercise.group)
    val equipment = when (exercise.equipment) {
        null -> return muscle
        "body only" -> stringResource(R.string.equipment_none)
        else -> exercise.equipment
    }
    return "$muscle · $equipment"
}

/** The bar at the top of a screen opened from a tab: back arrow, title, and an optional action. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        action?.invoke()
    }
}
