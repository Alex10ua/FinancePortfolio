package com.dev.alex.portfolio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.alex.portfolio.domain.Drift
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpColors
import com.dev.alex.portfolio.ui.theme.FpType
import kotlin.math.roundToInt

private val RangeRed = Color(0xFFEF4444)
private val RangeAmber = Color(0xFFF59E0B)
private val RangeGreen = Color(0xFF10B981)

private fun rangeColor(fraction: Float): Color =
    if (fraction < 0.5f) lerp(RangeRed, RangeAmber, fraction / 0.5f)
    else lerp(RangeAmber, RangeGreen, (fraction - 0.5f) / 0.5f)

/**
 * The holdings table's signature 52-week range: where today's price sits between the
 * year's low and high. All three figures are in the quote currency.
 */
@Composable
fun RangeBar(low: Double, high: Double, price: Double, format: (Double) -> String, modifier: Modifier = Modifier) {
    val colors = Fp.colors
    val span = high - low
    val fraction = if (span > 0) ((price - low) / span).coerceIn(0.0, 1.0).toFloat() else 0.5f
    val dot = rangeColor(fraction)
    val dark = colors.isDark
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(12.dp),
        ) {
            val trackHeight = 6.dp.toPx()
            val top = (size.height - trackHeight) / 2
            val radius = CornerRadius(trackHeight / 2, trackHeight / 2)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        RangeRed.copy(alpha = if (dark) 0.28f else 0.22f),
                        RangeAmber.copy(alpha = if (dark) 0.28f else 0.22f),
                        RangeGreen.copy(alpha = if (dark) 0.30f else 0.24f),
                    ),
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )
            val travelled = size.width * fraction
            if (travelled > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(RangeRed.copy(alpha = if (dark) 0.55f else 0.45f), dot),
                        startX = 0f,
                        endX = travelled,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(travelled, trackHeight),
                    cornerRadius = radius,
                )
            }
            val center = Offset(travelled.coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), size.height / 2)
            drawCircle(colors.surface, radius = 6.dp.toPx(), center = center)
            drawCircle(dot, radius = 4.75.dp.toPx(), center = center, style = Stroke(width = 2.5.dp.toPx()))
        }
        VSpace(5.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(format(low), style = FpType.mono(9.5.sp, FontWeight.Normal, colors.textSubtle), modifier = Modifier.weight(1f))
            Text("${(fraction * 100).roundToInt()}%", style = FpType.mono(9.5.sp, FontWeight.SemiBold, colors.textMuted))
            Text(
                format(high),
                style = FpType.mono(9.5.sp, FontWeight.Normal, colors.textSubtle),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Self-funding progress toward this row's own threshold — the track is 0…needed, so full
 * means reached and every overshoot gets the same hatched cap; the label carries how far.
 */
@Composable
fun ProgressTrack(progress: Double, reached: Boolean, color: Color, label: String, modifier: Modifier = Modifier, barHeight: Dp = 8.dp) {
    val colors = Fp.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .height(barHeight),
        ) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            val outline = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, radius))
            }
            clipPath(outline) {
                drawRect(colors.track)
                val fill = size.width * progress.coerceIn(0.02, 1.0).toFloat()
                drawRect(
                    brush = if (reached) Brush.horizontalGradient(listOf(color, color))
                    else Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), color), startX = 0f, endX = fill),
                    size = Size(fill, size.height),
                )
                if (reached) {
                    // hatched end cap over the last quarter
                    val capStart = size.width * 0.74f
                    val stripe = 5.dp.toPx()
                    val hatch = if (colors.isDark) Color(0x8C020617) else Color(0xBFFFFFFF)
                    var x = capStart - size.height
                    while (x < size.width) {
                        drawLine(
                            color = hatch,
                            start = Offset(maxOf(x, capStart), size.height - (maxOf(x, capStart) - x)),
                            end = Offset(x + size.height, 0f),
                            strokeWidth = 2.dp.toPx(),
                        )
                        x += stripe
                    }
                }
            }
            drawRoundRect(colors.border, cornerRadius = radius, style = Stroke(width = 1.dp.toPx()))
        }
        Text(
            label,
            style = FpType.mono(10.5.sp, FontWeight.Bold, if (reached) color else colors.textMuted),
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

/** Allocation-vs-target colour: over = amber, on (±1pp) = green, under = teal. */
fun FpColors.driftColor(drift: Drift): Color = when (drift) {
    Drift.Over -> over
    Drift.On -> onTarget
    Drift.Under -> under
}
