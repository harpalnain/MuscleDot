package com.example.fit.data

import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import kotlinx.serialization.Serializable

/**
 * One entry of `assets/exercises.json`, the built-in exercise list. Its two
 * pictures are the assets `exercises/<id>_0.webp` and `exercises/<id>_1.webp`.
 * The file is produced by `tools/exercise-pack`.
 */
@Serializable
data class BuiltInExercise(
    val id: String,
    val name: String,
    val group: MuscleGroup,
    val muscle: String,
    val equipment: String,
    val kind: ExerciseKind,
)
