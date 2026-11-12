package com.example.fit.domain

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** What a per-session chart measures. Load is shown in kilograms. */
enum class Measure { LOAD_KG, REPS, MINUTES }

data class ChartPoint(val date: LocalDate, val value: Double)

/** Points of a per-session chart and their moving average, one average value per point. */
data class SessionSeries(val measure: Measure, val points: List<ChartPoint>, val average: List<Double>)

private fun series(measure: Measure, points: List<ChartPoint>, n: Int) =
    SessionSeries(measure, points, movingAverage(points.map { it.value }, n))

/**
 * Chart of one exercise (spec 5.10). [routineId] null means all routines; with a
 * routine chosen, the average is taken over that routine's sessions only.
 */
fun exerciseSeries(kind: ExerciseKind, sessions: List<ExerciseSession>, routineId: Long?, n: Int): SessionSeries {
    val measure = when (kind) {
        ExerciseKind.WEIGHTS, ExerciseKind.BODY_WEIGHT -> Measure.LOAD_KG
        ExerciseKind.REPS_ONLY -> Measure.REPS
        ExerciseKind.CARDIO -> Measure.MINUTES
    }
    val points = sessions
        .filter { routineId == null || it.routineId == routineId }
        .sortedBy { it.date }
        .map { session ->
            val value = when (measure) {
                Measure.LOAD_KG -> session.loadHg() / 10.0
                Measure.REPS -> session.totalReps().toDouble()
                Measure.MINUTES -> session.totalMinutes().toDouble()
            }
            ChartPoint(session.date, value)
        }
    return series(measure, points, n)
}

/**
 * Chart of one routine: [workouts] are that routine's saved workouts. It shows load,
 * unless no workout has any load: then minutes if there is cardio, otherwise reps.
 */
fun routineSeries(workouts: List<Workout>, n: Int): SessionSeries {
    val sorted = workouts.sortedBy { it.date }
    val measure = when {
        sorted.isEmpty() || sorted.any { it.loadHg() > 0 } -> Measure.LOAD_KG
        sorted.any { it.cardioMinutes() > 0 } -> Measure.MINUTES
        else -> Measure.REPS
    }
    val points = sorted.map { workout ->
        val value = when (measure) {
            Measure.LOAD_KG -> workout.loadHg() / 10.0
            Measure.MINUTES -> workout.cardioMinutes().toDouble()
            Measure.REPS -> workout.exercises.sumOf { it.totalReps() }.toDouble()
        }
        ChartPoint(workout.date, value)
    }
    return series(measure, points, n)
}

data class WeekTotal(val weekStart: LocalDate, val loadHg: Long, val cardioMinutes: Int, val workouts: Int)

/**
 * One entry per week (Monday to Sunday) from the first saved workout's week through
 * [today]'s week. Weeks with no workout are included with zeros, so time stays honest.
 */
fun weeklyTotals(saved: List<Workout>, today: LocalDate): List<WeekTotal> {
    if (saved.isEmpty()) return emptyList()
    val byWeek = saved.groupBy { weekStart(it.date) }
    val first = byWeek.keys.min()
    val last = maxOf(weekStart(today), byWeek.keys.max())
    return generateSequence(first) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(last) }
        .map { week ->
            val workouts = byWeek[week].orEmpty()
            WeekTotal(week, workouts.sumOf { it.loadHg() }, workouts.sumOf { it.cardioMinutes() }, workouts.size)
        }
        .toList()
}

private val NICE_FACTORS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)
private const val TOLERANCE = 1e-9

/**
 * Axis ticks covering [min]..[max] at a round step. The step is the smallest of
 * 1, 2, 2.5, 5, 10 times a power of ten that is at least half the range, which
 * gives three or four ticks. A single value is first widened by 1 on each side.
 */
fun niceTicks(min: Double, max: Double): List<Double> {
    var low = min
    var high = max
    if (high <= low) {
        low -= 1
        high += 1
    }
    val rough = (high - low) / 2
    val power = 10.0.pow(floor(log10(rough)))
    val step = NICE_FACTORS.map { it * power }.first { it >= rough * (1 - TOLERANCE) }
    val firstIndex = floor(low / step + TOLERANCE).toLong()
    val lastIndex = ceil(high / step - TOLERANCE).toLong()
    return (firstIndex..lastIndex).map { it * step }
}
