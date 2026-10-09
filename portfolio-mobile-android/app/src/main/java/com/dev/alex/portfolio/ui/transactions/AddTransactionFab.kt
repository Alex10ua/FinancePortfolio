package com.dev.alex.portfolio.ui.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dev.alex.portfolio.Screen
import com.dev.alex.portfolio.ui.components.FpFab
import com.dev.alex.portfolio.ui.shell.ShellNav

/**
 * Opens the add-transaction form over the current page, so back returns there. On
 * Transactions (as in the mockup) and on the Dashboard, the mobile stand-in for the web
 * dashboard's "New Transaction" header button.
 */
@Composable
fun AddTransactionFab(portfolioId: String, nav: ShellNav, modifier: Modifier = Modifier) {
    FpFab("Add transaction", modifier) { nav.push(Screen.NewTransaction(portfolioId)) }
}
