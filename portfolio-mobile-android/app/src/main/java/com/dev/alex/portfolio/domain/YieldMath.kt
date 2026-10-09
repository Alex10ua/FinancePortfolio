package com.dev.alex.portfolio.domain

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The Yield Target view's maths, a port of the web's `yieldMath.ts` (and the backend's
 * `AiYieldMath`): where today's forward yield sits inside a ticker's own history of
 * trailing-twelve-month yields. Yields are ratios, so nothing here needs FX.
 */
enum class YieldTimeframe(val label: String, val months: Int?) {
    Y1("1Y", 12),
    Y3("3Y", 36),
    Y5("5Y", 60),
    Y10("10Y", 120),
    All("All", null),
}

/** The percentile floors offered, label → value. */
val YIELD_FLOORS = listOf("Any" to 0, "50th" to 50, "75th" to 75, "90th" to 90, "95th" to 95, "98th" to 98)

/** Linear-interpolated quantile of an ascending list, the same as the web's and the backend's. */
fun quantile(ascending: List<Double>, q: Double): Double {
    if (ascending.isEmpty()) return 0.0
    val i = (ascending.size - 1) * q
    val lo = floor(i).toInt()
    val hi = ceil(i).toInt()
    return ascending[lo] + (ascending[hi] - ascending[lo]) * (i - lo)
}

data class YieldStats(
    /** the timeframe slice, ascending */
    val sorted: List<Double>,
    /** today's forward yield: the level being ranked, not part of the window */
    val current: Double,
    val min: Double,
    val max: Double,
    val p25: Double,
    val median: Double,
    val p75: Double,
    val p90: Double,
    /** share of the window's months at or below today's yield, 0–100 */
    val percentile: Double,
    /** today's yield as a percent premium over the window median */
    val vsMedian: Double,
) {
    /** The yield level at [percentile] of the window. */
    fun yieldAt(percentile: Double): Double = quantile(sorted, maxOf(percentile, 1.0) / 100)
}

/**
 * Null when there is no history to rank against or no forward yield. [history] is the
 * monthly series oldest first, as the API sends it.
 */
fun yieldStats(history: List<Double>, current: Double?, timeframe: YieldTimeframe): YieldStats? {
    if (history.isEmpty() || current == null) return null
    val window = timeframe.months?.let { history.takeLast(it) } ?: history
    if (window.isEmpty()) return null
    val sorted = window.sorted()
    val median = quantile(sorted, 0.5)
    val below = sorted.count { it <= current }
    return YieldStats(
        sorted = sorted,
        current = current,
        min = sorted.first(),
        max = sorted.last(),
        p25 = quantile(sorted, 0.25),
        median = median,
        p75 = quantile(sorted, 0.75),
        p90 = quantile(sorted, 0.9),
        percentile = below.toDouble() / sorted.size * 100,
        vsMedian = if (median > 0) (current - median) / median * 100 else 0.0,
    )
}

/** "st"/"nd"/"rd"/"th" for a rounded percentile. */
fun ordinal(n: Double): String {
    val v = n.roundToInt()
    if (v % 100 in 11..13) return "th"
    return when (v % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}
