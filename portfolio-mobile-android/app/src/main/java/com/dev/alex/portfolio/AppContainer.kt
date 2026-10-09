package com.dev.alex.portfolio

import android.content.Context
import com.dev.alex.portfolio.data.PortfolioRepository
import com.dev.alex.portfolio.data.api.ApiClient
import com.dev.alex.portfolio.data.api.SessionCookieJar
import com.dev.alex.portfolio.data.api.SessionManager
import com.dev.alex.portfolio.data.auth.BiometricGate
import com.dev.alex.portfolio.data.auth.CredentialStore
import com.dev.alex.portfolio.data.cache.ResponseCache
import com.dev.alex.portfolio.data.logos.TickerLogoLoader
import com.dev.alex.portfolio.data.settings.AppSettings
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Hand-wired dependencies — one of each for the life of the process. */
class AppContainer(context: Context) {
    val settings = AppSettings(context)
    val credentials = CredentialStore(context)
    val biometrics = BiometricGate()
    val session = SessionManager()
    val cookies = SessionCookieJar()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(15, TimeUnit.SECONDS)
        // the backend can answer only after a synchronous market-data fetch (new tickers)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val api = ApiClient(http, session, cookies)
    val repository = PortfolioRepository(api, ResponseCache(File(context.filesDir, "api-cache")), session)

    /**
     * Ticker logos from the internet. A client of its own: no session cookies, and a disk
     * cache (in cacheDir, so Android may reclaim it) that honours Parqet's max-age.
     */
    val logos = TickerLogoLoader(
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "ticker-logos"), 20L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build(),
    )

    /** A screen hit a session that could not be renewed — the app goes back to the lock screen. */
    val sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Bumped after every write (or a write whose outcome is unknown). Each screen's
     * [com.dev.alex.portfolio.ui.common.LoadViewModel] reloads when it sees a new value:
     * a transaction moves holdings, cash, P&L, dividends and the charts all at once.
     */
    val dataVersion = MutableStateFlow(0L)
}
