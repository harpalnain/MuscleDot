package com.example.fit.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fit.data.ProfileRepository
import com.example.fit.data.TodayHolder
import com.example.fit.data.backup.BackupException
import com.example.fit.data.backup.BackupRepository
import com.example.fit.domain.AppClock
import com.example.fit.domain.BodyWeightEntry
import com.example.fit.domain.MAX_BODY_WEIGHT_HG
import com.example.fit.domain.MIN_BODY_WEIGHT_HG
import com.example.fit.domain.Profile
import com.example.fit.domain.ageOn
import com.example.fit.domain.formatStepWeight
import com.example.fit.domain.parseKgToHg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

/** What happened to an export or import, for a message to the user. */
enum class BackupMessage { SAVED, RESTORED, NOT_A_BACKUP, NEWER_VERSION }

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

    suspend fun exportText(): String = backup.export(clock.now())

    suspend fun importText(text: String): BackupMessage = try {
        backup.import(text)
        showStoredValues()
        BackupMessage.RESTORED
    } catch (e: BackupException) {
        when (e.reason) {
            BackupException.Reason.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
            BackupException.Reason.NEWER_VERSION -> BackupMessage.NEWER_VERSION
        }
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
