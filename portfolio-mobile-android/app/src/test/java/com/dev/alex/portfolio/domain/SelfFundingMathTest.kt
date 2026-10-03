package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.CalendarEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfFundingMathTest {
    private fun row(price: Double, dps: Double, held: Double, payments: Int? = 4) =
        SfRow("KO", "Coca-Cola", "USD", price, dps, held, payments)

    @Test
    fun `needed is the price over one period's dividend, rounded up`() {
        val calc = calcSelfFunding(row(price = 71.20, dps = 2.04, held = 14.0), Period.Quarterly)
        // 71.20 / (2.04 / 4) = 139.6 → 140
        assertEquals(140L, calc.needed)
        assertEquals(126.0, calc.gapShares, 1e-9)
        assertEquals(126 * 71.20, calc.gapCost, 1e-9)
        assertEquals(Tier.VeryFar, calc.tier)
        assertEquals(0.51, calc.perPayment!!, 1e-9)
    }

    @Test
    fun `coverage is uncapped past the threshold`() {
        val calc = calcSelfFunding(row(price = 10.0, dps = 1.0, held = 52.0), Period.Yearly)
        assertTrue(calc.reached)
        assertEquals(5.2, calc.sharesPerPeriod, 1e-9)
        assertEquals(1.0, calc.progress, 1e-9)
        assertEquals("520%", coverageLabel(calc))
    }

    @Test
    fun `a row short of a whole share never reads as reached`() {
        // 9.999 shares × 1.0 / 10.0 = 0.9999 of a share
        val calc = calcSelfFunding(row(price = 10.0, dps = 1.0, held = 9.999), Period.Yearly)
        assertEquals("99%", coverageLabel(calc))
    }

    @Test
    fun `nothing held means no reinvestment projection`() {
        assertNull(calcSelfFunding(row(price = 10.0, dps = 1.0, held = 0.0), Period.Yearly).years)
    }

    @Test
    fun `cadence is the number of distinct paying months`() {
        val calendar = mapOf(
            "JANUARY" to listOf(CalendarEntryDto("KO"), CalendarEntryDto("O")),
            "APRIL" to listOf(CalendarEntryDto("KO"), CalendarEntryDto("O")),
            "MAY" to listOf(CalendarEntryDto("O")),
        )
        val payments = paymentsPerYearByTicker(calendar)
        assertEquals(2, payments["KO"])
        assertEquals(3, payments["O"])
        assertEquals("Unknown", cadenceLabel(null))
        assertEquals("Quarterly", cadenceLabel(4))
    }
}
