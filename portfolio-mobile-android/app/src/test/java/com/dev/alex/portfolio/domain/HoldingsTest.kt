package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.HoldingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoldingsTest {
    private val rates: FxRates = mapOf("EUR" to 1.0, "USD" to 1.25, "GBP" to 0.8)

    @Test
    fun `a pence-quoted London line is brought into pounds and gets its profit`() {
        val dto = HoldingDto(
            ticker = "VOD.L",
            shareAmount = 100.0,
            costPerShare = 0.70,
            costBasis = 70.0,
            currentShareValue = 78.5,
            currentTotalValue = 7850.0,
            dividend = 6.2,
            dailyChange = 1.5,
            currency = "GBP",
            quoteCurrency = "GBp",
        )
        val h = dto.normalize(rates)
        assertEquals(0.785, h.currentShareValue!!, 1e-9)
        assertEquals(78.5, h.totalValue, 1e-9)
        assertEquals(8.5, h.totalProfit!!, 1e-9)
        assertEquals(8.5 / 70.0 * 100, h.totalProfitPercentage!!, 1e-9)
        assertEquals(0.062, h.dividend!!, 1e-9)
        // the quoted price survives for the 52-week range
        assertEquals(78.5, h.quoteShareValue!!, 1e-9)
    }

    @Test
    fun `a coin bought in EUR but priced in USD is converted at the rate`() {
        val dto = HoldingDto(
            ticker = "ETH",
            shareAmount = 2.0,
            costBasis = 3000.0,
            currentShareValue = 2500.0,
            currentTotalValue = 5000.0,
            currency = "EUR",
            quoteCurrency = "USD",
        )
        val h = dto.normalize(rates)
        assertEquals(4000.0, h.totalValue, 1e-9)
        assertEquals(1000.0, h.totalProfit!!, 1e-9)
    }

    @Test
    fun `same currency keeps the backend's own profit`() {
        val dto = HoldingDto(
            ticker = "KO",
            shareAmount = 10.0,
            currentTotalValue = 700.0,
            totalProfit = 42.0,
            currency = "USD",
            quoteCurrency = "USD",
        )
        assertEquals(42.0, dto.normalize(rates).totalProfit!!, 1e-9)
    }

    @Test
    fun `day change percent is off yesterday's close`() {
        val h = HoldingDto(ticker = "X", currentShareValue = 110.0, dailyChange = 10.0, currency = "USD").normalize(rates)
        assertEquals(10.0, h.dayChangePercent!!, 1e-9)
        assertNull(HoldingDto(ticker = "Y", currency = "USD").normalize(rates).dayChangePercent)
    }
}
