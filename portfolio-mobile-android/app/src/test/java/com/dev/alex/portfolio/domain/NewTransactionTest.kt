package com.dev.alex.portfolio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NewTransactionTest {
    private val today = LocalDate.of(2026, 10, 4)
    private val buy = TransactionDraft(date = today, ticker = "aapl ", quantity = "10", price = "150,25", currency = "USD")

    @Test
    fun `decimal comma and grouping space both parse`() {
        assertEquals(BigDecimal("1234.5"), parseDecimal("1 234,5"))
        assertEquals(BigDecimal("0.1"), parseDecimal(" 0.1 "))
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("1.234,50"))
    }

    @Test
    fun `a stock buy goes out as exact decimal strings`() {
        assertNull(validate(buy, today))
        val request = buy.toRequest()
        assertEquals("AAPL", request.ticker)
        assertEquals("STOCK", request.assetType)
        assertEquals("150.25", request.price)
        assertEquals("10", request.quantity)
        assertEquals("0", request.commission)
        assertEquals("2026-10-04", request.date)
        assertNull(request.amount)
    }

    @Test
    fun `cash books under its currency with no asset type`() {
        val deposit = TransactionDraft(date = today).withKind(AssetKind.Cash).copy(amount = "500", currency = "EUR")
        assertEquals("DEPOSIT", deposit.type)
        assertNull(validate(deposit, today))
        val request = deposit.toRequest()
        assertEquals("EUR", request.ticker)
        assertNull(request.assetType)
        assertEquals("500", request.amount)
        assertEquals("0", request.quantity)
    }

    @Test
    fun `switching asset kind resets the type and the ticker`() {
        val sell = buy.copy(type = "SELL").withKind(AssetKind.Crypto)
        assertEquals("BUY", sell.type)
        assertEquals("", sell.ticker)
        // same kind again changes nothing
        assertEquals(buy, buy.withKind(AssetKind.Stock))
    }

    @Test
    fun `validation names the first missing piece`() {
        assertEquals("Enter a ticker.", validate(buy.copy(ticker = " "), today))
        assertEquals("Pick a custom asset.", validate(buy.withKind(AssetKind.Custom), today))
        assertEquals("The quantity must be more than 0.", validate(buy.copy(quantity = "0"), today))
        assertEquals("Enter the price.", validate(buy.copy(price = "abc"), today))
        assertEquals("The commission can't be negative.", validate(buy.copy(commission = "-1"), today))
        assertEquals("The date can't be in the future.", validate(buy.copy(date = today.plusDays(1)), today))
        assertEquals("Pick a transaction type.", validate(buy.copy(type = "DEPOSIT"), today))
    }

    @Test
    fun `total is shares times price and absent for cash`() {
        assertEquals(0, BigDecimal("1502.50").compareTo(buy.total()))
        assertNull(buy.copy(price = "").total())
        assertNull(buy.withKind(AssetKind.Cash).total())
    }
}
