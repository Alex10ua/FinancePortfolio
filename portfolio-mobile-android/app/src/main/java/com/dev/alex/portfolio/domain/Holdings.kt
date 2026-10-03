package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.HoldingDto
import com.dev.alex.portfolio.data.api.UserSettingsDto

/**
 * One holding row with every money field in its **book** currency ([currency]) — the
 * mobile twin of the web's `normalizeHolding`.
 */
data class Holding(
    val ticker: String,
    val name: String?,
    val assetType: String?,
    val shareAmount: Double,
    val costPerShare: Double?,
    val costBasis: Double?,
    val currentShareValue: Double?,
    val currentTotalValue: Double?,
    /** annual dividend per share */
    val dividend: Double?,
    val dividendYield: Double?,
    val dividendYieldOnCost: Double?,
    val totalProfit: Double?,
    val totalProfitPercentage: Double?,
    /** per-share delta since yesterday's close */
    val dailyChange: Double?,
    val currency: String?,
    /** provider's currency for [quoteShareValue] — may be "GBp" */
    val quoteCurrency: String?,
    /** the price as quoted, before conversion — what the 52-week range is measured in */
    val quoteShareValue: Double?,
) {
    /** prefer the backend's BigDecimal product; fall back for legacy rows */
    val totalValue: Double get() = currentTotalValue ?: ((currentShareValue ?: 0.0) * shareAmount)
    val costBasisValue: Double get() = costBasis ?: ((costPerShare ?: 0.0) * shareAmount)

    /** today's move as a percent of yesterday's close */
    val dayChangePercent: Double?
        get() {
            val change = dailyChange ?: return null
            val price = currentShareValue ?: return null
            val previous = price - change
            return if (previous > 0) change / previous * 100 else null
        }
}

/**
 * The API reports market figures (price, value, day change, dividend) in the quote
 * currency and cost in the book currency, leaving profit null where the two differ. This
 * converts the market figures into the book currency and derives profit from them.
 */
fun HoldingDto.normalize(rates: FxRates): Holding {
    val quote = quoteCurrency
    val book = currency
    val sameCurrency = quote.isNullOrEmpty() || book.isNullOrEmpty() || quote == book

    fun toBook(value: Double?): Double? = when {
        value == null -> null
        sameCurrency -> value
        // pence → pounds needs no rate; only a real currency change goes through FX
        normalizeCurrency(quote) == normalizeCurrency(book) -> toMajorUnits(value, quote)
        else -> convert(value, quote, book, rates)
    }

    val shareValue = toBook(currentShareValue)
    val totalValue = toBook(currentTotalValue)
    val annualDividend = toBook(dividend)
    val profit = if (sameCurrency) {
        totalProfit
    } else if (totalValue != null && costBasis != null) {
        totalValue - costBasis
    } else {
        null
    }
    val profitPercent = if (sameCurrency) {
        totalProfitPercentage
    } else if (profit != null && costBasis != null && costBasis != 0.0) {
        profit / costBasis * 100
    } else {
        null
    }
    val yieldOnCost = if (sameCurrency) {
        dividendYieldOnCost
    } else if (annualDividend != null && costPerShare != null && costPerShare != 0.0) {
        annualDividend / costPerShare * 100
    } else {
        null
    }

    return Holding(
        ticker = ticker,
        name = name,
        assetType = assetType,
        shareAmount = shareAmount,
        costPerShare = costPerShare,
        costBasis = costBasis,
        currentShareValue = shareValue,
        currentTotalValue = totalValue,
        dividend = annualDividend,
        dividendYield = dividendYield,
        dividendYieldOnCost = yieldOnCost,
        totalProfit = profit,
        totalProfitPercentage = profitPercent,
        dailyChange = toBook(dailyChange),
        currency = book,
        quoteCurrency = quote,
        quoteShareValue = currentShareValue,
    )
}

/** What the app reads from one portfolio's saved (web) settings. Read only. */
data class PortfolioPrefs(
    val baseCurrency: String? = null,
    val currencyDisplay: CurrencyDisplay = CurrencyDisplay.Symbol,
    val chartRange: String? = null,
    /** ticker → target % of portfolio */
    val targets: Map<String, Double> = emptyMap(),
)

fun UserSettingsDto?.prefsFor(portfolioId: String): PortfolioPrefs {
    val saved = this?.portfolioSettings?.get(portfolioId) ?: return PortfolioPrefs()
    return PortfolioPrefs(
        baseCurrency = saved.baseCurrency?.takeIf { it.isNotBlank() },
        currencyDisplay = CurrencyDisplay.parse(saved.currencyDisplay),
        chartRange = saved.chartRange,
        targets = saved.targets.orEmpty()
            .mapNotNull { target ->
                val ticker = target.ticker ?: return@mapNotNull null
                val percent = target.percent?.takeIf { it.isFinite() } ?: return@mapNotNull null
                ticker to percent
            }
            .toMap(),
    )
}

fun currencyContextFor(prefs: PortfolioPrefs, holdings: List<Holding>, rates: FxRates) = CurrencyContext(
    base = resolveBaseCurrency(prefs.baseCurrency, holdings.map { it.currency }),
    display = prefs.currencyDisplay,
    rates = rates,
)

/** "STOCK" → "Stock" for legends. */
fun assetLabel(type: String?): String =
    (type ?: "CUSTOM").lowercase().replaceFirstChar { it.uppercase() }

/** Per-asset-type colours, shared with the web's portfolio list. */
val ASSET_COLORS: Map<String, Long> = mapOf(
    "STOCK" to 0xFF3B82F6,
    "FUND" to 0xFF8B5CF6,
    "CRYPTO" to 0xFFF59E0B,
    "COIN" to 0xFF14B8A6,
    "FIGURINE" to 0xFFEF4444,
    "CUSTOM" to 0xFF94A3B8,
)
