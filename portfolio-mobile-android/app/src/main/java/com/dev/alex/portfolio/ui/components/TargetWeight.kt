package com.dev.alex.portfolio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.alex.portfolio.domain.driftOf
import com.dev.alex.portfolio.domain.formatNumber
import com.dev.alex.portfolio.domain.formatTarget
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType

/**
 * The mockups' `TargetPctCell` stretched full width, as the mobile dashboard draws it:
 * "% OF PORTFOLIO" on the left, `{current}%` or `{current}%/{target}%` on the right, and
 * a 3 dp bar under both. Read-only: setting a target stays on the web, whose settings
 * PUT replaces the whole document.
 *
 * The bar follows the web's `TargetPercentCell`, not the mockup's absolute scale. With a
 * target, the track runs 0…target, so a full bar means reached; past it the bar stays full
 * with a hatched end, and a stop marks the end. Without one, it fills against the shared
 * [scaleMax] ([com.dev.alex.portfolio.domain.barScaleMax]). Tone is the drift colour:
 * over = amber, within ±1pp = green, under = teal.
 */
@Composable
fun TargetWeight(label: String, current: Double, target: Double?, scaleMax: Double, modifier: Modifier = Modifier) {
    val colors = Fp.colors
    val tone = if (target == null) {
        if (colors.isDark) NoTargetDark else Brand.Primary
    } else {
        colors.driftColor(driftOf(current, target))
    }
    // a 0% target means "hold none of this": any position at all is past it
    val ratio: Double? = when {
        target == null -> null
        target > 0 -> current / target
        current > 0 -> Double.POSITIVE_INFINITY
        else -> 0.0
    }
    val fill = (ratio?.coerceAtMost(1.0) ?: (current / scaleMax)).coerceIn(0.0, 1.0).toFloat()
    val capped = ratio != null && ratio > 1
    val track = colors.border
    val stop = if (colors.isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FpLabel(label, color = colors.textSubtle, size = 9.5.sp, modifier = Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    append("${formatNumber(current, 1)}%")
                    if (target != null) {
                        withStyle(SpanStyle(color = colors.textSubtle)) { append("/") }
                        withStyle(SpanStyle(color = tone, fontWeight = FontWeight.SemiBold)) { append("${formatTarget(target)}%") }
                    }
                },
                style = FpType.mono(12.sp, FontWeight.Medium, colors.text),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(3.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(7.dp),
        ) {
            val barHeight = 3.dp.toPx()
            val top = (size.height - barHeight) / 2
            val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
            val width = size.width * fill
            if (width > 0f) {
                drawRoundRect(tone, topLeft = Offset(0f, top), size = Size(width, barHeight), cornerRadius = radius)
            }
            if (capped) {
                // hatched end cap: the position runs past the end of the track
                val capStart = width - 8.dp.toPx()
                val step = 3.dp.toPx()
                clipRect(left = capStart, top = top, right = width, bottom = top + barHeight) {
                    var x = capStart - barHeight
                    while (x < width) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f),
                            start = Offset(x, top + barHeight),
                            end = Offset(x + barHeight, top),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        x += step
                    }
                }
            }
            if (ratio != null) {
                // end stop: where the target sits, so a full bar reads as "reached"
                drawRect(stop, topLeft = Offset(size.width - 2.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
            }
        }
    }
}

/** The web's `dark:bg-indigo-400` for a bar with no target. */
private val NoTargetDark = Color(0xFF818CF8)
