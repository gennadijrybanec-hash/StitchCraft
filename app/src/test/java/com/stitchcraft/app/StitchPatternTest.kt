package com.stitchcraft.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StitchPatternTest {
    private val palette = listOf(ThreadColor("310", "Black", 0))

    private fun pattern() = StitchPattern(
        width = 2,
        height = 2,
        palette = palette,
        cells = List(4) { PatternCell(colorIndex = 0, symbol = "●") }
    )

    @Test
    fun completedStitchesAccumulateInsteadOfMoving() {
        var p = pattern()
        p = p.updateCell(0, 0) { it.copy(completed = true) }
        p = p.updateCell(1, 1) { it.copy(completed = true) }
        assertTrue(p.cell(0, 0).completed)
        assertTrue(p.cell(1, 1).completed)
        assertEquals(2, p.completedCount())
    }

    @Test
    fun tappingCompletedStitchCanToggleOnlyThatStitch() {
        var p = pattern()
        p = p.updateCell(0, 0) { it.copy(completed = true) }
        p = p.updateCell(1, 0) { it.copy(completed = true) }
        p = p.updateCell(0, 0) { it.copy(completed = !it.completed) }
        assertFalse(p.cell(0, 0).completed)
        assertTrue(p.cell(1, 0).completed)
        assertEquals(1, p.completedCount())
    }

    @Test
    fun erasedCellsAreExcludedFromTotalsAndProgress() {
        var p = pattern()
        p = p.updateCell(0, 0) { it.copy(completed = true) }
        p = p.updateCell(1, 0) { it.copy(erased = true, completed = false) }
        assertEquals(3, p.stitchCount())
        assertEquals(1, p.completedCount())
        assertEquals(33, p.progressPercent())
    }
}
