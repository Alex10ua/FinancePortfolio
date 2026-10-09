package com.dev.alex.portfolio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.alex.portfolio.domain.formatSignedPercent
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import com.dev.alex.portfolio.ui.theme.Semantic

val CardShape = RoundedCornerShape(8.dp)

/** The mockups' Card: surface, 1px border, 8dp radius, a whisper of shadow in light mode. */
@Composable
fun FpCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    background: Color? = null,
    brush: Brush? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Fp.colors
    val fill = if (brush != null) {
        Modifier.background(brush, CardShape)
    } else {
        Modifier.background(background ?: colors.surface, CardShape)
    }
    Column(
        modifier = modifier
            .then(if (colors.isDark) Modifier else Modifier.shadow(1.dp, CardShape, clip = false))
            .clip(CardShape)
            .then(fill)
            .border(1.dp, borderColor ?: colors.border, CardShape)
            .padding(padding),
        content = content,
    )
}

/** `mobLabel`: 10sp, semibold, upper case, tracked out. */
@Composable
fun FpLabel(text: String, modifier: Modifier = Modifier, color: Color = Fp.colors.textMuted, size: TextUnit = 10.sp) {
    Text(
        text = text.uppercase(),
        style = FpType.label(color, size),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun FpIcon(icon: ImageVector, size: Dp = 16.dp, tint: Color = Fp.colors.textMuted, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/**
 * Letter avatar: the mockups' portfolio and user icon, round by default. Tickers pass
 * [TickerShape] so a missing logo matches the square logo tiles next to it.
 */
@Composable
fun Avatar(letter: String, size: Dp = 28.dp, color: Color = Brand.Primary, shape: Shape = CircleShape) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class BadgeTone(val fg: Long, val fgDark: Long, val bg: Long, val bgDark: Long)

private val BADGE_TONES = mapOf(
    "BUY" to BadgeTone(0xFF047857, 0xFF34D399, 0xFFD1FAE5, 0x2E10B981),
    "SELL" to BadgeTone(0xFFB91C1C, 0xFFF87171, 0xFFFEE2E2, 0x2EEF4444),
    "DIVIDEND" to BadgeTone(0xFF1D4ED8, 0xFF60A5FA, 0xFFDBEAFE, 0x2E3B82F6),
    "TAX" to BadgeTone(0xFFB45309, 0xFFFBBF24, 0xFFFEF3C7, 0x2EF59E0B),
    "DEPOSIT" to BadgeTone(0xFF0F766E, 0xFF2DD4BF, 0xFFCCFBF1, 0x2E14B8A6),
    "WITHDRAWAL" to BadgeTone(0xFF6D28D9, 0xFFA78BFA, 0xFFEDE9FE, 0x2E8B5CF6),
    "STOCK" to BadgeTone(0xFF1E40AF, 0xFF60A5FA, 0xFFDBEAFE, 0x2E3B82F6),
    "FUND" to BadgeTone(0xFF6D28D9, 0xFFA78BFA, 0xFFEDE9FE, 0x2E8B5CF6),
    "CRYPTO" to BadgeTone(0xFFB45309, 0xFFFBBF24, 0xFFFEF3C7, 0x2EF59E0B),
    "COIN" to BadgeTone(0xFF0F766E, 0xFF2DD4BF, 0xFFCCFBF1, 0x2E14B8A6),
    "FIGURINE" to BadgeTone(0xFFB91C1C, 0xFFF87171, 0xFFFEE2E2, 0x2EEF4444),
    "CUSTOM" to BadgeTone(0xFF475569, 0xFFCBD5E1, 0xFFF1F5F9, 0x2E94A3B8),
)

/** Transaction-type / asset-type pill. */
@Composable
fun TypeBadge(variant: String) {
    val tone = BADGE_TONES[variant] ?: BADGE_TONES.getValue("CUSTOM")
    val dark = Fp.colors.isDark
    Text(
        text = variant,
        color = Color(if (dark) tone.fgDark else tone.fg),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(if (dark) tone.bgDark else tone.bg))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Small uppercase status pill (tiers, "This month", "Scheduled"). */
@Composable
fun TagPill(text: String, color: Color, dashed: Boolean = false) {
    val colors = Fp.colors
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (dashed) {
                    Modifier.border(1.dp, colors.border, RoundedCornerShape(4.dp))
                } else {
                    Modifier.background(color.copy(alpha = if (colors.isDark) 0.14f else 0.10f))
                },
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Filter pill with an optional count. */
@Composable
fun FpChip(text: String, active: Boolean, count: Int? = null, onClick: () -> Unit) {
    val colors = Fp.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Brand.Primary else colors.surface)
            .border(1.dp, if (active) Brand.Primary else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (active) Color.White else colors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                color = if (active) Color.White else colors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (active) Color.White.copy(alpha = 0.22f)
                        else if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF1F5F9),
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** Segmented control; scrolls sideways when the options outgrow the row. */
@Composable
fun Segmented(
    options: List<String>,
    active: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val colors = Fp.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()))
            .clip(shape)
            .background(colors.surfaceMuted)
            .border(1.dp, colors.border, shape)
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val selected = option == active
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .then(if (selected && !colors.isDark) Modifier.shadow(1.dp, RoundedCornerShape(4.dp)) else Modifier)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) colors.surface else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = option,
                    color = if (selected) colors.text else colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** `MobTrend`: arrow + signed percent, green up / red down. */
@Composable
fun Trend(percent: Double?, size: TextUnit = 12.sp, suffix: String? = null) {
    if (percent == null || !percent.isFinite()) return
    val down = percent < 0
    val color = if (down) Semantic.Danger else Semantic.Success
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        FpIcon(if (down) FpIcons.ArrowDown else FpIcons.ArrowUp, size = (size.value - 1).dp, tint = color)
        Text(formatSignedPercent(percent), style = FpType.number(size, FontWeight.SemiBold, color))
        if (suffix != null) {
            Text(" $suffix", fontSize = size, color = Fp.colors.textMuted)
        }
    }
}

/** Segmented allocation bar; zero-weight segments are skipped. */
@Composable
fun AllocBar(segments: List<Pair<Double, Color>>, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    val visible = segments.filter { it.first > 0 }
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape),
    ) {
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().background(Fp.colors.border))
        }
        visible.forEach { (share, color) ->
            Box(
                Modifier
                    .weight(share.toFloat())
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

/** Small rounded colour swatch for legends. */
@Composable
fun ColorSquare(color: Color, size: Dp = 7.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/** Legend entry: colour square, label, right-aligned value. */
@Composable
fun LegendItem(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    val colors = Fp.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = colors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = value, style = FpType.number(11.sp, FontWeight.Normal, colors.textMuted))
    }
}

/** Label over value, the stat-grid cell the cards repeat. */
@Composable
fun StatCell(label: String, value: String, valueColor: Color = Fp.colors.text, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        FpLabel(label, color = Fp.colors.textSubtle, size = 9.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = FpType.mono(12.sp, FontWeight.SemiBold, valueColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Fp.colors.border),
    )
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

/** Thin padding wrapper used for list rows. */
fun Modifier.rowPadding(): Modifier = this.padding(horizontal = 12.dp, vertical = 11.dp)
