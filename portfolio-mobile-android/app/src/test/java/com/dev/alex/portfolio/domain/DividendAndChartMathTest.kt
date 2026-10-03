package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.HoldingDto
import com.dev.alex.portfolio.data.api.TransactionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DividendAndChartMathTest {
    private val ctx = CurrencyContext("EUR", CurrencyDisplay.Symbol, mapOf("EUR" to 1.0, "USD" to 1.25, "GBP" to 0.8))

    @Test
    fun `a running year is compared on its finished months only`() {
        val amounts = mapOf(
            MonthKey(2025, 0) to 100.0,
            MonthKey(2025, 1) to 100.0,
            MonthKey(2025, 2) to 500.0,
            MonthKey(2026, 0) to 110.0,
            MonthKey(2026, 1) to 110.0,
        )
        // now = March 2026: Jan–Feb are finished, March is not
        val income = DividendIncome(amounts, MonthKey(2026, 2))
        val year2026 = income.byYear().single { it.label == "2026" }
        assertEquals("YTD", year2026.tag)
        assertEquals(10.0, year2026.change!!.pct, 1e-9)
        assertEquals("Jan–Feb", year2026.change!!.months)
        assertEquals("2025", year2026.change!!.vs)
    }

    @Test
    fun `the first year of all has nothing to compare against`() {
        val income = DividendIncome(mapOf(MonthKey(2024, 5) to 50.0), MonthKey(2026, 2))
        assertNull(income.byYear().single().change)
    }

    @Test
    fun `month keys survive any timezone`() {
        assertEquals(MonthKey(2024, 0), MonthKey.parse("2024-01"))
        assertEquals(MonthKey(2024, 4), MonthKey.parse("2024-05-14"))
        assertEquals(MonthKey(2023, 11), MonthKey(2024, 0).shift(-1))
        assertEquals("2024-01", MonthKey(2024, 0).key)
    }

    @Test
    fun `the last batch moves the projection by quantity times annual DPS in book currency`() {
        val holdings = listOf(
            HoldingDto(ticker = "O", shareAmount = 40.0, dividend = 3.20, currency = "USD", quoteCurrency = "USD").normalize(ctx.rates),
            HoldingDto(ticker = "VOD.L", shareAmount = 100.0, dividend = 8.0, currency = "GBP", quoteCurrency = "GBp").normalize(ctx.rates),
        )
        val transactions = listOf(
            TransactionDto(ticker = "O", transactionType = "BUY", quantity = 10.0, date = "2026-09-18"),
            TransactionDto(ticker = "VOD.L", transactionType = "SELL", quantity = 100.0, date = "2026-09-18"),
            TransactionDto(ticker = "O", transactionType = "BUY", quantity = 30.0, date = "2026-03-02"),
            TransactionDto(ticker = "O", transactionType = "DIVIDEND", amount = 5.0, date = "2026-09-30"),
        )
        val batch = lastBatch(transactions, holdings, ctx, 2026)!!
        assertEquals("2026-09-18", batch.date)
        assertEquals(2, batch.moves.size)
        // +10 × 3.20 USD → 25.6 EUR; −100 × 0.08 GBP → −10 EUR
        assertEquals(25.6 - 10.0, batch.yearlyDelta, 1e-9)
    }

    @Test
    fun `chart range clamps to the months the series has`() {
        val months = (0..5).map { MonthKey(2026, it) }
        val today = LocalDate.of(2026, 6, 15)
        assertEquals(MonthKey(2026, 0), rangeStart("1Y", months, today))
        assertEquals(MonthKey(2026, 0), rangeStart("YTD", months, today))
        // June − 3 months = March, as the web's rangeStartMonth counts it
        assertEquals(MonthKey(2026, 2), rangeStart("3M", months, today))
        assertEquals(MonthKey(2026, 5), rangeStart("1M", listOf(MonthKey(2026, 5)), today))
    }

    @Test
    fun `drift within one point is on target`() {
        assertEquals(Drift.On, driftOf(10.5, 10.0))
        assertEquals(Drift.Over, driftOf(12.0, 10.0))
        assertEquals(Drift.Under, driftOf(8.0, 10.0))
        assertEquals("12.5", formatTarget(12.5))
        assertEquals("20", formatTarget(20.0))
    }
}
