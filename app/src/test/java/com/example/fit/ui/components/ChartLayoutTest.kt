package com.example.fit.ui.components

import com.example.fit.domain.Measure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartLayoutTest {
    @Test
    fun scaleMapsValuesBetweenBottomAndTop() {
        val scale = PlotScale(listOf(600.0, 800.0, 1000.0, 1200.0))
        assertEquals(100f, scale.y(600.0, top = 0f, bottom = 100f), 0.001f)
        assertEquals(0f, scale.y(1200.0, top = 0f, bottom = 100f), 0.001f)
        assertEquals(50f, scale.y(900.0, top = 0f, bottom = 100f), 0.001f)
    }

    @Test
    fun nearestMark() {
        assertEquals(1, nearestIndex(listOf(10f, 40f, 70f), 52f))
        assertEquals(2, nearestIndex(listOf(10f, 40f, 70f), 900f))
        assertNull(nearestIndex(emptyList(), 5f))
    }

    @Test
    fun marksSpreadWhenTheyFit_andKeepMinimumSpacingWhenTheyDoNot() {
        assertEquals(listOf(10f, 150f, 290f), markPositions(3, width = 300f, minSpacing = 28f, inset = 10f))
        assertEquals(listOf(10f, 38f, 66f, 94f), markPositions(4, width = 60f, minSpacing = 28f, inset = 10f))
        assertEquals(listOf(150f), markPositions(1, width = 300f, minSpacing = 28f, inset = 10f))
    }

    @Test
    fun measureText() {
        assertEquals("965 kg", formatMeasure(Measure.LOAD_KG, 965.0))
        assertEquals("987.5 kg", formatMeasure(Measure.LOAD_KG, 987.5))
        assertEquals("35 reps", formatMeasure(Measure.REPS, 35.0))
        assertEquals("25 min", formatMeasure(Measure.MINUTES, 25.0))
    }
}
