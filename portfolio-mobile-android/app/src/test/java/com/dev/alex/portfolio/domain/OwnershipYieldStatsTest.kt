package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.StatisticsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnershipYieldStatsTest {
    private fun holding(ticker: String, shares: Double, outstanding: Long?) = Holding(
        ticker = ticker, name = null, assetType = "STOCK", shareAmount = shares, costPerShare = null,
        costBasis = null, currentShareValue = null, currentTotalValue = null, dividend = null,
        dividendYield = null, dividendYieldOnCost = null, totalProfit = null, totalProfitPercentage = null,
        dailyChange = null, currency = "USD", quoteCurrency = "USD", quoteShareValue = null,
        sharesOutstanding = outstanding,
    )

    @Test
    fun `stakes rank largest first and custom assets drop out`() {
        val view = ownershipView(
            listOf(holding("KO", 400.0, 4_300_000_000), holding("SMLR", 40.0, 7_000_000), holding("COIN-1", 3.0, null)),
            null,
        )!!
        assertEquals(listOf("SMLR", "KO"), view.rows.map { it.ticker })
        assertEquals(OwnershipTier.Meaningful, view.rows[0].tier)
        assertEquals("175k", formatOneIn(view.rows[0].oneIn))
        assertEquals("10.8M", formatOneIn(view.rows[1].oneIn))
    }

    @Test
    fun `a projection re-ranks without touching the base`() {
        val view = ownershipView(
            listOf(holding("KO", 400.0, 4_300_000_000), holding("SMLR", 40.0, 7_000_000)),
            mapOf("SMLR" to 0.0),
        )!!
        val smlr = view.rows.first { it.ticker == "SMLR" }
        assertEquals(-40.0, smlr.delta, 0.0)
        assertEquals(1, smlr.baseRank)
        assertEquals(listOf("KO"), view.held.map { it.ticker })
        assertEquals(440.0, view.baseTotal, 0.0)
    }

    @Test
    fun `steppers move a tenth of the position, snapped clean`() {
        assertEquals(40.0, stepFor(400.0), 0.0)
        assertEquals(0.04, stepFor(0.42), 1e-12)
        assertEquals(1.0, stepFor(0.0), 0.0)
        assertEquals(0.3, round6(0.1 + 0.2), 0.0)
    }

    @Test
    fun `yield percentile ranks today against the window`() {
        val history = listOf(2.0, 3.0, 4.0, 5.0, 6.0)
        val stats = yieldStats(history, 5.0, YieldTimeframe.All)!!
        assertEquals(80.0, stats.percentile, 1e-9)
        assertEquals(4.0, stats.median, 1e-9)
        assertEquals(25.0, stats.vsMedian, 1e-9)
        assertEquals(5.6, stats.p90, 1e-9)
        // a 1-year window keeps only the last twelve months
        assertEquals(12, yieldStats(List(30) { it.toDouble() }, 1.0, YieldTimeframe.Y1)!!.sorted.size)
        assertNull(yieldStats(emptyList(), 5.0, YieldTimeframe.Y5))
        assertNull(yieldStats(history, null, YieldTimeframe.Y5))
    }

    @Test
    fun `ordinals`() {
        assertEquals("st", ordinal(91.0))
        assertEquals("th", ordinal(11.0))
        assertEquals("nd", ordinal(72.4))
    }

    @Test
    fun `statistics keep Yahoo's scaling and show pence as pence`() {
        val stats = StatisticsDto(
            currency = "GBp",
            profitMargin = 0.3934,
            dividendYield = 0.93,
            fiftyTwoWeekHigh = 3104.5,
            marketCap = 2.5e12,
        )
        val groups = statisticsGroups(stats).associateBy { it.id }
        val value = { id: String, label: String -> groups.getValue(id).values.first { it.label == label }.value }
        assertEquals("39.34%", value("profit", "Profit margin"))
        assertEquals("0.93%", value("dividends", "Forward dividend yield"))
        assertEquals("3104.50p", value("trading", "52-week high"))
        assertEquals("2.50Tp", value("valuation", "Market cap"))
        assertNull(value("valuation", "Trailing P/E"))
        assertEquals(1, groups.getValue("profit").reported)
    }
}
