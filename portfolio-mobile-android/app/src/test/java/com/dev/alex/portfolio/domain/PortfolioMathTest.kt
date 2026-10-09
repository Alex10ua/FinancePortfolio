package com.dev.alex.portfolio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioMathTest {
    @Test
    fun `bar scale is the largest weight or target rounded up to five`() {
        assertEquals(25.0, barScaleMax(listOf(12.0, 21.3), listOf(18.0)), 0.0)
        // a target above every weight sets the scale
        assertEquals(30.0, barScaleMax(listOf(12.0), listOf(27.5)), 0.0)
        // an exact multiple of five stays put
        assertEquals(20.0, barScaleMax(listOf(20.0), emptyList()), 0.0)
    }

    @Test
    fun `bar scale never drops under ten percent`() {
        assertEquals(10.0, barScaleMax(listOf(2.0, 3.5), emptyList()), 0.0)
        assertEquals(10.0, barScaleMax(emptyList(), emptyList()), 0.0)
    }

    @Test
    fun `one point either side of a target is on target`() {
        assertEquals(Drift.On, driftOf(10.5, 10.0))
        assertEquals(Drift.Over, driftOf(11.0, 10.0))
        assertEquals(Drift.Under, driftOf(9.0, 10.0))
    }
}
