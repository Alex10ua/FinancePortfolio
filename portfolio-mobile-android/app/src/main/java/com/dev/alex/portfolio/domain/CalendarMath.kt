package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.CalendarEntryDto
import java.time.LocalDate

data class Payment(
    val ticker: String,
    /** per share, in the major unit of [currency] */
    val perShare: Double,
    val shares: Double,
    /** native: perShare × shares */
    val amount: Double,
    val baseAmount: Double,
    val currency: String,
)

data class MonthBucket(
    val index: Int,
    val label: String,
    val short: String,
    val payments: List<Payment>,
    /** base currency */
    val total: Double,
    /** a closed year, or a month before this one */
    val paid: Boolean,
    val current: Boolean,
)

data class CalendarYear(
    val year: Int,
    val months: List<MonthBucket>,
) {
    val total: Double get() = months.sumOf { it.total }
    val paymentCount: Int get() = months.sumOf { it.payments.size }
    val payerCount: Int get() = months.flatMap { m -> m.payments.map { it.ticker } }.toSet().size
    val best: MonthBucket? get() = months.filter { it.payments.isNotEmpty() }.maxByOrNull { it.total }
    val average: Double
        get() {
            val paying = months.count { it.payments.isNotEmpty() }
            return if (paying > 0) total / paying else 0.0
        }
}

/**
 * Port of the web's DividendCalendarPage bucketing. The calendar DTO carries no currency:
 * `DividendData.tickerCurrency` (MarketData's own) is authoritative, the holding's book
 * currency plus the pence heuristic is the fallback.
 */
fun buildCalendarYear(
    calendar: Map<String, List<CalendarEntryDto>>?,
    year: Int,
    holdings: List<Holding>,
    tickerCurrency: Map<String, String>?,
    ctx: CurrencyContext,
    today: LocalDate = LocalDate.now(),
): CalendarYear {
    val holdingCurrency = holdings.associate { it.ticker.uppercase() to (it.currency ?: "USD") }
    val marketCurrency = tickerCurrency.orEmpty().entries.associate { it.key.uppercase() to it.value }
    val currentYear = today.year
    val currentMonth = today.monthValue - 1

    fun toPayment(entry: CalendarEntryDto): Payment {
        val ticker = entry.ticker
        val quoted = marketCurrency[ticker.uppercase()]
        val fallback = holdingCurrency[ticker.uppercase()] ?: "USD"
        val raw = entry.dividendAmount ?: 0.0
        // dividendAmount is quoted in MarketData's currency: a London payer reports pence
        val perShare = when {
            quoted != null -> toMajorUnits(raw, quoted)
            looksLikePenceQuote(ticker, fallback) -> raw / 100
            else -> raw
        }
        val currency = normalizeCurrency(quoted ?: fallback)
        val shares = entry.stockQuantity ?: 0.0
        val amount = perShare * shares
        return Payment(ticker, perShare, shares, amount, ctx.toBase(amount, currency), currency)
    }

    val months = MONTH_NAMES.mapIndexed { index, label ->
        val payments = calendar?.get(label.uppercase()).orEmpty().map(::toPayment)
        MonthBucket(
            index = index,
            label = label,
            short = label.take(3),
            payments = payments,
            total = payments.sumOf { it.baseAmount },
            paid = year < currentYear || (year == currentYear && index < currentMonth),
            current = year == currentYear && index == currentMonth,
        )
    }
    return CalendarYear(year, months)
}

/** "1", "12.5", "0.3333" — ex-date maths makes share counts fractional more often than not. */
fun formatCalendarShares(shares: Double): String {
    val rounded = Math.round(shares * 10000) / 10000.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.4f", rounded).trimEnd('0')
    }
}
