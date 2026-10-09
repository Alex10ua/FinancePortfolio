package com.dev.alex.portfolio.data.logos

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dev.alex.portfolio.data.api.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Ticker logos from Parqet's public logo service, the same source the web's `StockLogo`
 * falls back to, fetched as square 128 px PNGs. Three layers:
 * - decoded bitmaps in memory;
 * - OkHttp's disk cache ([client] carries one; Parqet sends max-age=86400 on a logo and
 *   43200 on a 404, so a miss is not asked again all day);
 * - the network.
 * Offline, a stored copy of any age beats a letter. [client] has no cookie jar: the ticker
 * in the URL is all Parqet ever gets.
 */
class TickerLogoLoader(private val client: OkHttpClient) {
    private val bitmaps = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Parqet has no logo for these (404, or not an image) — for the life of the process. */
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** When a key last failed for want of a network, so a list offline isn't one request per row per frame. */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** One fetch per key at a time: a ticker listed twice on screen costs one request. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** A long list opens a few connections, not one per row. */
    private val gate = Semaphore(MAX_PARALLEL)

    /** A logo already in memory, so a recycled list row paints it on its first frame. */
    fun peek(ticker: String, assetType: String?): ImageBitmap? = bitmaps.get(keyOf(ticker, assetType))

    /** Null when there is no logo, or none could be fetched just now. */
    suspend fun load(ticker: String, assetType: String?): ImageBitmap? {
        val key = keyOf(ticker, assetType)
        bitmaps.get(key)?.let { return it }
        if (key in missing) return null
        val failed = failedAt[key]
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) return null
        return locks.getOrPut(key) { Mutex() }.withLock {
            bitmaps.get(key) ?: if (key in missing) null else gate.withPermit { fetch(key, urlOf(ticker, assetType)) }
        }
    }

    /** Sign-out: which logos were fetched says which tickers this phone's portfolios hold. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        bitmaps.evictAll()
        missing.clear()
        failedAt.clear()
        runCatching { client.cache?.evictAll() }
        Unit
    }

    private suspend fun fetch(key: String, url: HttpUrl): ImageBitmap? = withContext(Dispatchers.IO) {
        // network (or a fresh disk copy) first; offline, whatever the disk holds, however old
        val answer = request(url, null) ?: request(url, CacheControl.FORCE_CACHE)
        when (answer) {
            null -> {
                failedAt[key] = System.currentTimeMillis()
                null
            }
            Answer.None -> {
                missing.add(key)
                null
            }
            is Answer.Image -> {
                failedAt.remove(key)
                val bitmap = BitmapFactory.decodeByteArray(answer.bytes, 0, answer.bytes.size)
                if (bitmap == null) {
                    missing.add(key)
                    null
                } else {
                    bitmap.asImageBitmap().also { bitmaps.put(key, it) }
                }
            }
        }
    }

    private sealed interface Answer {
        class Image(val bytes: ByteArray) : Answer
        data object None : Answer
    }

    /** null = no answer (no network, or [cacheControl] asked the disk and it had nothing) */
    private suspend fun request(url: HttpUrl, cacheControl: CacheControl?): Answer? {
        val request = Request.Builder()
            .url(url)
            .apply { if (cacheControl != null) cacheControl(cacheControl) }
            .build()
        return try {
            client.newCall(request).await().use { response ->
                when {
                    response.isSuccessful -> response.body?.bytes()?.let { Answer.Image(it) } ?: Answer.None
                    response.code == 404 -> Answer.None
                    else -> null
                }
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun keyOf(ticker: String, assetType: String?): String =
        if (assetType == "CRYPTO") "crypto/$ticker" else "symbol/$ticker"

    /** Parqet files coins under /crypto: /symbol/BTC is a different (or no) logo. */
    private fun urlOf(ticker: String, assetType: String?): HttpUrl =
        BASE_URL.newBuilder()
            .addPathSegment(if (assetType == "CRYPTO") "crypto" else "symbol")
            .addPathSegment(ticker)
            .addQueryParameter("format", "png")
            .addQueryParameter("size", SIZE_PX.toString())
            .build()

    private companion object {
        val BASE_URL = "https://assets.parqet.com/logos".toHttpUrl()

        /** a 32 dp tile at 4× density */
        const val SIZE_PX = 128
        const val MAX_PARALLEL = 4
        const val RETRY_AFTER_MS = 60_000L
    }
}
