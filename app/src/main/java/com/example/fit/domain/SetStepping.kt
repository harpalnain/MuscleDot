package com.example.fit.domain

enum class SetField { WEIGHT, REPS, MINUTES }

/** One press of − / + on a weight moves it 0.5 kg. */
const val WEIGHT_STEP_HG = 5
const val MAX_WEIGHT_HG = 9995
const val MAX_REPS = 999
const val MAX_MINUTES = 999

/** These values after [steps] presses of + (negative for −) on [field], kept inside the limits. */
fun SetValues.stepped(field: SetField, steps: Int): SetValues = when (field) {
    SetField.WEIGHT -> copy(weightHg = (weightHg + steps * WEIGHT_STEP_HG).coerceIn(0, MAX_WEIGHT_HG))
    SetField.REPS -> copy(reps = (reps + steps).coerceIn(1, MAX_REPS))
    SetField.MINUTES -> copy(minutes = (minutes + steps).coerceIn(1, MAX_MINUTES))
}
