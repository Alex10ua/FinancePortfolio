package com.dev.alex.portfolio.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dev.alex.portfolio.ui.theme.Fp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The web client's ticker logos: `portfolio-app-frontend/public/images/{TICKER}_icon.png`,
 * packaged as APK assets by app/build.gradle.kts, so both clients show the same picture and
 * the phone needs no network for it. Same lookup as the web's `StockLogo`, plus a
 * `{TICKER}-USD` try for crypto (the files are named BTC-USD, ETH-USD; the web finds those
 * coins on Parqet instead). That remote fallback is not ported: it needs an SVG decoder and
 * sends every held ticker to a third party.
 */
private object TickerLogos {
    private const val SUFFIX = "_icon.png"

    /** Decode no smaller than this: a 32 dp avatar draws its logo at 24 dp, ~96 px at 4×. */
    private const val MIN_PX = 96

    @Volatile
    private var available: Set<String>? = null

    private val bitmaps = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private fun fileFor(names: Set<String>, ticker: String, assetType: String?): String? {
        val direct = ticker + SUFFIX
        if (direct in names) return direct
        val crypto = "$ticker-USD$SUFFIX"
        return crypto.takeIf { assetType == "CRYPTO" && it in names }
    }

    /** A logo already in memory, so a recycled list row paints it on its first frame. */
    fun peek(ticker: String, assetType: String?): ImageBitmap? {
        val names = available ?: return null
        return fileFor(names, ticker, assetType)?.let { bitmaps.get(it) }
    }

    /** Blocking; call off the main thread. Null when the ticker has no logo file. */
    fun load(assets: AssetManager, ticker: String, assetType: String?): ImageBitmap? {
        val names = available ?: synchronized(this) {
            available ?: assets.list("").orEmpty().filter { it.endsWith(SUFFIX) }.toSet().also { available = it }
        }
        val file = fileFor(names, ticker, assetType) ?: return null
        bitmaps.get(file)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(file).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MIN_PX && bounds.outHeight / (sample * 2) >= MIN_PX) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = assets.open(file).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            bitmap.asImageBitmap().also { bitmaps.put(file, it) }
        } catch (e: IOException) {
            null
        }
    }
}

private val LogoRing = Color(0xFF334155)
private val LogoRingBorder = Color(0xFF475569)
private val LogoRingDarkBorder = Color(0xFFE2E8F0)

/**
 * A ticker's logo in the web's `StockLogo` ring (slate in light mode, white in dark), or the
 * letter [Avatar] in [color] when there is no logo. Custom assets never look one up, as on
 * the web: their tickers are user-made and can collide with real symbols.
 */
@Composable
fun TickerAvatar(ticker: String, size: Dp, color: Color, assetType: String? = null) {
    if (assetType == "CUSTOM") {
        Avatar(ticker, size = size, color = color)
        return
    }
    val assets = LocalContext.current.assets
    val logo by produceState<ImageBitmap?>(TickerLogos.peek(ticker, assetType), ticker, assetType) {
        if (value == null) value = withContext(Dispatchers.IO) { TickerLogos.load(assets, ticker, assetType) }
    }
    val bitmap = logo
    if (bitmap == null) {
        Avatar(ticker, size = size, color = color)
        return
    }
    val dark = Fp.colors.isDark
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) Color.White else LogoRing)
            .border(1.dp, if (dark) LogoRingDarkBorder else LogoRingBorder, CircleShape),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = ticker,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size * 0.75f),
        )
    }
}
