package com.dev.alex.portfolio.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Port of the web client's `lib/currency.ts`. The API answers in native currencies and the
 * client converts — EUR is the pivot, rates are units of a currency per 1 EUR.
 */
typealias FxRates = Map<String, Double>

enum class CurrencyDisplay {
    Symbol, Code, Both;

    companion object {
        fun parse(value: String?): CurrencyDisplay = CurrencyDisplay.entries.firstOrNull { it.name == value } ?: Symbol
    }
}

private val SYMBOLS = mapOf(
    "EUR" to "€", "USD" to "$", "GBP" to "£", "CHF" to "Fr", "PLN" to "zł", "SEK" to "kr",
    "NOK" to "kr", "DKK" to "kr", "CZK" to "Kč", "HUF" to "Ft", "CAD" to "$", "AUD" to "$",
    "JPY" to "¥", "SGD" to "$", "HKD" to "$", "ILS" to "₪",
)

/** GBp/GBx pence collapse to GBP — the same asset, quoted in minor units. */
fun normalizeCurrency(code: String?): String = when {
    code.isNullOrEmpty() -> "USD"
    code == "GBp" || code == "GBx" -> "GBP"
    else -> code
}

fun isMinorUnit(code: String?): Boolean = code == "GBp" || code == "GBx"

fun toMajorUnits(value: Double, quoteCurrency: String?): Double =
    if (isMinorUnit(quoteCurrency)) value / 100 else value

/** Symbol for a code; an unknown code writes itself plus a space ("USDT 12.00"). */
fun currencySymbol(code: String?): String {
    val normalized = normalizeCurrency(code)
    return SYMBOLS[normalized] ?: "$normalized "
}

/** rateVsEur of a quote currency. Pence have no ECB rate: GBP × 100, as the backend does. */
fun rateOf(code: String?, rates: FxRates): Double {
    if (code.isNullOrEmpty()) return 1.0
    if (isMinorUnit(code)) {
        val gbp = rates["GBP"]
        return if (gbp != null && gbp != 0.0) gbp * 100 else 1.0
    }
    val rate = rates[code]
    return if (rate != null && rate != 0.0) rate else 1.0
}

fun convert(amount: Double, from: String?, to: String?, rates: FxRates): Double {
    if (!amount.isFinite() || amount == 0.0) return 0.0
    if (to.isNullOrEmpty() || from.isNullOrEmpty() || from == to) return amount
    return amount * rateOf(to, rates) / rateOf(from, rates)
}

/** Sum a {currency: nativeAmount} map into one currency. */
fun convertMap(byCurrency: Map<String, Double?>?, to: String, rates: FxRates): Double =
    byCurrency?.entries?.sumOf { (currency, amount) -> convert(amount ?: 0.0, currency, to, rates) } ?: 0.0

/**
 * "Probably pence" where no quote currency is available: a London listing (.L/.IL) held
 * in GBP. Only a fallback — prefer MarketData's own currency wherever it is sent.
 */
fun looksLikePenceQuote(ticker: String, txCurrency: String?): Boolean =
    normalizeCurrency(txCurrency) == "GBP" && PENCE_SUFFIX.containsMatchIn(ticker)

private val PENCE_SUFFIX = Regex("""\.(L|IL)$""", RegexOption.IGNORE_CASE)

/** Grouped, fixed decimals, rounded half-up like JavaScript's toLocaleString. */
fun formatNumber(value: Double, decimals: Int): String {
    val pattern = if (decimals > 0) "#,##0." + "0".repeat(decimals) else "#,##0"
    val format = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(value)
}

/**
 * Money in the chosen notation: `€1,234.50`, `1,234.50 EUR`, `€1,234.50 EUR`. The sign
 * goes before the symbol (`-€50.00`, never `€-50.00`). Tiny amounts get 6 decimals so a
 * fraction of a cent doesn't print as 0.00.
 */
fun formatMoney(
    value: Double?,
    currency: String,
    display: CurrencyDisplay = CurrencyDisplay.Symbol,
    decimals: Int? = null,
): String {
    if (value == null || !value.isFinite()) return "—"
    val absolute = abs(value)
    val digits = decimals ?: if (absolute > 0 && absolute < 0.01) 6 else 2
    val number = formatNumber(absolute, digits)
    val sign = if (value < 0 && number.any { it in '1'..'9' }) "-" else ""
    val prefix = if (display == CurrencyDisplay.Code) "" else currencySymbol(currency)
    val suffix = if (display == CurrencyDisplay.Symbol) "" else " ${normalizeCurrency(currency)}"
    return "$sign$prefix$number$suffix"
}

/** Money with an explicit sign either way: `+€12.00` / `-€12.00`. */
fun formatSignedMoney(value: Double?, currency: String, display: CurrencyDisplay, decimals: Int? = null): String {
    if (value == null || !value.isFinite()) return "—"
    val text = formatMoney(abs(value), currency, display, decimals)
    val zero = text.none { it in '1'..'9' }
    return when {
        zero -> text
        value < 0 -> "-$text"
        else -> "+$text"
    }
}

fun formatPercent(value: Double?, decimals: Int = 2): String {
    if (value == null || !value.isFinite()) return "—"
    return String.format(Locale.US, "%.${decimals}f%%", value)
}

/** `+1.24%` / `-0.62%`; a value that rounds to zero carries no sign. */
fun formatSignedPercent(value: Double?, decimals: Int = 2): String {
    if (value == null || !value.isFinite()) return "—"
    val text = String.format(Locale.US, "%.${decimals}f", abs(value))
    val zero = text.none { it in '1'..'9' }
    val sign = when {
        zero -> ""
        value > 0 -> "+"
        else -> "-"
    }
    return "$sign$text%"
}

/** Share counts: fractional positions keep 4 decimals, whole ones none. */
fun formatShares(value: Double?): String {
    if (value == null || !value.isFinite()) return "—"
    if (abs(value) < 1) return String.format(Locale.US, "%.4f", value)
    return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/** A count rounded to a whole number, grouped: 1,240. */
fun formatCount(value: Double?): String =
    if (value == null || !value.isFinite()) "—" else formatNumber(Math.round(value).toDouble(), 0)

/** Chart axis ticks: 187.4k, 1.2M, 950. */
fun formatCompact(value: Double): String {
    val absolute = abs(value)
    return when {
        absolute >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000)
        absolute >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000)
        else -> String.format(Locale.US, "%.0f", value)
    }
}

/**
 * The portfolio's display currency plus the converters into it — the mobile twin of the
 * web's `usePortfolioCurrency`.
 */
class CurrencyContext(
    val base: String,
    val display: CurrencyDisplay,
    val rates: FxRates,
) {
    /** native amount → base currency; a missing currency means it already is base */
    fun toBase(value: Double?, from: String?): Double = convert(value ?: 0.0, from ?: base, base, rates)

    fun sumToBase(byCurrency: Map<String, Double?>?): Double = convertMap(byCurrency, base, rates)

    fun money(value: Double?, currency: String = base, decimals: Int? = null): String =
        formatMoney(value, currency, display, decimals)

    fun signedMoney(value: Double?, currency: String = base, decimals: Int? = null): String =
        formatSignedMoney(value, currency, display, decimals)
}

/**
 * An explicit per-portfolio setting wins; otherwise the portfolio's single currency, or USD
 * when it mixes several (the web's long-standing default).
 */
fun resolveBaseCurrency(saved: String?, heldCurrencies: Collection<String?>): String {
    if (!saved.isNullOrBlank()) return saved
    val held = heldCurrencies.filterNotNull().filter { it.isNotBlank() }.map(::normalizeCurrency).toSet()
    return if (held.size == 1) held.first() else "USD"
}

/** `/fx-rates` decoded with nullable values → a clean map. */
fun Map<String, Double?>.toFxRates(): FxRates =
    entries.mapNotNull { (code, rate) -> rate?.takeIf { it.isFinite() && it > 0 }?.let { code to it } }.toMap()
