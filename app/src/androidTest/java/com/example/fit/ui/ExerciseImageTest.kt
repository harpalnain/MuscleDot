package com.example.fit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.ui.components.ExerciseImage
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Rule
import org.junit.Test

class ExerciseImageTest {
    @get:Rule
    val rule = createComposeRule()

    private val builtIn = Exercise(
        "bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS,
        imageBase = "exercises/bench-press",
    )

    @Test
    fun builtInShowsItsPicture_customShowsAnIcon() {
        rule.setContent {
            OverloadTheme {
                Column {
                    ExerciseImage(builtIn, Modifier.size(56.dp))
                    ExerciseImage(builtIn.copy(id = "custom-1", isCustom = true, imageBase = null), Modifier.size(56.dp))
                }
            }
        }
        rule.onNodeWithTag("exercise-image").assertIsDisplayed()
        rule.onNodeWithTag("exercise-icon").assertIsDisplayed()
    }
}
