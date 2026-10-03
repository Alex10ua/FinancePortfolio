package com.dev.alex.portfolio.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.Screen
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.data.api.TransactionDto
import com.dev.alex.portfolio.domain.Direction
import com.dev.alex.portfolio.domain.TRANSACTION_TYPES
import com.dev.alex.portfolio.domain.amountOf
import com.dev.alex.portfolio.domain.dayOf
import com.dev.alex.portfolio.domain.directionOf
import com.dev.alex.portfolio.domain.filterTransactions
import com.dev.alex.portfolio.domain.formatMoney
import com.dev.alex.portfolio.domain.formatShares
import com.dev.alex.portfolio.domain.hasQuantity
import com.dev.alex.portfolio.domain.transactionYears
import com.dev.alex.portfolio.domain.yearOf
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpTextField
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.TypeBadge
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.IconAction
import com.dev.alex.portfolio.ui.shell.MobileShell
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import java.time.LocalDate

private const val PAGE_SIZE = 25

class TransactionsViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<List<TransactionDto>>(app) {
    override suspend fun load(tracker: StaleTracker): List<TransactionDto> =
        tracker.take(app.repository.transactions(portfolioId))
}

@Composable
fun TransactionsScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("transactions:$portfolioId") { TransactionsViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val currentYear = remember { LocalDate.now().year }
    var chosenYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }

    val all = state.data.orEmpty()
    val years = transactionYears(all, currentYear)
    // default: this year if it has anything, else the latest year that does
    val year = chosenYear ?: (years.firstOrNull { y -> all.any { yearOf(it.date) == y } } ?: currentYear)
    val rows = filterTransactions(all, year, type, search)
    val pages = maxOf(1, (rows.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val safePage = page.coerceIn(0, pages - 1)

    MobileShell(
        nav = nav,
        title = "Transactions",
        subtitle = "${nav.portfolioName(portfolioId)} · ${rows.size} records",
    ) {
        Box(Modifier.fillMaxSize()) {
            PageBody(state, onRefresh = { vm.refresh(force = true) }) { _ ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PickerButton(
                        icon = FpIcons.Calendar,
                        label = year.toString(),
                        options = years.map { it.toString() },
                        onPick = { picked ->
                            chosenYear = picked.toInt()
                            page = 0
                        },
                    )
                    FpTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            page = 0
                        },
                        placeholder = "Search ticker…",
                        icon = FpIcons.Search,
                        modifier = Modifier.weight(1f),
                    )
                    PickerButton(
                        icon = FpIcons.Filter,
                        label = type ?: "Type",
                        options = listOf("All types") + TRANSACTION_TYPES,
                        onPick = { picked ->
                            type = if (picked == "All types") null else picked
                            page = 0
                        },
                    )
                }
                VSpace(12.dp)
                if (rows.isEmpty()) {
                    EmptyState(
                        FpIcons.Rows,
                        "No transactions",
                        if (type == null && search.isBlank()) "Nothing was booked in $year." else "Nothing in $year matches this filter.",
                    )
                    return@PageBody
                }
                val visible = rows.drop(safePage * PAGE_SIZE).take(PAGE_SIZE)
                FpCard(padding = 0.dp) {
                    visible.forEachIndexed { index, tx ->
                        if (index > 0) Divider()
                        TransactionRow(tx)
                    }
                    Divider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        val from = safePage * PAGE_SIZE + 1
                        val to = from + visible.size - 1
                        Text(
                            "Showing $from–$to of ${rows.size}",
                            color = Fp.colors.textMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconAction(FpIcons.ChevLeft, "Previous page", tint = pagerTint(safePage > 0)) {
                            if (safePage > 0) page = safePage - 1
                        }
                        Text("Page ${safePage + 1} of $pages", color = Fp.colors.text, fontSize = 11.5.sp)
                        IconAction(FpIcons.ChevRight, "Next page", tint = pagerTint(safePage < pages - 1)) {
                            if (safePage < pages - 1) page = safePage + 1
                        }
                    }
                }
                // keeps the pager clear of the FAB at the end of the scroll
                VSpace(64.dp)
            }
            AddFab(
                onClick = { nav.push(Screen.NewTransaction(portfolioId)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(18.dp),
            )
        }
    }
}

/** The mockup's `MobFab`: 48dp primary circle with a plus, bottom right. */
@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Brand.Primary)
            .clickable(onClickLabel = "Add transaction", onClick = onClick)
            .semantics { contentDescription = "Add transaction" },
    ) {
        FpIcon(FpIcons.Plus, size = 20.dp, tint = Color.White)
    }
}

@Composable
private fun pagerTint(enabled: Boolean) = if (enabled) Fp.colors.textMuted else Fp.colors.border

@Composable
private fun TransactionRow(tx: TransactionDto) {
    val colors = Fp.colors
    val direction = directionOf(tx.transactionType)
    val tone = when (direction) {
        Direction.In -> colors.gain
        Direction.Out -> colors.loss
        Direction.Tax -> colors.over
        Direction.Neutral -> colors.text
    }
    val sign = when (direction) {
        Direction.In -> "+"
        Direction.Out -> "-"
        else -> ""
    }
    val currency = tx.currency ?: "USD"
    val amount = amountOf(tx)
    val ticker = tx.ticker?.takeIf { it.isNotBlank() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(tx.transactionType ?: "—")
                HSpace(8.dp)
                Text(
                    ticker ?: "—",
                    color = if (ticker == null) colors.textSubtle else colors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val detail = buildString {
                append(dayOf(tx.date))
                if (hasQuantity(tx)) {
                    append(" · ")
                    append(formatShares(tx.quantity))
                    append(" × ")
                    append(formatMoney(tx.price, currency))
                }
            }
            Text(
                detail,
                style = FpType.mono(11.sp, FontWeight.Normal, colors.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        HSpace(10.dp)
        Text(
            if (amount == null) "—" else "$sign${formatMoney(kotlin.math.abs(amount), currency)}",
            style = FpType.mono(13.sp, FontWeight.SemiBold, tone),
            maxLines = 1,
        )
    }
}

/** Bordered button that opens a short list, the mockup's year and type pickers. */
@Composable
private fun PickerButton(icon: ImageVector, label: String, options: List<String>, onPick: (String) -> Unit) {
    val colors = Fp.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(horizontal = 10.dp),
        ) {
            FpIcon(icon, size = 14.dp)
            Text(label, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            FpIcon(FpIcons.ChevDown, size = 14.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}
