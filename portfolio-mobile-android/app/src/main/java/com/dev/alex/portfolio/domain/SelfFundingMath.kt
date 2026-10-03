package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.CalendarEntryDto
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Self-funding — how many shares a position needs before one period's dividend pays for
 * one more share at today's price. Port of the web's `selfFundingMath.ts`.
 *
 *   needed    = ceil(price / (annual DPS / periodDivisor))
 *   gapShares = max(0, needed − held)
 *
 * Price and dividend come from the same MarketData document, so the ratio is exact in any
 * currency; only the formatted money uses the holding's currency.
 */
enum class Period(val divisor: Int, val noun: String) {
    Yearly(1, "year"),
    Quarterly(4, "quarter"),
    Monthly(12, "month"),
}

enum class Tier(val label: String, val color: Long) {
    SelfFunding("Self-funding", 0xFF10B981),
    Close("Close", 0xFF14B8A6),
    Far("Far", 0xFF3B82F6),
    VeryFar("Very far", 0xFF8B5CF6),
}

data class SfRow(
    val ticker: String,
    val name: String?,
    val currency: String?,
    val price: Double,
    val dps: Double,
    val held: Double,
    /** distinct paying months in the dividend calendar; null = cadence unknown */
    val paymentsPerYear: Int?,
)

data class SfCalc(
    val perPeriod: Double,
    /** one actual payment per share; null while the cadence is unknown */
    val perPayment: Double?,
    val needed: Long,
    val gapShares: Double,
    val gapCost: Double,
    /** shares one period's dividend buys at today's price — uncapped: 5.2 = 520% */
    val sharesPerPeriod: Double,
    /** [sharesPerPeriod] clamped to 1, the bar fill */
    val progress: Double,
    /** dps / price, as a fraction */
    val yield: Double,
    val tier: Tier,
    /** years of dividend-only reinvestment to close the gap; null when nothing is held */
    val years: Double?,
) {
    val reached: Boolean get() = gapShares <= 0.0
}

fun calcSelfFunding(row: SfRow, period: Period): SfCalc {
    val perPeriod = row.dps / period.divisor
    val needed = max(1L, ceil(row.price / perPeriod).toLong())
    val gapShares = max(0.0, needed - row.held)
    val ratio = gapShares / needed
    val tier = when {
        gapShares == 0.0 -> Tier.SelfFunding
        ratio < 0.25 -> Tier.Close
        ratio < 0.6 -> Tier.Far
        else -> Tier.VeryFar
    }
    val dividendYield = row.dps / row.price
    // reinvest only: the position compounds at its own yield, price and dividend flat
    val years = when {
        gapShares == 0.0 -> 0.0
        row.held > 0 && dividendYield > 0 -> ln(needed / row.held) / ln(1 + dividendYield)
        else -> null
    }
    // measured against the exact price, so a position past the threshold reads 520%, not 100%
    val sharesPerPeriod = row.held * perPeriod / row.price
    return SfCalc(
        perPeriod = perPeriod,
        perPayment = row.paymentsPerYear?.takeIf { it > 0 }?.let { row.dps / it },
        needed = needed,
        gapShares = gapShares,
        gapCost = gapShares * row.price,
        sharesPerPeriod = sharesPerPeriod,
        progress = min(1.0, sharesPerPeriod),
        yield = dividendYield,
        tier = tier,
        years = years,
    )
}

/** 100% = the dividend buys exactly one share. A row still short is held at 99%. */
fun coverageLabel(calc: SfCalc): String {
    val pct = calc.sharesPerPeriod * 100
    return if (!calc.reached) "${min(99L, Math.round(pct))}%" else "${formatNumber(Math.round(pct).toDouble(), 0)}%"
}

fun cadenceLabel(paymentsPerYear: Int?): String = when (paymentsPerYear) {
    null -> "Unknown"
    1 -> "Annual"
    2 -> "Semi-annual"
    4 -> "Quarterly"
    12 -> "Monthly"
    else -> "$paymentsPerYear× / year"
}

/** Distinct paying months per ticker in the rolling calendar — there is no frequency field. */
fun paymentsPerYearByTicker(calendar: Map<String, List<CalendarEntryDto>>?): Map<String, Int> {
    val months = mutableMapOf<String, MutableSet<String>>()
    calendar.orEmpty().forEach { (month, entries) ->
        entries.forEach { entry ->
            if (entry.ticker.isNotBlank()) months.getOrPut(entry.ticker.uppercase()) { mutableSetOf() }.add(month)
        }
    }
    return months.mapValues { it.value.size }
}

/** Screenable rows (a price and a dividend to divide) + how many holdings dropped out. */
fun buildSelfFundingRows(holdings: List<Holding>, payments: Map<String, Int>): Pair<List<SfRow>, Int> {
    val held = holdings.filter { it.shareAmount > 0 }
    val rows = held.mapNotNull { h ->
        val price = h.currentShareValue ?: 0.0
        val dps = h.dividend ?: 0.0
        if (price <= 0 || dps <= 0) return@mapNotNull null
        SfRow(
            ticker = h.ticker,
            name = h.name,
            currency = h.currency,
            price = price,
            dps = dps,
            held = h.shareAmount,
            paymentsPerYear = payments[h.ticker.uppercase()],
        )
    }
    return rows to (held.size - rows.size)
}

/** Closest first: highest coverage, then the smaller gap. */
fun rankSelfFunding(rows: List<SfRow>, period: Period): List<Pair<SfRow, SfCalc>> =
    rows.map { it to calcSelfFunding(it, period) }
        .sortedWith(compareByDescending<Pair<SfRow, SfCalc>> { it.second.progress }.thenBy { it.second.gapShares })
