package com.dev.alex.portfolio.ui.ownership

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.domain.Holding
import com.dev.alex.portfolio.domain.OwnershipRow
import com.dev.alex.portfolio.domain.OwnershipTier
import com.dev.alex.portfolio.domain.barPosition
import com.dev.alex.portfolio.domain.formatHeldShares
import com.dev.alex.portfolio.domain.formatOneIn
import com.dev.alex.portfolio.domain.formatShareCount
import com.dev.alex.portfolio.domain.formatStakePercent
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.ownershipView
import com.dev.alex.portfolio.domain.parseDecimal
import com.dev.alex.portfolio.domain.round6
import com.dev.alex.portfolio.domain.stepFor
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.TickerAvatar
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.MobileShell
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import com.dev.alex.portfolio.ui.theme.Semantic
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Ownership: how much of each company (or coin) the portfolio actually owns, ranked by
 * stake. A port of `MobileOwnership` (responsive-more.jsx) over the web `OwnershipPage`'s
 * rules ([com.dev.alex.portfolio.domain.ownershipView]). What-if mode re-projects every
 * stake from stepped share counts; it lives in this view-model only and is never saved.
 */
class OwnershipViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<List<Holding>>(app) {
    var whatIf by mutableStateOf(false)
        private set

    /** ticker → projected share count */
    var projection by mutableStateOf<Map<String, Double>>(emptyMap())
        private set

    override suspend fun load(tracker: StaleTracker): List<Holding> = coroutineScope {
        val holdings = async { tracker.take(app.repository.holdings(portfolioId)) }
        val rates = tracker.take(app.repository.fxRates()).toFxRates()
        holdings.await().map { it.normalize(rates) }
    }

    fun toggleWhatIf() {
        whatIf = !whatIf
    }

    fun setShares(ticker: String, shares: Double) {
        projection = projection + (ticker to maxOf(0.0, shares))
    }

    fun reset() {
        projection = emptyMap()
    }
}

@Composable
fun OwnershipScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("ownership:$portfolioId") { OwnershipViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    MobileShell(nav = nav, title = "Ownership", subtitle = "Your stake in each company · ranked") {
        PageBody(state, onRefresh = { vm.refresh(force = true) }) { holdings ->
            val view = ownershipView(holdings, if (vm.whatIf) vm.projection else null)
            if (view == null) {
                EmptyState(
                    FpIcons.Diamond,
                    "No ownership data yet",
                    "Add stock or crypto holdings with shares-outstanding data to see how much of each you own.",
                )
                return@PageBody
            }
            val held = view.held
            val dirty = view.changed.isNotEmpty()
            KpiCard(held.firstOrNull(), held.lastOrNull(), view.totalShares, view.baseTotal, held.size, dirty)
            VSpace(12.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(if (vm.whatIf) "What-if on" else "What-if", FpIcons.Sparkle, active = vm.whatIf, onClick = vm::toggleWhatIf)
                if (vm.whatIf && dirty) SmallButton("Reset", null, active = false, ghost = true, onClick = vm::reset)
                Box(Modifier.weight(1f))
                FpIcon(FpIcons.Info, size = 12.dp)
                Text("log · tick = 10×", style = FpType.mono(10.5.sp, FontWeight.Normal, Fp.colors.textMuted))
            }
            VSpace(12.dp)
            if (vm.whatIf) {
                WhatIfBanner(view.changed.size, view.baseTotal, view.totalShares)
                VSpace(12.dp)
            }
            view.rows.forEachIndexed { index, row ->
                OwnershipCard(
                    row = row,
                    position = index + 1,
                    minLog = view.minLog,
                    maxLog = view.maxLog,
                    whatIf = vm.whatIf,
                    onShares = { vm.setShares(row.ticker, it) },
                )
                VSpace(8.dp)
            }
        }
    }
}

@Composable
private fun KpiCard(top: OwnershipRow?, bottom: OwnershipRow?, total: Double, baseTotal: Double, companies: Int, dirty: Boolean) {
    FpCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(
                "Largest",
                top?.let { "1 in ${formatOneIn(it.oneIn)}" } ?: "—",
                top?.let { if (it.delta != 0.0) "${it.ticker} · was ${formatOneIn(it.baseOneIn)}" else it.ticker } ?: "all positions closed",
                Color(OwnershipTier.Meaningful.color),
                Modifier.weight(1f),
            )
            Kpi(
                "Smallest",
                bottom?.let { "1 in ${formatOneIn(it.oneIn)}" } ?: "—",
                bottom?.let { if (it.delta != 0.0) "${it.ticker} · was ${formatOneIn(it.baseOneIn)}" else it.ticker } ?: "—",
                Color(OwnershipTier.Trace.color),
                Modifier.weight(1f),
            )
            Kpi(
                "Shares",
                formatShareCount(total),
                if (dirty) "now ${formatShareCount(baseTotal)}" else "$companies companies",
                Brand.Primary,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, sub: String, accent: Color, modifier: Modifier) {
    val colors = Fp.colors
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            HSpace(5.dp)
            FpLabel(label)
        }
        VSpace(4.dp)
        Text(value, style = FpType.mono(15.sp, FontWeight.Bold, colors.text), maxLines = 1)
        Text(sub, color = colors.textMuted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallButton(text: String, icon: ImageVector?, active: Boolean, ghost: Boolean = false, onClick: () -> Unit) {
    val colors = Fp.colors
    val shape = RoundedCornerShape(6.dp)
    val fg = if (active) Color.White else colors.text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (active) Brand.Primary else if (ghost) Color.Transparent else colors.surface)
            .then(if (active || ghost) Modifier else Modifier.border(1.dp, colors.border, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (icon != null) {
            FpIcon(icon, size = 13.dp, tint = if (active) Color.White else Brand.Primary)
            HSpace(5.dp)
        }
        Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WhatIfBanner(changed: Int, baseTotal: Double, total: Double) {
    val colors = Fp.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (colors.isDark) Brand.Primary.copy(alpha = 0.14f) else Brand.Primary50)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        FpIcon(FpIcons.Sparkle, size = 13.dp, tint = Brand.Primary)
        HSpace(8.dp)
        if (changed == 0) {
            Text("Step any share count — nothing is saved.", color = colors.textMuted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
        } else {
            Text(
                "$changed position${if (changed > 1) "s" else ""} · ${formatHeldShares(baseTotal)} → ${formatHeldShares(total)} sh",
                style = FpType.mono(11.5.sp, FontWeight.Bold, colors.text),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DeltaChip(round6(total - baseTotal))
        }
    }
}

@Composable
private fun OwnershipCard(row: OwnershipRow, position: Int, minLog: Double, maxLog: Double, whatIf: Boolean, onShares: (Double) -> Unit) {
    val colors = Fp.colors
    val tierColor = Color(row.tier.color)
    val exited = row.shares <= 0
    val move = row.baseRank - position
    FpCard(
        padding = 12.dp,
        borderColor = if (row.delta != 0.0) Brand.Primary else null,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (exited) 0.6f else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                position.toString(),
                style = FpType.mono(11.5.sp, FontWeight.SemiBold, colors.textSubtle),
                textAlign = TextAlign.End,
                modifier = Modifier.width(18.dp),
            )
            HSpace(8.dp)
            TickerAvatar(row.ticker, size = 26.dp, color = tierColor, assetType = row.assetType)
            HSpace(9.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.ticker, style = FpType.mono(13.sp, FontWeight.Bold, colors.link), maxLines = 1)
                    if (whatIf && move != 0) {
                        HSpace(4.dp)
                        FpIcon(
                            if (move > 0) FpIcons.ArrowUp else FpIcons.ArrowDown,
                            size = 11.dp,
                            tint = if (move > 0) colors.gain else colors.loss,
                        )
                    }
                    HSpace(6.dp)
                    Text(row.tier.label.uppercase(), color = tierColor, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
                Text(row.name.orEmpty(), color = colors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (exited) "exited" else "1 in ${formatOneIn(row.oneIn)}",
                    style = FpType.mono(14.sp, FontWeight.Bold, colors.text),
                )
                Text(
                    if (exited) "—" else formatStakePercent(row.fraction),
                    style = FpType.mono(10.5.sp, FontWeight.Normal, colors.textMuted),
                )
            }
        }
        if (!exited) {
            VSpace(4.dp)
            OwnershipBar(
                fraction = row.fraction,
                ghostFraction = if (row.delta != 0.0) row.baseFraction else null,
                minLog = minLog,
                maxLog = maxLog,
                color = tierColor,
            )
        }
        VSpace(2.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (whatIf) {
                SharesStepper(row.shares, stepFor(row.baseShares), dirty = row.delta != 0.0, onChange = onShares)
                DeltaChip(row.delta)
            } else {
                Text(formatHeldShares(row.shares), style = FpType.mono(11.sp, FontWeight.Bold, colors.text))
                Text("sh", color = colors.textMuted, fontSize = 11.sp)
            }
            Box(Modifier.weight(1f))
            Text("of ${formatShareCount(row.outstanding)}", style = FpType.mono(11.sp, FontWeight.Normal, colors.textMuted))
        }
    }
}

/**
 * Bar length ∝ log10 of the stake, so a 1-in-100k and a 1-in-10B position both read.
 * Faint ticks mark each 10× step; in What-if a ghost dot and a dashed segment show where
 * today's stake sits.
 */
@Composable
private fun OwnershipBar(fraction: Double, ghostFraction: Double?, minLog: Double, maxLog: Double, color: Color) {
    val colors = Fp.colors
    val decades = maxOf(1, (maxLog - minLog).roundToInt())
    val fill = barPosition(fraction, minLog, maxLog).toFloat()
    val ghost = ghostFraction?.takeIf { it > 0 && abs(it - fraction) / fraction > 1e-9 }
        ?.let { barPosition(it, minLog, maxLog).toFloat() }
    val tick = if (colors.isDark) Color(0x40647488) else Color(0x99CBD5E1)
    val ring = if (colors.isDark) colors.surface else Color.White
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp),
    ) {
        val top = 9.dp.toPx()
        val barHeight = size.height - 2 * top
        val radius = CornerRadius(barHeight / 2, barHeight / 2)
        drawRoundRect(colors.track, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
        for (i in 1 until decades) {
            val x = size.width * i / decades
            drawLine(tick, Offset(x, top), Offset(x, top + barHeight), strokeWidth = 1.dp.toPx())
        }
        drawRoundRect(colors.border, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius, style = Stroke(1.dp.toPx()))
        val fillWidth = maxOf(size.width * fill, 8.dp.toPx())
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.33f), color), startX = 0f, endX = fillWidth),
            topLeft = Offset(0f, top),
            size = Size(fillWidth, barHeight),
            cornerRadius = radius,
        )
        val center = size.height / 2
        if (ghost != null) {
            val from = size.width * minOf(fill, ghost)
            val to = size.width * maxOf(fill, ghost)
            drawLine(
                color.copy(alpha = 0.75f),
                Offset(from, center),
                Offset(to, center),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
            val gx = size.width * ghost
            drawCircle(ring, radius = 5.dp.toPx(), center = Offset(gx, center))
            drawCircle(Color(0xFF94A3B8), radius = 4.dp.toPx(), center = Offset(gx, center), style = Stroke(2.dp.toPx()))
        }
        val x = size.width * fill
        drawCircle(color.copy(alpha = 0.4f), radius = 7.dp.toPx(), center = Offset(x, center))
        drawCircle(ring, radius = 6.dp.toPx(), center = Offset(x, center))
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, center))
    }
}

/** − [count] + ; typing a count works too. One tap ≈ 10% of the real position. */
@Composable
private fun SharesStepper(value: Double, step: Double, dirty: Boolean, onChange: (Double) -> Unit) {
    val colors = Fp.colors
    val shape = RoundedCornerShape(6.dp)
    // while typing, keep the raw text so "0." and "" survive
    var draft by remember { mutableStateOf<String?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .background(colors.surface)
            .border(if (dirty) 1.5.dp else 1.dp, if (dirty) Brand.Primary else colors.borderStrong, shape),
    ) {
        StepButton(FpIcons.Minus, "Fewer shares", enabled = value > 0) {
            draft = null
            onChange(maxOf(0.0, round6(value - step)))
        }
        BasicTextField(
            value = draft ?: formatHeldShares(value),
            onValueChange = { text ->
                val raw = text.filter { it.isDigit() || it == '.' || it == ',' }
                draft = raw
                onChange(parseDecimal(raw)?.toDouble() ?: 0.0)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = FpType.mono(12.5.sp, FontWeight.SemiBold, colors.text).copy(textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Brand.Primary),
            modifier = Modifier
                .width(64.dp)
                .onFocusChanged { if (!it.isFocused) draft = null },
        )
        StepButton(FpIcons.Plus, "More shares", enabled = true) {
            draft = null
            onChange(round6(value + step))
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(26.dp)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        FpIcon(icon, size = 12.dp, tint = Fp.colors.textMuted)
    }
}

/** "+40 sh" in green, "−12 sh" in red; nothing at zero. */
@Composable
private fun DeltaChip(delta: Double) {
    if (delta == 0.0) return
    val up = delta > 0
    val tone = if (up) Fp.colors.gain else Fp.colors.loss
    Text(
        "${if (up) "+" else "−"}${formatHeldShares(abs(delta))} sh",
        style = FpType.mono(10.5.sp, FontWeight.Bold, tone),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background((if (up) Semantic.Success else Semantic.Danger).copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
