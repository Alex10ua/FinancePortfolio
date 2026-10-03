package com.dev.alex.portfolio.ui.portfolios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.Screen
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.domain.FxRates
import com.dev.alex.portfolio.domain.NetWorth
import com.dev.alex.portfolio.domain.PORTFOLIO_PALETTE
import com.dev.alex.portfolio.domain.PortfolioSummary
import com.dev.alex.portfolio.domain.formatMoney
import com.dev.alex.portfolio.domain.formatNumber
import com.dev.alex.portfolio.domain.formatSignedPercent
import com.dev.alex.portfolio.domain.netWorth
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.prefsFor
import com.dev.alex.portfolio.domain.summarizePortfolio
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.optional
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.AllocBar
import com.dev.alex.portfolio.ui.components.Avatar
import com.dev.alex.portfolio.ui.components.ColorSquare
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.components.FpTextField
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.LegendItem
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.MobileShell
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import com.dev.alex.portfolio.ui.theme.gainLoss
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class PortfolioListData(val worth: NetWorth, val rates: FxRates)

class PortfolioListViewModel(app: AppContainer) : LoadViewModel<PortfolioListData>(app) {
    override suspend fun load(tracker: StaleTracker): PortfolioListData = coroutineScope {
        val portfolios = async { tracker.take(app.repository.portfolios()) }
        val rates = async { tracker.take(app.repository.fxRates()).toFxRates() }
        val settings = async { optional { tracker.take(app.repository.settings()) } }
        val fx = rates.await()
        val saved = settings.await()
        val summaries = portfolios.await().map { portfolio ->
            async {
                val holdings = optional { tracker.take(app.repository.holdings(portfolio.portfolioId)) }.orEmpty()
                summarizePortfolio(
                    portfolioId = portfolio.portfolioId,
                    name = portfolio.portfolioName,
                    holdings = holdings.map { it.normalize(fx) },
                    prefs = saved.prefsFor(portfolio.portfolioId),
                    rates = fx,
                )
            }
        }.awaitAll()
        PortfolioListData(netWorth(summaries, fx), fx)
    }
}

@Composable
fun PortfolioListScreen(nav: ShellNav) {
    val vm = screenViewModel("portfolios") { PortfolioListViewModel(it) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }

    val worth = state.data?.worth
    LaunchedEffect(worth) {
        if (worth != null) nav.rememberPortfolioOrder(worth.items.map { it.portfolioId })
    }

    MobileShell(
        nav = nav,
        title = "All Portfolios",
        subtitle = worth?.let { "${it.items.size} portfolios · ${it.assetCount} assets · ${it.currency}" },
    ) {
        PageBody(state, onRefresh = { vm.refresh(force = true) }) { data ->
            if (data.worth.items.isEmpty()) {
                EmptyState(
                    icon = FpIcons.Folder,
                    title = "No portfolios yet",
                    body = "Create a portfolio in the web app — it shows up here on the next refresh.",
                )
                return@PageBody
            }
            NetWorthCard(data.worth, data.rates)
            VSpace(12.dp)
            FpTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = "Search portfolios…",
                icon = FpIcons.Search,
            )
            VSpace(12.dp)
            val query = search.trim().lowercase()
            data.worth.items.forEachIndexed { index, item ->
                if (query.isEmpty() || item.name.lowercase().contains(query)) {
                    PortfolioCard(item, Color(PORTFOLIO_PALETTE[index % PORTFOLIO_PALETTE.size])) {
                        nav.open(Screen.Dashboard(item.portfolioId))
                    }
                    VSpace(10.dp)
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(worth: NetWorth, rates: FxRates) {
    val colors = Fp.colors
    val up = worth.profit >= 0
    FpCard {
        FpLabel("Net worth · All portfolios")
        VSpace(4.dp)
        Text(formatMoney(worth.total, worth.currency), style = FpType.number(26.sp, FontWeight.Bold, colors.text))
        VSpace(3.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FpIcon(if (up) FpIcons.ArrowUp else FpIcons.ArrowDown, size = 12.dp, tint = colors.gainLoss(worth.profit))
            HSpace(4.dp)
            Text(
                "${if (up) "+" else "-"}${formatMoney(kotlin.math.abs(worth.profit), worth.currency)} · ${formatSignedPercent(worth.profitPct)}",
                style = FpType.number(12.sp, FontWeight.SemiBold, colors.gainLoss(worth.profit)),
            )
            HSpace(5.dp)
            Text("unrealized P&L", color = colors.textMuted, fontSize = 12.sp)
        }
        VSpace(14.dp)
        Divider()
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                FpLabel("Cost basis")
                VSpace(3.dp)
                Text(formatMoney(worth.totalCost, worth.currency), style = FpType.number(14.sp, FontWeight.SemiBold, colors.text))
                Text("across ${worth.items.size} portfolios", color = colors.textMuted, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f)) {
                FpLabel("Best performer")
                val best = worth.best
                if (best == null) {
                    VSpace(3.dp)
                    Text("—", color = colors.textSubtle, fontSize = 13.sp)
                } else {
                    val index = worth.items.indexOf(best)
                    VSpace(3.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(best.name.ifBlank { "?" }, size = 18.dp, color = Color(PORTFOLIO_PALETTE[index % PORTFOLIO_PALETTE.size]))
                        HSpace(6.dp)
                        Text(
                            best.name,
                            color = colors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        formatSignedPercent(best.profitPct),
                        style = FpType.number(11.sp, FontWeight.SemiBold, colors.gainLoss(best.profitPct)),
                    )
                }
            }
        }
        VSpace(14.dp)
        FpLabel("Allocation by portfolio")
        VSpace(8.dp)
        val weights = worth.items.mapIndexed { index, item ->
            Triple(item, worth.weightOf(item, rates), Color(PORTFOLIO_PALETTE[index % PORTFOLIO_PALETTE.size]))
        }
        AllocBar(weights.map { it.second to it.third }, height = 8.dp)
        VSpace(10.dp)
        weights.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                pair.forEach { (item, share, color) ->
                    LegendItem(color, item.name, "${formatNumber(share, 1)}%", Modifier.weight(1f))
                }
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun PortfolioCard(item: PortfolioSummary, color: Color, onClick: () -> Unit) {
    val colors = Fp.colors
    FpCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.name.ifBlank { "?" }, size = 32.dp, color = color)
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                Text(item.name, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.assetCount} assets · ${item.currency}", color = colors.textMuted, fontSize = 11.sp)
            }
            FpIcon(FpIcons.ChevRight, size = 14.dp, tint = colors.textSubtle)
        }
        VSpace(12.dp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatMoney(item.value, item.currency),
                style = FpType.number(20.sp, FontWeight.SemiBold, colors.text),
                modifier = Modifier.weight(1f),
            )
            Text(formatSignedPercent(item.profitPct), style = FpType.number(12.sp, FontWeight.SemiBold, colors.gainLoss(item.profit)))
        }
        VSpace(3.dp)
        Row {
            Text(
                "Cost basis ${formatMoney(item.cost, item.currency)}",
                style = FpType.number(11.5.sp, FontWeight.Normal, colors.textMuted),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${if (item.profit >= 0) "+" else "-"}${formatMoney(kotlin.math.abs(item.profit), item.currency)}",
                style = FpType.number(11.5.sp, FontWeight.SemiBold, colors.gainLoss(item.profit)),
            )
        }
        VSpace(12.dp)
        AllocBar(item.allocation.map { it.weight to Color(it.color) }, height = 6.dp)
        VSpace(7.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            item.allocation.forEach { segment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorSquare(Color(segment.color), size = 6.dp)
                    HSpace(4.dp)
                    Text(
                        "${segment.label} ${formatNumber(segment.weight, 0)}%",
                        color = colors.textMuted,
                        fontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}
