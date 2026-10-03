package com.dev.alex.portfolio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.alex.portfolio.domain.formatCompact
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val GRID_LINES = 4

private fun DrawScope.axis(
    measurer: TextMeasurer,
    min: Double,
    max: Double,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    gridColor: Color,
    labelColor: Color,
) {
    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    for (i in 0..GRID_LINES) {
        val y = top + (bottom - top) * i / GRID_LINES
        drawLine(
            color = gridColor,
            start = Offset(left, y),
            end = Offset(right, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f)),
        )
        val value = max - (max - min) * i / GRID_LINES
        val layout = measurer.measure(formatCompact(value), style)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(left - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f),
        )
    }
}

/**
 * The mockups' AreaChart: one series, gradient fill, dashed grid with compact labels, a
 * dot on the last point. Tap a point to select it; the caller shows what it means.
 */
@Composable
fun AreaChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = Brand.Primary,
    height: Dp = 130.dp,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
) {
    val colors = Fp.colors
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values, selectedIndex) {
                detectTapGestures { tap ->
                    if (values.size < 2) return@detectTapGestures
                    val left = 40.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val fraction = ((tap.x - left) / (right - left)).coerceIn(0f, 1f)
                    val index = (fraction * (values.size - 1)).roundToInt()
                    onSelect(if (index == selectedIndex) null else index)
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val left = 40.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        var low = values.min() * 0.985
        var high = values.max() * 1.005
        if (high - low < 1e-9) {
            low -= 1
            high += 1
        }
        axis(measurer, low, high, left, right, top, bottom, colors.border, colors.textMuted)

        fun x(i: Int) = if (values.size == 1) right else left + (right - left) * i / (values.size - 1)
        fun y(v: Double) = (top + (1 - (v - low) / (high - low)) * (bottom - top)).toFloat()

        val line = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(values.lastIndex), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)), startY = top, endY = bottom))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val marked = selectedIndex?.takeIf { it in values.indices } ?: values.lastIndex
        if (selectedIndex != null && selectedIndex in values.indices) {
            drawLine(colors.textSubtle, Offset(x(marked), top), Offset(x(marked), bottom), strokeWidth = 1f)
        }
        val dot = Offset(x(marked), y(values[marked]))
        drawCircle(colors.surface, radius = 6.dp.toPx(), center = dot)
        drawCircle(color, radius = 4.dp.toPx(), center = dot)
    }
}

/**
 * Vertical bars with the same grid as [AreaChart]. Tap a bar to select it: the rest dim
 * and the selected one prints its value — the mobile stand-in for the web's hover.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = Brand.Primary,
    barColors: List<Color>? = null,
    height: Dp = 150.dp,
    selectedIndex: Int? = null,
    valueLabel: (Double) -> String = ::formatCompact,
    onSelect: (Int?) -> Unit = {},
) {
    val colors = Fp.colors
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values, selectedIndex) {
                detectTapGestures { tap ->
                    if (values.isEmpty()) return@detectTapGestures
                    val left = 44.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val cell = (right - left) / values.size
                    val index = floor((tap.x - left) / cell).toInt()
                    onSelect(if (index !in values.indices || index == selectedIndex) null else index)
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val left = 44.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val high = max(values.max() * 1.05, 1e-9)
        axis(measurer, 0.0, high, left, right, top, bottom, colors.border, colors.textMuted)

        val cell = (right - left) / values.size
        val barWidth = cell * 0.6f
        val labelStyle = TextStyle(fontSize = 10.sp, color = colors.textMuted)
        // thin the axis labels out when they would collide
        val widest = labels.maxOfOrNull { measurer.measure(it, labelStyle).size.width } ?: 0
        val every = max(1, kotlin.math.ceil((widest + 6.dp.toPx()) / cell).toInt())

        values.forEachIndexed { i, v ->
            val x = left + i * cell + (cell - barWidth) / 2
            val y = (top + (1 - v / high) * (bottom - top)).toFloat()
            val base = barColors?.getOrNull(i) ?: color
            val dimmed = selectedIndex != null && selectedIndex != i
            drawRoundRect(
                color = if (dimmed) colors.textSubtle.copy(alpha = 0.35f) else base.copy(alpha = 0.92f),
                topLeft = Offset(x, min(y, bottom)),
                size = Size(barWidth, max(0f, bottom - y)),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
            if (i % every == 0 || i == selectedIndex) {
                val text = labels.getOrNull(i).orEmpty()
                val layout = measurer.measure(text, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x + barWidth / 2 - layout.size.width / 2f, bottom + 4.dp.toPx()),
                )
            }
            if (i == selectedIndex) {
                val layout = measurer.measure(
                    valueLabel(v),
                    TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = colors.text),
                )
                val labelX = (x + barWidth / 2 - layout.size.width / 2f)
                    .coerceIn(0f, size.width - layout.size.width)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(labelX, max(0f, y - layout.size.height - 2.dp.toPx())),
                )
            }
        }
    }
}
