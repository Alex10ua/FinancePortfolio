package com.dev.alex.portfolio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Design tokens from portfolio-design/project/src/tokens.jsx. */
object Brand {
    val Primary = Color(0xFF4F46E5)
    val PrimaryHover = Color(0xFF4338CA)
    val Primary50 = Color(0xFFEEF2FF)
    val Primary100 = Color(0xFFE0E7FF)
    val Primary200 = Color(0xFFC7D2FE)
    val Primary900 = Color(0xFF312E81)
    val Indigo400 = Color(0xFF6366F1)
    val Indigo800 = Color(0xFF3730A3)
}

object Semantic {
    val Success = Color(0xFF10B981)
    val Danger = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)
    val Warning = Color(0xFFF59E0B)
    val Purple = Color(0xFF8B5CF6)
    val Teal = Color(0xFF14B8A6)
}

@Immutable
data class FpColors(
    val isDark: Boolean,
    val pageBg: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val sidebar: Color,
    /** data-ink tuned per theme */
    val gain: Color,
    val loss: Color,
    val gainBg: Color,
    val lossBg: Color,
    val link: Color,
    val over: Color,
    val onTarget: Color,
    val under: Color,
    /** progress-track background */
    val track: Color,
)

val LightColors = FpColors(
    isDark = false,
    pageBg = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF8FAFC),
    border = Color(0xFFE2E8F0),
    borderStrong = Color(0xFFCBD5E1),
    text = Color(0xFF0F172A),
    textMuted = Color(0xFF475569),
    textSubtle = Color(0xFF94A3B8),
    sidebar = Color(0xFFFFFFFF),
    gain = Color(0xFF059669),
    loss = Color(0xFFDC2626),
    gainBg = Color(0xFFECFDF5),
    lossBg = Color(0xFFFEF2F2),
    link = Color(0xFF2563EB),
    over = Color(0xFFB45309),
    onTarget = Color(0xFF059669),
    under = Color(0xFF0F766E),
    track = Color(0xFFF1F5F9),
)

val DarkColors = FpColors(
    isDark = true,
    pageBg = Color(0xFF020617),
    surface = Color(0xFF1E293B),
    surfaceMuted = Color(0xFF0F172A),
    border = Color(0xFF334155),
    borderStrong = Color(0xFF475569),
    text = Color(0xFFF1F5F9),
    textMuted = Color(0xFF94A3B8),
    textSubtle = Color(0xFF64748B),
    sidebar = Color(0xFF0F172A),
    gain = Color(0xFF34D399),
    loss = Color(0xFFF87171),
    gainBg = Color(0x2410B981),
    lossBg = Color(0x24EF4444),
    link = Color(0xFF60A5FA),
    over = Color(0xFFFBBF24),
    onTarget = Color(0xFF34D399),
    under = Color(0xFF2DD4BF),
    track = Color(0xFF0F172A),
)

val LocalFpColors = staticCompositionLocalOf { LightColors }

object Fp {
    val colors: FpColors
        @Composable @ReadOnlyComposable get() = LocalFpColors.current

    /** tabular figures, so columns of numbers line up */
    const val TNUM = "tnum"
}

fun FpColors.gainLoss(value: Double): Color = if (value >= 0) gain else loss
fun FpColors.gainLossBg(value: Double): Color = if (value >= 0) gainBg else lossBg

/** Text styles of the mockups' recurring roles. */
object FpType {
    fun label(color: Color, size: TextUnit = 10.sp) = TextStyle(
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        color = color,
        letterSpacing = 0.8.sp,
    )

    fun number(size: TextUnit, weight: FontWeight = FontWeight.SemiBold, color: Color) = TextStyle(
        fontSize = size,
        fontWeight = weight,
        color = color,
        fontFeatureSettings = Fp.TNUM,
    )

    fun mono(size: TextUnit, weight: FontWeight = FontWeight.SemiBold, color: Color) = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = weight,
        color = color,
        fontFeatureSettings = Fp.TNUM,
    )
}

@Composable
fun FinanceTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = Brand.Primary,
            onPrimary = Color.White,
            background = colors.pageBg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textMuted,
            outline = colors.border,
            error = Semantic.Danger,
        )
    } else {
        lightColorScheme(
            primary = Brand.Primary,
            onPrimary = Color.White,
            background = colors.pageBg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceMuted,
            onSurfaceVariant = colors.textMuted,
            outline = colors.border,
            error = Semantic.Danger,
        )
    }
    CompositionLocalProvider(LocalFpColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
