package com.example.fit.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.ProfileRepository
import com.example.fit.data.TodayHolder
import com.example.fit.data.backup.BackupRepository
import com.example.fit.domain.AppClock
import com.example.fit.domain.BodyWeightEntry
import com.example.fit.domain.MAX_BODY_WEIGHT_HG
import com.example.fit.domain.MIN_BODY_WEIGHT_HG
import com.example.fit.domain.Profile
import com.example.fit.domain.ageOn
import com.example.fit.domain.formatStepWeight
import com.example.fit.domain.parseKgToHg
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class ProfileUiState(
    val profile: Profile = Profile(),
    val age: Int? = null,
    val bodyWeightText: String = "",
    val bodyWeightValid: Boolean = true,
    val bodyWeights: List<BodyWeightEntry> = emptyList(),
    /**
     * Goes up when the app itself changes the form's values (a − / + nudge, an
     * import). The text fields keep what the user is typing until this changes.
     */
    val revision: Int = 0,
)

interface ProfileActions {
    fun setName(name: String)
    fun setDateOfBirth(date: LocalDate?)
    fun setHeight(feet: Int?, inches: Int?)

    /** Stored as today's body weight whenever the text is a valid weight. */
    fun setBodyWeightText(text: String)

    /** Moves the current body weight by [steps] tenths of a kilogram. Nothing happens when there is none. */
    fun nudgeBodyWeight(steps: Int)
    fun exportBackup()
    fun importBackup()
}

class ProfileViewModel(
    private val profile: ProfileRepository,
    private val backup: BackupRepository,
    private val today: TodayHolder,
    private val clock: AppClock,
) : ViewModel() {
    private val revision = MutableStateFlow(0)

    /** The body weight text as typed since the last revision; null shows the stored weight. */
    private val typedWeight = MutableStateFlow<String?>(null)
    private val profileWrites = Mutex()
    private val backupResults = Channel<BackupMessage>(Channel.BUFFERED)
    private val importText = MutableStateFlow<String?>(null)

    /** What happened to each export and import, delivered once each. */
    val backupMessages: Flow<BackupMessage> = backupResults.receiveAsFlow()

    /** True while a chosen file has been read and waits for the "replace everything?" answer. */
    val importWaiting: StateFlow<Boolean> = importText.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Null while loading. */
    val state: StateFlow<ProfileUiState?> = combine(
        profile.observeProfile(),
        profile.observeBodyWeights(),
        today.date,
        typedWeight,
        revision,
    ) { current, weights, date, typed, revisionNow ->
        val text = typed ?: weights.lastOrNull()?.let { formatStepWeight(it.weightHg) }.orEmpty()
        ProfileUiState(
            profile = current,
            age = current.dateOfBirth?.let { ageOn(it, date) },
            bodyWeightText = text,
            bodyWeightValid = text.isBlank() || parseKgToHg(text) != null,
            bodyWeights = weights,
            revision = revisionNow,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayDate: LocalDate get() = today.date.value

    fun setName(name: String) = change { it.copy(name = name) }

    fun setDateOfBirth(date: LocalDate?) = change { it.copy(dateOfBirth = date) }

    fun setHeight(feet: Int?, inches: Int?) = change { it.copy(heightFeet = feet, heightInches = inches) }

    fun finishWelcome() = change { it.copy(welcomeDone = true) }

    fun setBodyWeightText(text: String) {
        typedWeight.value = text
        val weightHg = parseKgToHg(text) ?: return
        viewModelScope.launch { profile.setBodyWeight(today.date.value, weightHg) }
    }

    fun nudgeBodyWeight(steps: Int) {
        viewModelScope.launch {
            val current = profile.currentBodyWeightHg() ?: return@launch
            profile.setBodyWeight(today.date.value, (current + steps).coerceIn(MIN_BODY_WEIGHT_HG, MAX_BODY_WEIGHT_HG))
            showStoredValues()
        }
    }

    /**
     * Saves a backup with [write], which puts the text in the file the user chose.
     * It runs in this ViewModel, so leaving the screen or turning the phone does not
     * cut it short, and a file that could not be written is reported.
     */
    fun exportBackup(write: suspend (String) -> Unit) {
        viewModelScope.launch { backupResults.send(saveBackup({ backup.export(clock.now()) }, write)) }
    }

    /** Reads the file the user chose with [read]; if that works, [importWaiting] asks before anything is replaced. */
    fun readImport(read: suspend () -> String?) {
        viewModelScope.launch {
            when (val result = readBackup(read)) {
                is BackupRead.Text -> importText.value = result.text
                is BackupRead.Failed -> backupResults.send(result.message)
            }
        }
    }

    /** The user said yes: everything in the app is replaced by the file that was read. */
    fun confirmImport() {
        val text = importText.value ?: return
        importText.value = null
        viewModelScope.launch {
            val message = restoreBackup { backup.import(text) }
            if (message == BackupMessage.RESTORED) showStoredValues()
            backupResults.send(message)
        }
    }

    fun cancelImport() {
        importText.value = null
    }

    /** Drops what was typed and makes the form show what is stored. */
    private fun showStoredValues() {
        typedWeight.value = null
        revision.value++
    }

    /** Profile changes are applied one after another, each to the latest stored profile. */
    private fun change(update: (Profile) -> Profile) {
        viewModelScope.launch {
            profileWrites.withLock { profile.saveProfile(update(profile.observeProfile().first())) }
        }
    }
}
