package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.DividendDataDto
import com.dev.alex.portfolio.data.api.TransactionDto

/** % change of a period on the same period a year earlier (the web's IncomeTooltip). */
data class Change(
    val pct: Double,
    /** what it is compared against: "2025", "Q3 2025", "Apr 2025" */
    val vs: String,
    /** finished months it was measured on while the period is still running ("Jan–Aug") */
    val months: String?,
)

data class IncomeBar(
    /** axis label */
    val label: String,
    /** "Q3 2026", "Apr 2026", "2026" */
    val title: String,
    val amount: Double,
    /** YTD / QTD / MTD while the period is still running */
    val tag: String?,
    val change: Change?,
)

/** Projection for the next twelve months, in base currency. */
fun yearlyProjection(data: DividendDataDto, ctx: CurrencyContext): Double =
    data.projectionByCurrency?.let { ctx.sumToBase(it) }
        ?: ctx.toBase(data.yearlyCombineDividendsProjection, data.displayCurrency)

/** Received income per month, converted per currency and merged. */
fun incomeByMonth(data: DividendDataDto, ctx: CurrencyContext): Map<MonthKey, Double> {
    val out = mutableMapOf<MonthKey, Double>()
    val byCurrency = data.amountByMonthByCurrency
    if (byCurrency != null) {
        for ((currency, months) in byCurrency) {
            for ((key, amount) in months) {
                val month = MonthKey.parse(key) ?: continue
                out[month] = (out[month] ?: 0.0) + ctx.toBase(amount ?: 0.0, currency)
            }
        }
    } else {
        for ((key, amount) in data.amountByMonth) {
            val month = MonthKey.parse(key) ?: continue
            out[month] = (out[month] ?: 0.0) + ctx.toBase(amount ?: 0.0, data.displayCurrency)
        }
    }
    return out
}

/**
 * The three income views behind the Dividends screen's Year / Quarter / Month switch.
 * A running period is compared on its finished months only — a part period against a
 * whole one reads as a cut.
 */
class DividendIncome(private val amounts: Map<MonthKey, Double>, private val now: MonthKey) {

    private fun paidIn(months: List<MonthKey>) = months.sumOf { amounts[it] ?: 0.0 }

    private fun changeOn(months: List<MonthKey>, vs: String): Change? {
        val done = months.filter { it < now }
        val base = paidIn(done.map { it.shift(-12) })
        if (done.isEmpty() || base <= 0) return null
        val first = done.first().label
        val last = done.last().label
        return Change(
            pct = (paidIn(done) / base - 1) * 100,
            vs = vs,
            months = when {
                done.size == months.size -> null
                first == last -> first
                else -> "$first–$last"
            },
        )
    }

    fun years(): List<Int> = amounts.keys.map { it.year }.distinct().sorted()

    fun byYear(): List<IncomeBar> = years().map { year ->
        val months = MonthKey.range(year, 0, 12)
        IncomeBar(
            label = year.toString(),
            title = year.toString(),
            amount = paidIn(months),
            tag = if (now in months) "YTD" else null,
            change = changeOn(months, (year - 1).toString()),
        )
    }

    fun byQuarter(year: Int): List<IncomeBar> = (1..4).map { quarter ->
        val months = MonthKey.range(year, (quarter - 1) * 3, 3)
        IncomeBar(
            label = "Q$quarter",
            title = "Q$quarter $year",
            amount = paidIn(months),
            tag = if (now in months) "QTD" else null,
            change = changeOn(months, "Q$quarter ${year - 1}"),
        )
    }

    fun byMonth(year: Int): List<IncomeBar> = (0..11).map { index ->
        val month = MonthKey(year, index)
        IncomeBar(
            label = month.label,
            title = month.title,
            amount = amounts[month] ?: 0.0,
            tag = if (month == now) "MTD" else null,
            change = changeOn(listOf(month), month.shift(-12).title),
        )
    }
}

data class TopPayer(val ticker: String, val amount: Double)

/** All-time dividends per ticker still held, in base currency, largest first. */
fun topPayers(data: DividendDataDto, ctx: CurrencyContext, heldTickers: Set<String>, limit: Int = 7): List<TopPayer> {
    val currencyOf = data.tickerCurrency.orEmpty()
    val merged = linkedMapOf<String, Double>()
    for (entry in data.tickerAmount) {
        for ((ticker, amount) in entry) {
            merged[ticker] = ctx.toBase(amount ?: 0.0, currencyOf[ticker] ?: data.displayCurrency)
        }
    }
    return merged.entries
        .filter { it.value > 0 && it.key in heldTickers }
        .sortedByDescending { it.value }
        .take(limit)
        .map { TopPayer(it.key, it.value) }
}

data class BatchMove(val ticker: String, val sell: Boolean, val quantity: Double)

data class LastBatch(
    /** 'YYYY-MM-DD' */
    val date: String,
    val moves: List<BatchMove>,
    /** what this batch moved the yearly projection by, in base currency */
    val yearlyDelta: Double,
)

/**
 * The newest date's BUY/SELL transactions of [year] — one imported statement or one
 * session's orders — and how much they shifted the projection: Σ ±quantity × annual DPS.
 *
 * Unlike the web page (which converts the normalized DPS out of the *quote* currency and
 * so lands 100× low on a pence-quoted line), the DPS here is already in the holding's
 * book currency and is converted from there.
 */
fun lastBatch(transactions: List<TransactionDto>, holdings: List<Holding>, ctx: CurrencyContext, year: Int): LastBatch? {
    val trades = transactions.filter {
        (it.transactionType == "BUY" || it.transactionType == "SELL") && yearOf(it.date) == year
    }
    val lastDate = trades.maxOfOrNull { dayOf(it.date) } ?: return null
    val batch = trades.filter { dayOf(it.date) == lastDate }
    val byTicker = holdings.associateBy { it.ticker }
    val delta = batch.sumOf { tx ->
        val sign = if (tx.transactionType == "SELL") -1.0 else 1.0
        val holding = byTicker[tx.ticker]
        val dps = holding?.dividend ?: 0.0
        // a sold-out ticker has no holding left — the projection it carried is simply gone
        if (dps == 0.0) 0.0 else ctx.toBase(sign * (tx.quantity ?: 0.0) * dps, holding?.currency)
    }
    return LastBatch(
        date = lastDate,
        moves = batch.map { BatchMove(it.ticker ?: "—", it.transactionType == "SELL", it.quantity ?: 0.0) },
        yearlyDelta = delta,
    )
}
