package com.dev.alex.portfolio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    private val rates: FxRates = mapOf("EUR" to 1.0, "USD" to 1.10, "GBP" to 0.85)

    @Test
    fun `converts through the EUR pivot`() {
        assertEquals(110.0, convert(100.0, "EUR", "USD", rates), 1e-9)
        assertEquals(100.0, convert(110.0, "USD", "EUR", rates), 1e-9)
    }

    @Test
    fun `pence are GBP over a hundred without any GBp rate`() {
        assertEquals(1.0, convert(100.0, "GBp", "GBP", rates), 1e-9)
        assertEquals(0.01 / 0.85, convert(1.0, "GBp", "EUR", rates), 1e-9)
    }

    @Test
    fun `an unknown currency converts at 1 rather than failing`() {
        assertEquals(5.0, convert(5.0, "XYZ", "EUR", rates), 1e-9)
    }

    @Test
    fun `sign goes before the symbol`() {
        assertEquals("-€50.00", formatMoney(-50.0, "EUR"))
        assertEquals("$1,234.50", formatMoney(1234.5, "USD"))
        assertEquals("1,234.50 USD", formatMoney(1234.5, "USD", CurrencyDisplay.Code))
        assertEquals("€12.00 EUR", formatMoney(12.0, "EUR", CurrencyDisplay.Both))
    }

    @Test
    fun `unknown codes write themselves`() {
        assertEquals("USDT 1.50", formatMoney(1.5, "USDT"))
    }

    @Test
    fun `missing money is a dash, never zero`() {
        assertEquals("—", formatMoney(null, "EUR"))
        assertEquals("—", formatMoney(Double.NaN, "EUR"))
    }

    @Test
    fun `percent that rounds to zero carries no sign`() {
        assertEquals("0.00%", formatSignedPercent(-0.001))
        assertEquals("+1.24%", formatSignedPercent(1.2449))
        assertEquals("-0.62%", formatSignedPercent(-0.62))
    }

    @Test
    fun `shares keep fractions only when there are some`() {
        assertEquals("120", formatShares(120.0))
        assertEquals("12.5", formatShares(12.5))
        assertEquals("0.4500", formatShares(0.45))
    }

    @Test
    fun `base currency is the setting, else the single held one, else USD`() {
        assertEquals("CHF", resolveBaseCurrency("CHF", listOf("EUR")))
        assertEquals("GBP", resolveBaseCurrency(null, listOf("GBP", "GBp")))
        assertEquals("USD", resolveBaseCurrency(null, listOf("EUR", "USD")))
        assertEquals("USD", resolveBaseCurrency(null, emptyList()))
    }
}
