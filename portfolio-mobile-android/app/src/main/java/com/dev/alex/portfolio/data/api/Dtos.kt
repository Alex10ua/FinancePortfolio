package com.dev.alex.portfolio.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire shapes of the backend's `/api/v1` answers. Field names follow the Java models;
 * anything the app does not read is dropped by [ApiJson]'s ignoreUnknownKeys.
 *
 * Every money figure arrives in its **native** currency (see CLAUDE.md, "Currency
 * conversion is the frontend's job") — the domain layer converts, never this one.
 * BigDecimal travels as a JSON number, so Double is fine for display maths, the same
 * precision the web client works with.
 */
val ApiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

@Serializable
data class PortfolioDto(
    /** UUID + portfolio name — a string, and not URL-safe. Always pass it as a path segment. */
    val portfolioId: String,
    val portfolioName: String = "",
    val description: String? = null,
)

@Serializable
data class FirstTradeYearDto(val firstTradeYear: Int? = null)

/** `GET /{portfolioId}` — HoldingsCompleteData. */
@Serializable
data class HoldingDto(
    val ticker: String = "",
    val name: String? = null,
    val assetType: String? = null,
    val shareAmount: Double = 0.0,
    val exactShareAmount: Double? = null,
    val costPerShare: Double? = null,
    val costBasis: Double? = null,
    /** quote currency — see [quoteCurrency] */
    val currentShareValue: Double? = null,
    /** quote currency */
    val currentTotalValue: Double? = null,
    val totalReceivedDividend: Double? = null,
    /** annual dividend PER SHARE, quote currency */
    val dividend: Double? = null,
    val dividendYield: Double? = null,
    val dividendYieldOnCost: Double? = null,
    /** null where the quote and book currencies differ */
    val totalProfit: Double? = null,
    val totalProfitPercentage: Double? = null,
    /** PER-SHARE delta (price − priceYesterday), quote currency */
    val dailyChange: Double? = null,
    /** book currency: the one the position was bought in */
    val currency: String? = null,
    /** MarketData.currency; may be "GBp" pence, or USD for a coin bought in EUR */
    val quoteCurrency: String? = null,
    val sharesOutstanding: Long? = null,
)

@Serializable
data class TransactionDto(
    val transactionId: String = "",
    val ticker: String? = null,
    val name: String? = null,
    val transactionType: String? = null,
    val assetType: String? = null,
    val quantity: Double? = null,
    val price: Double? = null,
    /** DIVIDEND / DEPOSIT / WITHDRAWAL / TAX carry their money here */
    val amount: Double? = null,
    val totalAmount: Double? = null,
    val commission: Double? = null,
    /** LocalDate, 'YYYY-MM-DD' */
    val date: String? = null,
    val currency: String? = null,
)

@Serializable
data class CashHoldingDto(
    val currency: String = "",
    val amount: Double = 0.0,
)

/** One month-end point of `/portfolio-history`. */
@Serializable
data class PerformancePointDto(
    val date: String = "",
    /** unconverted sum of [valueByCurrency] — only exact for a single-currency portfolio */
    val portfolioValue: Double = 0.0,
    val valueByCurrency: Map<String, Double?>? = null,
)

@Serializable
data class DividendDataDto(
    val yearlyCombineDividendsProjection: Double = 0.0,
    val projectionByCurrency: Map<String, Double?>? = null,
    /** 'yyyy-MM' → amount, unconverted sum across currencies */
    val amountByMonth: Map<String, Double?> = emptyMap(),
    /** currency → ('yyyy-MM' → amount in that currency) */
    val amountByMonthByCurrency: Map<String, Map<String, Double?>>? = null,
    /** [{ticker: all-time amount}] in the ticker's MarketData currency */
    val tickerAmount: List<Map<String, Double?>> = emptyList(),
    /** ticker → its dividends' quote currency (MarketData.currency, may be "GBp") */
    val tickerCurrency: Map<String, String>? = null,
    val displayCurrency: String? = null,
)

/** One payment of `/dividends-calendar`; the response is keyed by Java month name ("JANUARY"). */
@Serializable
data class CalendarEntryDto(
    val ticker: String = "",
    /** per share, quoted in MarketData's currency — the DTO carries no currency of its own */
    val dividendAmount: Double? = null,
    val stockQuantity: Double? = null,
)

@Serializable
data class TickerTagsDto(
    val ticker: String = "",
    val tags: List<String> = emptyList(),
)

/** The two fields the range bar needs out of Yahoo's ~75 key statistics; quote currency. */
@Serializable
data class StatisticsDto(
    val fiftyTwoWeekHigh: Double? = null,
    val fiftyTwoWeekLow: Double? = null,
)

@Serializable
data class AllocationTargetDto(
    val ticker: String? = null,
    val percent: Double? = null,
)

@Serializable
data class PortfolioSettingsDto(
    val targets: List<AllocationTargetDto>? = null,
    val chartRange: String? = null,
    val baseCurrency: String? = null,
    val currencyDisplay: String? = null,
)

/**
 * `GET /users/me/settings`. Read only: the PUT replaces the whole document, so a client
 * holding a partial copy would wipe every portfolio it does not know about.
 */
@Serializable
data class UserSettingsDto(
    val theme: String? = null,
    val portfolioSettings: Map<String, PortfolioSettingsDto?>? = null,
)

/**
 * `POST /{portfolioId}/createTransaction` — the app's one write. Numbers travel as decimal
 * strings (exact, and what the web form sends); Jackson reads them into BigDecimal. No
 * field has a default except the nullable ones: [ApiJson] does not encode defaults.
 */
@Serializable
data class CreateTransactionRequest(
    val ticker: String,
    val transactionType: String,
    /** absent for DEPOSIT/WITHDRAWAL, as on the web */
    val assetType: String? = null,
    val quantity: String,
    val price: String,
    val commission: String,
    /** 'YYYY-MM-DD', the calendar day — never an instant (CLAUDE.md, LocalDate gotcha) */
    val date: String,
    val currency: String,
    /** DEPOSIT/WITHDRAWAL carry their money here */
    val amount: String? = null,
)

/** `{save, holdingSynced}` — false: stored, but the holding recalculates on the next trade. */
@Serializable
data class CreateTransactionResponse(val holdingSynced: Boolean = true)

/** `GET /{portfolioId}/watchlist/search?q=` — tickers marketData already knows. */
@Serializable
data class TickerSuggestionDto(
    val ticker: String,
    val name: String? = null,
    /** quote currency, so GBp for a London line */
    val currency: String? = null,
    val price: Double? = null,
)

/** `GET /{portfolioId}/custom-assets` — a CUSTOM BUY/SELL needs one of these to exist. */
@Serializable
data class CustomAssetDto(
    val ticker: String,
    val name: String = "",
    val currency: String? = null,
)
