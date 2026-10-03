package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.CashHoldingDto
import com.dev.alex.portfolio.data.api.PerformancePointDto
import java.time.LocalDate

// ---------------------------------------------------------------- portfolio list

data class AllocSegment(val label: String, val weight: Double, val color: Long)

data class PortfolioSummary(
    val portfolioId: String,
    val name: String,
    val value: Double,
    val cost: Double,
    val profit: Double,
    val profitPct: Double,
    /** the portfolio's own base currency — each card is shown in it */
    val currency: String,
    val assetCount: Int,
    val allocation: List<AllocSegment>,
)

/** Port of the web's `usePortfolioValues`, one portfolio at a time. */
fun summarizePortfolio(
    portfolioId: String,
    name: String,
    holdings: List<Holding>,
    prefs: PortfolioPrefs,
    rates: FxRates,
): PortfolioSummary {
    val ctx = currencyContextFor(prefs, holdings, rates)
    val value = holdings.sumOf { ctx.toBase(it.totalValue, it.currency) }
    val cost = holdings.sumOf { ctx.toBase(it.costBasisValue, it.currency) }
    val profit = value - cost

    val byType = linkedMapOf<String, Double>()
    for (h in holdings) {
        val type = h.assetType ?: "CUSTOM"
        byType[type] = (byType[type] ?: 0.0) + ctx.toBase(h.totalValue, h.currency)
    }
    val typeTotal = byType.values.sum()
    val allocation = byType.entries
        .sortedByDescending { it.value }
        .map { (type, amount) ->
            AllocSegment(
                label = assetLabel(type),
                weight = if (typeTotal > 0) amount / typeTotal * 100 else 0.0,
                color = ASSET_COLORS[type] ?: 0xFF94A3B8,
            )
        }

    return PortfolioSummary(
        portfolioId = portfolioId,
        name = name,
        value = value,
        cost = cost,
        profit = profit,
        profitPct = if (cost > 0) profit / cost * 100 else 0.0,
        currency = ctx.base,
        assetCount = holdings.size,
        allocation = allocation,
    )
}

data class NetWorth(
    /** biggest first — cards and the allocation bar share this order and its colours */
    val items: List<PortfolioSummary>,
    val total: Double,
    val totalCost: Double,
    /** one currency when every portfolio shares it, else USD */
    val currency: String,
) {
    val profit: Double get() = total - totalCost
    val profitPct: Double get() = if (totalCost > 0) profit / totalCost * 100 else 0.0
    val assetCount: Int get() = items.sumOf { it.assetCount }
    val best: PortfolioSummary? get() = items.filter { it.cost > 0 }.maxByOrNull { it.profitPct }

    /** each portfolio's share of the total, in its card's colour order */
    fun weightOf(item: PortfolioSummary, rates: FxRates): Double =
        if (total > 0) convert(item.value, item.currency, currency, rates) / total * 100 else 0.0
}

fun netWorth(items: List<PortfolioSummary>, rates: FxRates): NetWorth {
    val currencies = items.map { it.currency }.toSet()
    val currency = if (currencies.size == 1) currencies.first() else "USD"
    return NetWorth(
        items = items.sortedByDescending { convert(it.value, it.currency, currency, rates) },
        total = items.sumOf { convert(it.value, it.currency, currency, rates) },
        totalCost = items.sumOf { convert(it.cost, it.currency, currency, rates) },
        currency = currency,
    )
}

/** Card colours by position in the value-sorted list — same palette as the web. */
val PORTFOLIO_PALETTE: List<Long> = listOf(
    0xFF4F46E5, 0xFF14B8A6, 0xFFF59E0B, 0xFF8B5CF6,
    0xFFEF4444, 0xFF10B981, 0xFF3B82F6, 0xFFEC4899,
)

// ---------------------------------------------------------------- dashboard

data class DashboardStats(
    val totalValue: Double,
    val totalCost: Double,
    val unrealized: Double,
    val realized: Double,
    val cash: Double,
    /** mean of the rows' dividend yields, as the web's Avg Yield card computes it */
    val avgYield: Double,
    val dayChange: Double,
    val holdingsCount: Int,
) {
    val totalWithCash: Double get() = totalValue + cash
    val totalProfit: Double get() = unrealized + realized
    val totalProfitPct: Double? get() = if (totalCost > 0) totalProfit / totalCost * 100 else null
    val dayChangePct: Double?
        get() {
            val yesterday = totalValue - dayChange
            return if (yesterday > 0) dayChange / yesterday * 100 else null
        }
}

fun dashboardStats(
    holdings: List<Holding>,
    ctx: CurrencyContext,
    cash: List<CashHoldingDto>,
    realizedByCurrency: Map<String, Double?>,
): DashboardStats = DashboardStats(
    totalValue = holdings.sumOf { ctx.toBase(it.totalValue, it.currency) },
    totalCost = holdings.sumOf { ctx.toBase(it.costBasisValue, it.currency) },
    unrealized = holdings.sumOf { ctx.toBase(it.totalProfit ?: 0.0, it.currency) },
    realized = realizedByCurrency.entries.sumOf { (currency, amount) -> ctx.toBase(amount ?: 0.0, currency) },
    cash = cash.sumOf { ctx.toBase(it.amount, it.currency) },
    avgYield = if (holdings.isEmpty()) 0.0 else holdings.sumOf { it.dividendYield ?: 0.0 } / holdings.size,
    // dailyChange is PER SHARE — multiply by the position before it means anything
    dayChange = holdings.sumOf { ctx.toBase((it.dailyChange ?: 0.0) * it.shareAmount, it.currency) },
    holdingsCount = holdings.size,
)

/** Row value ÷ all rows' value, both in base currency, so a mixed portfolio still adds to 100%. */
fun portfolioPercents(holdings: List<Holding>, ctx: CurrencyContext): Map<String, Double> {
    val inBase = holdings.map { it.ticker to ctx.toBase(it.totalValue, it.currency) }
    val total = inBase.sumOf { it.second }
    if (total == 0.0) return emptyMap()
    return inBase.associate { (ticker, value) -> ticker to value / total * 100 }
}

/** Holdings by value in base currency, largest first — the dashboard's default order. */
fun sortedByValue(holdings: List<Holding>, ctx: CurrencyContext): List<Holding> =
    holdings.sortedByDescending { ctx.toBase(it.totalValue, it.currency) }

/** Asset-type chips with counts, in order of first appearance. */
fun assetTypeCounts(holdings: List<Holding>): List<Pair<String, Int>> {
    val counts = linkedMapOf<String, Int>()
    holdings.forEach { counts[it.assetType ?: "CUSTOM"] = (counts[it.assetType ?: "CUSTOM"] ?: 0) + 1 }
    return counts.entries.map { it.key to it.value }
}

// ---------------------------------------------------------------- value chart

val CHART_RANGES = listOf("1M", "3M", "6M", "YTD", "1Y", "ALL")
private val RANGE_MONTHS = mapOf("1M" to 1, "3M" to 3, "6M" to 6, "1Y" to 12)

data class ValuePoint(val month: MonthKey, val value: Double)

/** Month-end values converted into the base currency; `valueByCurrency` first, flat sum as fallback. */
fun valueSeries(points: List<PerformancePointDto>, ctx: CurrencyContext): List<ValuePoint> =
    points.mapNotNull { point ->
        val month = MonthKey.parse(point.date) ?: return@mapNotNull null
        val value = point.valueByCurrency?.let { ctx.sumToBase(it) } ?: point.portfolioValue
        ValuePoint(month, value)
    }.sortedBy { it.month }

/**
 * First month shown for a range, clamped to the months the series has: a window reaching
 * before the first point starts there, and one starting after the last point falls back to
 * it so the chart is never empty. Port of the web's `rangeStartMonth`.
 */
fun rangeStart(range: String, months: List<MonthKey>, today: LocalDate = LocalDate.now()): MonthKey? {
    if (months.isEmpty()) return null
    val first = months.first()
    val last = months.last()
    if (range == "ALL") return first
    val now = MonthKey.of(today)
    val start = if (range == "YTD") MonthKey(now.year, 0) else now.shift(-(RANGE_MONTHS[range] ?: 12))
    return when {
        start <= first -> first
        start > last -> last
        else -> start
    }
}

fun visibleSeries(series: List<ValuePoint>, range: String, today: LocalDate = LocalDate.now()): List<ValuePoint> {
    val start = rangeStart(range, series.map { it.month }, today) ?: return emptyList()
    return series.filter { it.month >= start }
}

/** ±1pp of target counts as on target. */
enum class Drift { Over, On, Under }

fun driftOf(current: Double, target: Double): Drift = when {
    current - target >= 1 -> Drift.Over
    current - target <= -1 -> Drift.Under
    else -> Drift.On
}

/** "20" or "12.5" — targets print without a pointless ".0". */
fun formatTarget(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(java.util.Locale.US, "%.1f", rounded)
}
