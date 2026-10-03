package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.CreateTransactionRequest
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The "Add Transaction" form's rules, ported from the web's `CreateTransactionDialog`:
 * the same asset → transaction-type table and the same request shapes. The legacy
 * COIN/FIGURINE/FUND types are left out (CUSTOM replaced them), and so is creating a
 * custom asset — that stays on the web.
 */
enum class AssetKind(val label: String, val types: List<String>) {
    Stock("Stock", listOf("BUY", "SELL", "DIVIDEND", "TAX")),
    Crypto("Crypto", listOf("BUY", "SELL")),
    Custom("Custom", listOf("BUY", "SELL")),
    Cash("Cash", listOf("DEPOSIT", "WITHDRAWAL")),
}

/** The web form's currency list — the ones the backend has ECB rates for. */
val TRANSACTION_CURRENCIES = listOf("USD", "EUR", "GBP", "CHF", "PLN", "CZK")

/** What the user has typed so far. Numbers stay text until they are sent. */
data class TransactionDraft(
    val date: LocalDate,
    val kind: AssetKind = AssetKind.Stock,
    val type: String = AssetKind.Stock.types.first(),
    val ticker: String = "",
    val quantity: String = "",
    val price: String = "",
    val commission: String = "",
    val amount: String = "",
    val currency: String = "USD",
) {
    /** Switching asset kind resets the type to that kind's first one, as on the web. */
    fun withKind(next: AssetKind): TransactionDraft =
        if (next == kind) this else copy(kind = next, type = next.types.first(), ticker = "")

    /** Shares × price, or null while either is missing. Cash has no total but its amount. */
    fun total(): BigDecimal? {
        if (kind == AssetKind.Cash) return null
        val q = parseDecimal(quantity) ?: return null
        val p = parseDecimal(price) ?: return null
        return q.multiply(p)
    }
}

/** "1234.5", "1234,5" and "1 234,5" alike — a phone keyboard may offer either separator. */
fun parseDecimal(text: String): BigDecimal? {
    val cleaned = text.trim().replace(" ", "").replace(" ", "").replace(',', '.')
    if (cleaned.isEmpty()) return null
    return cleaned.toBigDecimalOrNull()
}

/**
 * The first thing stopping [draft] from being sent, or null. Stricter than the web in two
 * ways meant for a phone: no date after [today] (a mis-tap on the calendar), and quantity
 * must be above zero.
 */
fun validate(draft: TransactionDraft, today: LocalDate): String? {
    if (draft.type !in draft.kind.types) return "Pick a transaction type."
    if (draft.date.isAfter(today)) return "The date can't be in the future."
    if (draft.kind == AssetKind.Cash) {
        val amount = parseDecimal(draft.amount) ?: return "Enter the amount."
        if (amount.signum() <= 0) return "The amount must be more than 0."
    } else {
        if (draft.ticker.isBlank()) {
            return if (draft.kind == AssetKind.Custom) "Pick a custom asset." else "Enter a ticker."
        }
        val quantity = parseDecimal(draft.quantity) ?: return "Enter the quantity."
        if (quantity.signum() <= 0) return "The quantity must be more than 0."
        val price = parseDecimal(draft.price) ?: return "Enter the price."
        if (price.signum() < 0) return "The price can't be negative."
    }
    if (draft.commission.isNotBlank()) {
        val commission = parseDecimal(draft.commission) ?: return "The commission must be a number."
        if (commission.signum() < 0) return "The commission can't be negative."
    }
    return null
}

/**
 * The web form's two payloads. Cash books under its currency as ticker, with no asset
 * type and zero quantity/price; everything else carries quantity × price. Call only after
 * [validate] passed.
 */
fun TransactionDraft.toRequest(): CreateTransactionRequest {
    val fee = parseDecimal(commission)?.toPlainString() ?: "0"
    return if (kind == AssetKind.Cash) {
        CreateTransactionRequest(
            ticker = currency,
            transactionType = type,
            quantity = "0",
            price = "0",
            commission = fee,
            date = date.toString(),
            currency = currency,
            amount = parseDecimal(amount)?.toPlainString(),
        )
    } else {
        CreateTransactionRequest(
            ticker = ticker.trim().uppercase(),
            transactionType = type,
            assetType = kind.name.uppercase(),
            quantity = parseDecimal(quantity)?.toPlainString() ?: "0",
            price = parseDecimal(price)?.toPlainString() ?: "0",
            commission = fee,
            date = date.toString(),
            currency = currency,
        )
    }
}
