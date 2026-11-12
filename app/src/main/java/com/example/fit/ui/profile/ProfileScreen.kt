package com.example.fit.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.data.backup.backupFileName
import com.example.fit.domain.ChartPoint
import com.example.fit.domain.formatKg
import com.example.fit.domain.parseKgToHg
import com.example.fit.ui.components.SectionLabel
import com.example.fit.ui.components.StepButton
import com.example.fit.ui.components.TrendChart
import com.example.fit.ui.overloadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_OF_BIRTH = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)
private val FEET = 3..8
private val INCHES = 0..11
private const val BACKUP_MIME = "application/json"

/** A backup is small text. Anything bigger than this is some other file, and is not read into memory. */
private const val MAX_BACKUP_BYTES = 50L * 1024 * 1024

@Composable
fun ProfileScreen() {
    val viewModel = overloadViewModel { ProfileViewModel(it.profile, it.backup, it.today, it.clock) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val messages = mapOf(
        BackupMessage.SAVED to stringResource(R.string.backup_saved),
        BackupMessage.RESTORED to stringResource(R.string.backup_restored),
        BackupMessage.NOT_A_BACKUP to stringResource(R.string.backup_not_a_backup),
        BackupMessage.NEWER_VERSION to stringResource(R.string.backup_newer_version),
    )

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        if (uri != null) {
            scope.launch {
                val text = viewModel.exportText()
                withContext(Dispatchers.IO) { context.writeText(uri, text) }
                snackbar.showSnackbar(messages.getValue(BackupMessage.SAVED))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { context.readText(uri) }
                // A file that cannot be read as text is certainly not a backup; let the import say so.
                pendingImport = text.orEmpty()
            }
        }
    }

    val actions = remember(viewModel) {
        object : ProfileActions {
            override fun setName(name: String) = viewModel.setName(name).let { }
            override fun setDateOfBirth(date: LocalDate?) = viewModel.setDateOfBirth(date).let { }
            override fun setHeight(feet: Int?, inches: Int?) = viewModel.setHeight(feet, inches).let { }
            override fun setBodyWeightText(text: String) = viewModel.setBodyWeightText(text)
            override fun nudgeBodyWeight(steps: Int) = viewModel.nudgeBodyWeight(steps)
            override fun exportBackup() = exportLauncher.launch(backupFileName(viewModel.todayDate))
            override fun importBackup() = importLauncher.launch(arrayOf(BACKUP_MIME, "text/*", "*/*"))
        }
    }

    Box(Modifier.fillMaxSize().testTag("screen-profile")) {
        state?.let { ProfileContent(it, actions) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            text = { Text(stringResource(R.string.backup_replace_question)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch { snackbar.showSnackbar(messages.getValue(viewModel.importText(text))) }
                }) { Text(stringResource(R.string.backup_replace)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun Context.writeText(uri: Uri, text: String) {
    contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
}

private fun Context.readText(uri: Uri): String? = runCatching {
    contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytes((MAX_BACKUP_BYTES + 1).toInt())
        if (bytes.size > MAX_BACKUP_BYTES) null else String(bytes)
    }
}.getOrNull()

@Composable
fun ProfileContent(state: ProfileUiState, actions: ProfileActions, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_profile),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ProfileFields(state, actions)
        TrendChart(
            title = stringResource(R.string.body_weight_chart_title),
            points = state.bodyWeights.map { ChartPoint(it.date, it.weightHg / 10.0) },
            valueText = { formatKg(Math.round(it * 10)) },
            emptyText = stringResource(R.string.body_weight_chart_empty),
        )
        SectionLabel(stringResource(R.string.backup_section))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions::exportBackup, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_export), maxLines = 1)
            }
            OutlinedButton(onClick = actions::importBackup, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_import), maxLines = 1)
            }
        }
        Text(
            text = stringResource(R.string.backup_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.picture_credits),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Name, date of birth, height and body weight: shared by the Profile and the Welcome screen. */
@Composable
fun ProfileFields(state: ProfileUiState, actions: ProfileActions, modifier: Modifier = Modifier) {
    // The fields hold what is being typed. They take the stored values again only
    // when the app itself changed them (state.revision), never mid-typing.
    var name by remember(state.revision) { mutableStateOf(state.profile.name) }
    var weightText by remember(state.revision) { mutableStateOf(state.bodyWeightText) }
    val weightInvalid = weightText.isNotBlank() && parseKgToHg(weightText) == null ||
        !state.bodyWeightValid && weightText == state.bodyWeightText

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                actions.setName(it)
            },
            label = { Text(stringResource(R.string.profile_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )

        DateOfBirthField(state.profile.dateOfBirth, state.age, actions::setDateOfBirth)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.profile_height),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            NumberChoice(
                selected = state.profile.heightFeet,
                options = FEET,
                label = { stringResource(R.string.profile_feet, it) },
                placeholder = stringResource(R.string.profile_feet_unset),
                onPick = { actions.setHeight(it, state.profile.heightInches) },
            )
            NumberChoice(
                selected = state.profile.heightInches,
                options = INCHES,
                label = { stringResource(R.string.profile_inches, it) },
                placeholder = stringResource(R.string.profile_inches_unset),
                onPick = { actions.setHeight(state.profile.heightFeet, it) },
            )
        }

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    actions.setBodyWeightText(it)
                },
                label = { Text(stringResource(R.string.profile_body_weight)) },
                suffix = { Text(stringResource(R.string.unit_kg)) },
                isError = weightInvalid,
                supportingText = if (weightInvalid) {
                    { Text(stringResource(R.string.profile_body_weight_hint)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("body-weight-field"),
            )
            // Small corrections without the keyboard: 0.1 kg per press.
            Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp))) {
                StepButton("−", stringResource(R.string.profile_body_weight_decrease), onStep = { actions.nudgeBodyWeight(-1) })
                StepButton("+", stringResource(R.string.profile_body_weight_increase), onStep = { actions.nudgeBodyWeight(+1) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthField(date: LocalDate?, age: Int?, onChange: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { picking = true }
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.profile_date_of_birth),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = date?.let(DATE_OF_BIRTH::format) ?: stringResource(R.string.profile_date_of_birth_unset),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (age != null) {
            Text(
                text = pluralStringResource(R.plurals.profile_age, age, age),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (picking) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = PastDates,
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    pickerState.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Nobody is born in the future. */
@OptIn(ExperimentalMaterial3Api::class)
private object PastDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
    override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
}

/** A button showing the chosen number (such as "5 ft") that opens a list to choose another. */
@Composable
private fun NumberChoice(
    selected: Int?,
    options: IntRange,
    label: @Composable (Int) -> String,
    placeholder: String,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(selected?.let { label(it) } ?: placeholder) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}
