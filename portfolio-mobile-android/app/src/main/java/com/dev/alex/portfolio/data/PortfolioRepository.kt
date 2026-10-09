package com.dev.alex.portfolio.data

import com.dev.alex.portfolio.data.api.AddToWatchlistRequest
import com.dev.alex.portfolio.data.api.ApiClient
import com.dev.alex.portfolio.data.api.ApiJson
import com.dev.alex.portfolio.data.api.CalendarEntryDto
import com.dev.alex.portfolio.data.api.CashHoldingDto
import com.dev.alex.portfolio.data.api.CreateTransactionRequest
import com.dev.alex.portfolio.data.api.CreateTransactionResponse
import com.dev.alex.portfolio.data.api.CustomAssetDto
import com.dev.alex.portfolio.data.api.DividendDataDto
import com.dev.alex.portfolio.data.api.FirstTradeYearDto
import com.dev.alex.portfolio.data.api.HoldingDto
import com.dev.alex.portfolio.data.api.NotFoundException
import com.dev.alex.portfolio.data.api.PerformancePointDto
import com.dev.alex.portfolio.data.api.PortfolioDto
import com.dev.alex.portfolio.data.api.SessionManager
import com.dev.alex.portfolio.data.api.StatisticsDto
import com.dev.alex.portfolio.data.api.TargetYieldRequest
import com.dev.alex.portfolio.data.api.TickerSuggestionDto
import com.dev.alex.portfolio.data.api.TickerTagsDto
import com.dev.alex.portfolio.data.api.TransactionDto
import com.dev.alex.portfolio.data.api.UserSettingsDto
import com.dev.alex.portfolio.data.api.WatchlistEntryDto
import com.dev.alex.portfolio.data.api.isOfflineError
import com.dev.alex.portfolio.data.cache.ResponseCache
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/** A decoded answer, and whether it came from the network or the offline cache. */
data class Fetched<T>(val value: T, val savedAt: Long, val fromCache: Boolean)

/**
 * Network first, cache second: every successful GET is written to [ResponseCache], and a
 * transport failure (or a 5xx from a proxy whose backend is down) falls back to the last
 * good copy. Session failures are never papered over with cached data.
 */
class PortfolioRepository(
    private val api: ApiClient,
    private val cache: ResponseCache,
    private val session: SessionManager,
) {
    suspend fun portfolios() = fetch(listOf("portfolios"), serializer = ListSerializer(PortfolioDto.serializer()))

    suspend fun holdings(portfolioId: String) =
        fetch(listOf(portfolioId), serializer = ListSerializer(HoldingDto.serializer()))

    suspend fun fxRates() = fetch(
        listOf("fx-rates"),
        serializer = MapSerializer(String.serializer(), Double.serializer().nullable),
    )

    suspend fun settings() = fetch(listOf("users", "me", "settings"), serializer = UserSettingsDto.serializer())

    suspend fun portfolioHistory(portfolioId: String) = fetch(
        listOf(portfolioId, "portfolio-history"),
        serializer = ListSerializer(PerformancePointDto.serializer()),
    )

    suspend fun realizedPnl(portfolioId: String) = fetch(
        listOf(portfolioId, "realizedPnL"),
        serializer = MapSerializer(String.serializer(), Double.serializer().nullable),
    )

    suspend fun cashHoldings(portfolioId: String) =
        fetch(listOf(portfolioId, "cash"), serializer = ListSerializer(CashHoldingDto.serializer()))

    /** Currency → DEPOSIT − WITHDRAWAL. The backend sums nothing else yet (TODO.list → A1). */
    suspend fun cashBalance(portfolioId: String) = fetch(
        listOf(portfolioId, "cashBalance"),
        serializer = MapSerializer(String.serializer(), Double.serializer().nullable),
    )

    /** Every transaction of every type — the per-year endpoint only returns BUY/SELL. */
    suspend fun transactions(portfolioId: String) =
        fetch(listOf(portfolioId, "transactions"), serializer = ListSerializer(TransactionDto.serializer()))

    suspend fun firstTradeYear(portfolioId: String) =
        fetch(listOf(portfolioId, "firstTradeYear"), serializer = FirstTradeYearDto.serializer())

    suspend fun dividends(portfolioId: String) =
        fetch(listOf(portfolioId, "dividends"), serializer = DividendDataDto.serializer())

    /** Month name ("JANUARY") → payments. No [year] = the rolling twelve-month projection. */
    suspend fun dividendCalendar(portfolioId: String, year: Int? = null) = fetch(
        listOf(portfolioId, "dividends-calendar"),
        query = if (year == null) emptyMap() else mapOf("year" to year.toString()),
        serializer = MapSerializer(String.serializer(), ListSerializer(CalendarEntryDto.serializer())),
    )

    suspend fun tags(portfolioId: String) = fetch(
        listOf("tags"),
        query = mapOf("portfolioId" to portfolioId),
        serializer = ListSerializer(TickerTagsDto.serializer()),
    )

    /** Yahoo key statistics; null when the backend has none for the ticker (404). */
    suspend fun statistics(ticker: String): Fetched<StatisticsDto?> =
        fetch(listOf("market-data", ticker, "statistics"), serializer = StatisticsDto.serializer().nullable, nullOn404 = true)

    suspend fun customAssets(portfolioId: String) =
        fetch(listOf(portfolioId, "custom-assets"), serializer = ListSerializer(CustomAssetDto.serializer()))

    /** Ticker search as the user types: live only, a cached answer to an old query helps no one. */
    suspend fun searchTickers(portfolioId: String, query: String): List<TickerSuggestionDto> =
        ApiJson.decodeFromString(
            ListSerializer(TickerSuggestionDto.serializer()),
            api.get(listOf(portfolioId, "watchlist", "search"), mapOf("q" to query)),
        )

    /**
     * The app's one write, sent at most once (see [ApiClient.post]). Returns the backend's
     * `holdingSynced`. Once the server has answered 2xx the row is stored, so a body this
     * build can't read still counts as saved.
     */
    suspend fun createTransaction(portfolioId: String, request: CreateTransactionRequest): Boolean {
        val body = api.post(
            listOf(portfolioId, "createTransaction"),
            ApiJson.encodeToString(CreateTransactionRequest.serializer(), request),
        )
        return runCatching { ApiJson.decodeFromString(CreateTransactionResponse.serializer(), body).holdingSynced }
            .getOrDefault(true)
    }

    suspend fun watchlist(portfolioId: String) =
        fetch(listOf(portfolioId, "watchlist"), serializer = ListSerializer(WatchlistEntryDto.serializer()))

    /**
     * Watch a ticker. An unknown symbol is fetched from the provider first, so this can
     * take seconds. A second add of the same ticker answers 400 "already on this
     * watchlist", so a repeat can't double it. Blank [targetYield] = 5-year 90th percentile.
     */
    suspend fun addToWatchlist(portfolioId: String, ticker: String, targetYield: String?) {
        api.post(
            listOf(portfolioId, "watchlist"),
            ApiJson.encodeToString(AddToWatchlistRequest.serializer(), AddToWatchlistRequest(ticker, targetYield)),
        )
    }

    /** Idempotent: the same target sent twice leaves the same row. */
    suspend fun setTargetYield(portfolioId: String, ticker: String, targetYield: String) {
        api.put(
            listOf(portfolioId, "watchlist", ticker),
            ApiJson.encodeToString(TargetYieldRequest.serializer(), TargetYieldRequest(targetYield)),
        )
    }

    suspend fun removeFromWatchlist(portfolioId: String, ticker: String) {
        api.delete(listOf(portfolioId, "watchlist", ticker))
    }

    suspend fun clearCache() = cache.clear()

    private suspend fun <T> fetch(
        segments: List<String>,
        query: Map<String, String> = emptyMap(),
        serializer: KSerializer<T>,
        nullOn404: Boolean = false,
    ): Fetched<T> {
        val key = cacheKey(segments, query)
        val body = try {
            api.get(segments, query)
        } catch (e: NotFoundException) {
            if (!nullOn404) throw e
            "null"
        } catch (e: Exception) {
            if (!isOfflineError(e)) throw e
            val entry = cache.read(key) ?: throw e
            // a cached "null" (statistics) is a valid answer, so test the decode, not its value
            val cached = runCatching { ApiJson.decodeFromString(serializer, entry.body) }
            if (cached.isFailure) throw e
            return Fetched(cached.getOrThrow(), entry.savedAt, fromCache = true)
        }
        // decode before caching, so a body this build can't read never replaces a good copy
        val value = ApiJson.decodeFromString(serializer, body)
        cache.write(key, body)
        return Fetched(value, System.currentTimeMillis(), fromCache = false)
    }

    /** Per server and user, so two accounts on one phone never read each other's copy. */
    private fun cacheKey(segments: List<String>, query: Map<String, String>): String {
        val server = session.baseUrl?.toString().orEmpty()
        val user = session.username.orEmpty()
        val q = query.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return "$server|$user|${segments.joinToString("/")}?$q"
    }
}
