package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.StatisticsDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * The Statistics screen's rows, a port of the web `StatisticsPage`'s `buildGroups`: the
 * same groups, labels and Yahoo scaling. A null value renders as an em dash: it means
 * the exchange or filing doesn't report it, never zero.
 */
enum class StatKind { Plain, Signed, Link }

sealed interface StatLine

data class StatValue(val label: String, val value: String?, val kind: StatKind = StatKind.Plain) : StatLine

data class StatSubhead(val text: String) : StatLine

data class StatGroup(val id: String, val title: String, val hint: String?, val lines: List<StatLine>) {
    val values: List<StatValue> get() = lines.filterIsInstance<StatValue>()
    val reported: Int get() = values.count { it.value != null }
}

/** Mobile order: the mockup stacks the web's three columns into one. */
val STAT_GROUP_ORDER = listOf("valuation", "profit", "income", "balance", "dividends", "trading", "shares", "profile")

val RECOMMENDATION_LABEL = mapOf(
    "strong_buy" to "Strong Buy",
    "buy" to "Buy",
    "hold" to "Hold",
    "underperform" to "Underperform",
    "sell" to "Sell",
)

/** "1.23T" / "4.56B" / "7.89M" / "1.00k" / "12.34" */
fun statAbbr(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e12 -> fixed(v / 1e12, 2) + "T"
        a >= 1e9 -> fixed(v / 1e9, 2) + "B"
        a >= 1e6 -> fixed(v / 1e6, 2) + "M"
        a >= 1e3 -> fixed(v / 1e3, 2) + "k"
        else -> fixed(v, 2)
    }
}

/** Yahoo fraction 0.3934 → "39.34%". */
fun statFraction(v: Double?, decimals: Int = 2): String? = v?.let { fixed(it * 100, decimals) + "%" }

/** Yahoo already-percent 0.93 → "0.93%". */
fun statPercent(v: Double?, decimals: Int = 2): String? = v?.let { fixed(it, decimals) + "%" }

fun statNumber(v: Double?, decimals: Int = 2): String? = v?.let { fixed(it, decimals) }

private fun statInt(v: Double?): String? = v?.let { formatNumber(it, 0) }

private val STAT_DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun statDate(v: String?): String? {
    if (v.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(v.take(10)).format(STAT_DAY) }.getOrDefault(v)
}

/** Prices in the quote currency: "$412.50", "£3.10", or "3,104.00p" for a pence listing. */
class StatMoney(currency: String?) {
    private val pence = currency == "GBp" || currency == "GBx"
    private val prefix = when {
        currency.isNullOrEmpty() || pence -> ""
        else -> mapOf("USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CHF" to "CHF ")[currency] ?: "$currency "
    }
    private val suffix = if (pence) "p" else ""

    /** per-share / price scale */
    fun price(v: Double?, decimals: Int = 2): String? = v?.let { "$prefix${fixed(it, decimals)}$suffix" }

    /** filing scale: billions, trillions */
    fun big(v: Double?): String? = v?.let { "$prefix${statAbbr(it)}$suffix" }
}

fun statisticsGroups(s: StatisticsDto): List<StatGroup> {
    val money = StatMoney(s.currency)
    return listOf(
        StatGroup(
            "valuation", "Valuation measures", null,
            listOf(
                StatValue("Market cap", money.big(s.marketCap)),
                StatValue("Enterprise value", money.big(s.enterpriseValue)),
                StatValue("Trailing P/E", statNumber(s.trailingPE)),
                StatValue("Forward P/E", statNumber(s.forwardPE)),
                StatValue("PEG ratio", statNumber(s.pegRatio)),
                StatValue("Price / sales", statNumber(s.priceToSales)),
                StatValue("Price / book", statNumber(s.priceToBook)),
                StatValue("EV / revenue", statNumber(s.enterpriseToRevenue)),
                StatValue("EV / EBITDA", statNumber(s.enterpriseToEbitda)),
            ),
        ),
        StatGroup(
            "profit", "Profitability & returns", "ttm",
            listOf(
                StatValue("Profit margin", statFraction(s.profitMargin)),
                StatValue("Operating margin", statFraction(s.operatingMargin)),
                StatValue("Gross margin", statFraction(s.grossMargin)),
                StatValue("EBITDA margin", statFraction(s.ebitdaMargin)),
                StatSubhead("Management effectiveness"),
                StatValue("Return on assets", statFraction(s.returnOnAssets)),
                StatValue("Return on equity", statFraction(s.returnOnEquity)),
            ),
        ),
        StatGroup(
            "profile", "Fiscal calendar & profile", null,
            listOf(
                StatValue("Fiscal year ends", statDate(s.fiscalYearEnd)),
                StatValue("Most recent quarter", statDate(s.mostRecentQuarter)),
                StatValue("Exchange", s.exchange),
                StatValue("Quote type", s.quoteType?.lowercase()?.replaceFirstChar { it.uppercase() }),
                StatValue("Full-time employees", statInt(s.fullTimeEmployees)),
                StatValue("Website", s.website, StatKind.Link),
            ),
        ),
        StatGroup(
            "income", "Income statement", "ttm",
            listOf(
                StatValue("Revenue", money.big(s.revenue)),
                StatValue("Revenue per share", money.price(s.revenuePerShare)),
                StatValue("Quarterly revenue growth (yoy)", statFraction(s.revenueGrowth)),
                StatValue("Gross profit", money.big(s.grossProfit)),
                StatValue("EBITDA", money.big(s.ebitda)),
                StatValue("Net income to common", money.big(s.netIncomeToCommon)),
                StatValue("Diluted EPS", money.price(s.dilutedEps)),
                StatValue("Forward EPS", money.price(s.forwardEps)),
                StatValue("Quarterly earnings growth (yoy)", statFraction(s.earningsQuarterlyGrowth)),
                StatValue("Earnings growth", statFraction(s.earningsGrowth)),
            ),
        ),
        StatGroup(
            "balance", "Balance sheet", "mrq",
            listOf(
                StatValue("Total cash", money.big(s.totalCash)),
                StatValue("Total cash per share", money.price(s.totalCashPerShare)),
                StatValue("Total debt", money.big(s.totalDebt)),
                StatValue("Total debt / equity", statPercent(s.debtToEquity)),
                StatValue("Current ratio", statNumber(s.currentRatio)),
                StatValue("Quick ratio", statNumber(s.quickRatio)),
                StatValue("Book value per share", money.price(s.bookValuePerShare)),
                StatSubhead("Cash flow · ttm"),
                StatValue("Operating cash flow", money.big(s.operatingCashflow)),
                StatValue("Levered free cash flow", money.big(s.freeCashflow)),
            ),
        ),
        StatGroup(
            "dividends", "Dividends & splits", null,
            listOf(
                StatValue("Forward dividend rate", money.price(s.dividendRate)),
                StatValue("Forward dividend yield", statPercent(s.dividendYield)),
                StatValue("Trailing annual rate", money.price(s.trailingAnnualDividendRate)),
                StatValue("Trailing annual yield", statFraction(s.trailingAnnualDividendYield)),
                StatValue("5-year average yield", statPercent(s.fiveYearAvgDividendYield)),
                StatValue("Payout ratio", statFraction(s.payoutRatio)),
                StatValue("Ex-dividend date", statDate(s.exDividendDate)),
                StatValue("Next dividend date", statDate(s.nextDividendDate)),
                StatValue("Last split factor", s.lastSplitFactor),
                StatValue("Last split date", statDate(s.lastSplitDate)),
            ),
        ),
        StatGroup(
            "trading", "Trading & 52-week", null,
            listOf(
                StatValue("Beta (5y monthly)", statNumber(s.beta)),
                StatValue("52-week high", money.price(s.fiftyTwoWeekHigh)),
                StatValue("52-week low", money.price(s.fiftyTwoWeekLow)),
                StatValue("52-week change", statFraction(s.fiftyTwoWeekChange), StatKind.Signed),
                StatValue("S&P 500 52-week change", statFraction(s.sp500FiftyTwoWeekChange), StatKind.Signed),
                StatValue("50-day average", money.price(s.fiftyDayAverage)),
                StatValue("200-day average", money.price(s.twoHundredDayAverage)),
                StatValue("Volume", statInt(s.volume)),
                StatValue("Average volume (3m)", statInt(s.averageVolume)),
                StatValue("Average volume (10d)", statInt(s.averageVolume10days)),
            ),
        ),
        StatGroup(
            "shares", "Share statistics", null,
            listOf(
                StatValue("Shares outstanding", s.sharesOutstanding?.let(::statAbbr)),
                StatValue("Implied shares outstanding", s.impliedSharesOutstanding?.let(::statAbbr)),
                StatValue("Float", s.floatShares?.let(::statAbbr)),
                StatValue("Shares short", statInt(s.sharesShort)),
                StatValue("Shares short (prior month)", statInt(s.sharesShortPriorMonth)),
                StatValue("Short ratio", statNumber(s.shortRatio)),
                StatValue("Short % of float", statFraction(s.shortPercentOfFloat)),
                StatValue("% held by insiders", statFraction(s.heldPercentInsiders)),
                StatValue("% held by institutions", statFraction(s.heldPercentInstitutions)),
            ),
        ),
    )
}

private fun fixed(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
