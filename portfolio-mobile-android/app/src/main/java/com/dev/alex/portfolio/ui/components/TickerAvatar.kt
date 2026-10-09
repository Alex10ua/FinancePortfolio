package com.dev.alex.portfolio.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dev.alex.portfolio.PortfolioApp
import com.dev.alex.portfolio.ui.theme.Fp

/** Ticker tiles are square (slightly softened), like the logos themselves. */
val TickerShape = RoundedCornerShape(percent = 15)

/**
 * A ticker's logo from the internet ([com.dev.alex.portfolio.data.logos.TickerLogoLoader],
 * Parqet's logo service) as a square tile, or the letter [Avatar] in [color], same shape,
 * while it loads and when there is none. Square, unlike the web's round `StockLogo`: the
 * logos are full-bleed squares (KO's red), and a circle cut their corners off. White under
 * the image, for a logo with transparent edges. Custom assets never look one up, as on
 * the web: their tickers are user-made and can collide with real symbols.
 */
@Composable
fun TickerAvatar(ticker: String, size: Dp, color: Color, assetType: String? = null) {
    if (assetType == "CUSTOM") {
        Avatar(ticker, size = size, color = color, shape = TickerShape)
        return
    }
    val logos = (LocalContext.current.applicationContext as PortfolioApp).container.logos
    val logo by produceState<ImageBitmap?>(logos.peek(ticker, assetType), ticker, assetType) {
        if (value == null) value = logos.load(ticker, assetType)
    }
    val bitmap = logo
    if (bitmap == null) {
        Avatar(ticker, size = size, color = color, shape = TickerShape)
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(TickerShape)
            .background(Color.White)
            .border(1.dp, Fp.colors.border, TickerShape),
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = ticker,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
