package com.dev.alex.portfolio.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * The Ownership screen's maths, ported from the web's `OwnershipPage`: how much of each
 * company (or coin's circulating supply) a position is, as "1 part in N", ranked, with
 * the client-only What-if projection on top. Nothing here is ever saved.
 */
enum class OwnershipTier(val label: String, val color: Long) {
    Meaningful("Meaningful", 0xFF10B981),
    Small("Small", 0xFF14B8A6),
    Tiny("Tiny", 0xFF3B82F6),
    Trace("Trace", 0xFF8B5CF6),
}

fun tierFor(fraction: Double): OwnershipTier = when {
    fraction >= 5e-7 -> OwnershipTier.Meaningful
    fraction >= 2e-8 -> OwnershipTier.Small
    fraction >= 1e-9 -> OwnershipTier.Tiny
    else -> OwnershipTier.Trace
}

data class OwnershipRow(
    val ticker: String,
    val name: String?,
    val assetType: String?,
    /** shares outstanding, or circulating supply for a coin */
    val outstanding: Double,
    /** what is actually held */
    val baseShares: Double,
    /** the projection; equal to [baseShares] with What-if off */
    val shares: Double,
    /** rank by today's stake, 1 = largest */
    val baseRank: Int,
) {
    val baseFraction: Double get() = baseShares / outstanding
    val fraction: Double get() = if (shares > 0) shares / outstanding else 0.0
    val oneIn: Double get() = if (shares > 0) outstanding / shares else Double.POSITIVE_INFINITY
    val baseOneIn: Double get() = outstanding / baseShares
    val delta: Double get() = round6(shares - baseShares)
    val tier: OwnershipTier get() = tierFor(if (fraction > 0) fraction else baseFraction)
}

data class OwnershipView(
    /** largest stake first */
    val rows: List<OwnershipRow>,
    /** log10 bounds of the bar scale, over projected and today's stakes alike */
    val minLog: Double,
    val maxLog: Double,
) {
    val held: List<OwnershipRow> get() = rows.filter { it.shares > 0 }
    val totalShares: Double get() = rows.sumOf { it.shares }
    val baseTotal: Double get() = rows.sumOf { it.baseShares }
    val changed: List<OwnershipRow> get() = rows.filter { it.delta != 0.0 }
}

/**
 * Stocks against shares outstanding, crypto against circulating supply. A custom asset
 * has no such figure, so it drops out on its own. [projection] (ticker → shares) is the
 * What-if state; null = off.
 */
fun ownershipView(holdings: List<Holding>, projection: Map<String, Double>?): OwnershipView? {
    val base = holdings.filter { it.shareAmount > 0 && (it.sharesOutstanding ?: 0L) > 0 }
    if (base.isEmpty()) return null
    val rank = base.sortedByDescending { it.shareAmount / it.sharesOutstanding!!.toDouble() }
        .mapIndexed { index, h -> h.ticker to index + 1 }
        .toMap()
    val rows = base.map { h ->
        OwnershipRow(
            ticker = h.ticker,
            name = h.name,
            assetType = h.assetType,
            outstanding = h.sharesOutstanding!!.toDouble(),
            baseShares = h.shareAmount,
            shares = projection?.get(h.ticker) ?: h.shareAmount,
            baseRank = rank.getValue(h.ticker),
        )
    }.sortedByDescending { it.fraction }
    // the scale spans both, so a ghost marker of today's stake stays on the track
    val logs = rows.filter { it.fraction > 0 }.map { log10(it.fraction) } + rows.map { log10(it.baseFraction) }
    return OwnershipView(rows, logs.min(), logs.max())
}

/** Where a stake sits on the log bar, 0…1 (at least 0.02, so a dot never vanishes). */
fun barPosition(fraction: Double, minLog: Double, maxLog: Double): Double {
    val span = maxLog - minLog
    return if (span == 0.0) 1.0 else maxOf(0.02, (log10(fraction) - minLog) / span)
}

/** One stepper tap ≈ 10% of the position, snapped clean: 0.42 BTC steps by 0.04. */
fun stepFor(base: Double): Double {
    val step = base / 10
    return when {
        step >= 1 -> maxOf(1.0, Math.round(step).toDouble())
        step <= 0 -> 1.0
        else -> java.math.BigDecimal(step).round(java.math.MathContext(1)).toDouble()
    }
}

/** Kills float noise from repeated ± steps (0.30000000000000004). */
fun round6(value: Double): Double = (value * 1e6).roundToLong() / 1e6

/** The N in "1 part in N": "592k", "1.29M", "24.4B". */
fun formatOneIn(n: Double): String = when {
    n.isInfinite() -> "∞"
    n >= 1e9 -> fixed(n / 1e9, if (n / 1e9 < 10) 2 else 1) + "B"
    n >= 1e6 -> fixed(n / 1e6, if (n / 1e6 < 10) 2 else 1) + "M"
    n >= 1e3 -> "${(n / 1e3).roundToLong()}k"
    else -> "${n.roundToLong()}"
}

/** Shares outstanding: "15.2B", "820.4M", else the plain count. */
fun formatShareCount(n: Double): String = when {
    n >= 1e9 -> fixed(n / 1e9, 2) + "B"
    n >= 1e6 -> fixed(n / 1e6, 1) + "M"
    else -> formatHeldShares(n)
}

/** A held count: whole shares grouped ("1,200"), coins to six places ("0.4213"). */
fun formatHeldShares(n: Double): String =
    DecimalFormat(if (n % 1.0 == 0.0) "#,##0" else "#,##0.######", DecimalFormatSymbols(Locale.US)).format(n)

/** "4.2e-7" → "0.000042%" style: two significant digits, as the web's toPrecision(2). */
fun formatStakePercent(fraction: Double): String =
    java.math.BigDecimal(fraction * 100).round(java.math.MathContext(2)).stripTrailingZeros().toPlainString() + "%"

private fun fixed(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
