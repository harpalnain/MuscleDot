package com.example.fit.ui.components

import com.example.fit.domain.Measure
import com.example.fit.domain.formatNumber
import kotlin.math.abs

// Pure layout arithmetic for the charts, kept apart from drawing so it can be unit tested.

/** Maps values to vertical positions. [ticks] are the axis ticks, ascending, at least two. */
class PlotScale(val ticks: List<Double>) {
    init {
        require(ticks.size >= 2) { "A scale needs at least two ticks" }
    }

    private val min = ticks.first()
    private val max = ticks.last()

    /** The lowest tick maps to [bottom], the highest to [top]. */
    fun y(value: Double, top: Float, bottom: Float): Float =
        bottom - ((value - min) / (max - min)).toFloat() * (bottom - top)
}

/** Index of the position nearest to [x], or null for an empty list. */
fun nearestIndex(xs: List<Float>, x: Float): Int? = xs.indices.minByOrNull { abs(xs[it] - x) }

/**
 * Horizontal positions for [count] marks. They spread evenly across [width] (inside
 * [inset] at both ends) when that leaves at least [minSpacing] between them;
 * otherwise they sit [minSpacing] apart and run past [width], and the plot scrolls.
 */
fun markPositions(count: Int, width: Float, minSpacing: Float, inset: Float): List<Float> = when {
    count <= 0 -> emptyList()
    count == 1 -> listOf(width / 2)
    else -> {
        val spacing = maxOf(minSpacing, (width - 2 * inset) / (count - 1))
        List(count) { inset + it * spacing }
    }
}

/** The width the marks need: at least the visible [viewport]. */
fun contentWidth(count: Int, viewport: Float, minSpacing: Float, inset: Float): Float =
    maxOf(viewport, 2 * inset + (count - 1).coerceAtLeast(0) * minSpacing)

/** "965 kg", "35 reps", "25 min" */
fun formatMeasure(measure: Measure, value: Double): String {
    val unit = when (measure) {
        Measure.LOAD_KG -> "kg"
        Measure.REPS -> "reps"
        Measure.MINUTES -> "min"
    }
    return "${formatNumber(value)} $unit"
}
