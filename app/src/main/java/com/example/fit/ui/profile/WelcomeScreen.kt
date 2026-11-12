package com.example.fit.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fit.R
import com.example.fit.ui.overloadViewModel
import java.time.LocalDate

/** Shown the first time the app opens: the profile, which can be skipped (spec 5.12). */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val viewModel = overloadViewModel { ProfileViewModel(it.profile, it.backup, it.today, it.clock) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        object : ProfileActions {
            override fun setName(name: String) = viewModel.setName(name).let { }
            override fun setDateOfBirth(date: LocalDate?) = viewModel.setDateOfBirth(date).let { }
            override fun setHeight(feet: Int?, inches: Int?) = viewModel.setHeight(feet, inches).let { }
            override fun setBodyWeightText(text: String) = viewModel.setBodyWeightText(text)
            override fun nudgeBodyWeight(steps: Int) = viewModel.nudgeBodyWeight(steps)
            override fun exportBackup() {}
            override fun importBackup() {}
        }
    }
    val finish = {
        viewModel.finishWelcome()
        onFinished()
    }
    Box(Modifier.fillMaxSize().testTag("screen-welcome")) {
        state?.let { WelcomeContent(it, actions, onSkip = finish, onDone = finish) }
    }
}

@Composable
fun WelcomeContent(
    state: ProfileUiState,
    actions: ProfileActions,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.welcome_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProfileFields(state, actions)
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.welcome_skip)) }
            Button(onClick = onDone, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.done)) }
        }
    }
}
