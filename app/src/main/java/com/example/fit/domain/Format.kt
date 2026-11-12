package com.example.fit.domain

import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// The app is English only for now, so numbers and dates use one fixed locale.
private val LOCALE = Locale.US
private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM", LOCALE)
private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM", LOCALE)
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE)
private val WEEKDAY_SHORT = DateTimeFormatter.ofPattern("EEE", LOCALE)
private val BODY_WEIGHT_TEXT = Regex("""\d{1,3}(\.\d)?""")
/** Body weight is accepted from 20.0 to 400.0 kg. */
const val MIN_BODY_WEIGHT_HG = 200
const val MAX_BODY_WEIGHT_HG = 4000

/** Kilograms with thousands separators, and one decimal only when needed: "1,010", "42.5". */
private fun kgText(hg: Long): String {
    val whole = String.format(LOCALE, "%,d", hg / 10)
    val tenth = hg % 10
    return if (tenth == 0L) whole else "$whole.$tenth"
}

/** A plain figure with at most one decimal: "965", "987.5", "1,010". */
fun formatNumber(value: Double): String = kgText(Math.round(value * 10))

/** "1,010 kg", "42.5 kg", "0 kg". */
fun formatKg(hg: Long): String = "${kgText(hg)} kg"

/** A weight as shown between − and +: always one decimal and no unit, "40.0", "42.5". */
fun formatStepWeight(hg: Int): String = String.format(LOCALE, "%d.%d", hg / 10, hg % 10)

/** Axis labels: in full below 10,000 ("600", "1,000", "72.5"), compact from there ("20K", "17.2K"). */
fun formatAxis(value: Double): String {
    val compact = value >= 10_000
    val tenths = Math.round((if (compact) value / 1000 else value) * 10)
    return kgText(tenths) + if (compact) "K" else ""
}

/**
 * Body weight typed by the user, as hectograms: "72.3" gives 723. Null unless it
 * is a number from 20.0 to 400.0 kg with at most one decimal.
 */
fun parseKgToHg(text: String): Int? {
    val trimmed = text.trim()
    if (!BODY_WEIGHT_TEXT.matches(trimmed)) return null
    val parts = trimmed.split('.')
    val hg = parts[0].toInt() * 10 + (parts.getOrNull(1)?.toInt() ?: 0)
    return hg.takeIf { it in MIN_BODY_WEIGHT_HG..MAX_BODY_WEIGHT_HG }
}

/** Age in whole years on [today]. */
fun ageOn(dateOfBirth: LocalDate, today: LocalDate): Int = Period.between(dateOfBirth, today).years

/** "Thursday, 12 November" */
fun formatDayLong(date: LocalDate): String = DAY_LONG.format(date)

/** "5 Nov" */
fun formatDayShort(date: LocalDate): String = DAY_SHORT.format(date)

/** "November 2026" */
fun formatMonth(month: YearMonth): String = MONTH.format(month)

/** "Wed" */
fun formatWeekdayShort(date: LocalDate): String = WEEKDAY_SHORT.format(date)

/**
 * One line describing an exercise's sets, from its first set: "3 sets · 40 kg × 10".
 * Body weight with extra weight reads "3 sets · +5 kg × 10"; body weight without
 * extra and reps only read "3 sets · 10 reps"; cardio reads "10 min".
 */
fun setsSummary(kind: ExerciseKind, sets: List<SetValues>): String {
    val first = sets.firstOrNull() ?: SetValues()
    if (kind == ExerciseKind.CARDIO) return "${first.minutes} min"
    val count = if (sets.size == 1) "1 set" else "${sets.size} sets"
    val each = when {
        kind == ExerciseKind.WEIGHTS -> "${formatKg(first.weightHg.toLong())} × ${first.reps}"
        kind == ExerciseKind.BODY_WEIGHT && first.weightHg > 0 -> "+${formatKg(first.weightHg.toLong())} × ${first.reps}"
        else -> "${first.reps} reps"
    }
    return "$count · $each"
}
