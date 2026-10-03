package com.dev.alex.portfolio.domain

import com.dev.alex.portfolio.data.api.TransactionDto

val TRANSACTION_TYPES = listOf("BUY", "SELL", "DIVIDEND", "TAX", "DEPOSIT", "WITHDRAWAL")

/** Ledger reading of a row, as the design colours it: cash in, cash out, tax, or a purchase. */
enum class Direction { In, Out, Tax, Neutral }

fun directionOf(type: String?): Direction = when (type) {
    "DIVIDEND", "DEPOSIT" -> Direction.In
    "SELL", "WITHDRAWAL" -> Direction.Out
    "TAX" -> Direction.Tax
    else -> Direction.Neutral
}

/** Trades carry totalAmount; cash events carry amount; old rows may only have qty × price. */
fun amountOf(tx: TransactionDto): Double? =
    tx.totalAmount ?: tx.amount ?: run {
        val quantity = tx.quantity ?: return@run null
        val price = tx.price ?: return@run null
        quantity * price
    }

/** Trades show "qty × price"; cash events have neither. */
fun hasQuantity(tx: TransactionDto): Boolean =
    (tx.transactionType == "BUY" || tx.transactionType == "SELL" || tx.transactionType == "DIVIDEND") &&
        tx.quantity != null && tx.price != null && tx.quantity != 0.0

/** Years that have transactions, newest first, always including the current one. */
fun transactionYears(transactions: List<TransactionDto>, currentYear: Int): List<Int> =
    (transactions.mapNotNull { yearOf(it.date) } + currentYear).distinct().sortedDescending()

fun filterTransactions(
    transactions: List<TransactionDto>,
    year: Int,
    type: String?,
    search: String,
): List<TransactionDto> {
    val query = search.trim().lowercase()
    return transactions
        .filter { yearOf(it.date) == year }
        .filter { type == null || it.transactionType == type }
        .filter {
            query.isEmpty() ||
                it.ticker.orEmpty().lowercase().contains(query) ||
                it.name.orEmpty().lowercase().contains(query)
        }
        // newest first — the backend's order is not guaranteed
        .sortedByDescending { dayOf(it.date) }
}
