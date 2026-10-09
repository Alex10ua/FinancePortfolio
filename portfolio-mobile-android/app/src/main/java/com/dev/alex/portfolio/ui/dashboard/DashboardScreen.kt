package com.dev.alex.portfolio.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.data.api.CashHoldingDto
import com.dev.alex.portfolio.domain.ASSET_COLORS
import com.dev.alex.portfolio.domain.CHART_RANGES
import com.dev.alex.portfolio.domain.CurrencyContext
import com.dev.alex.portfolio.domain.DashboardStats
import com.dev.alex.portfolio.domain.Holding
import com.dev.alex.portfolio.domain.ValuePoint
import com.dev.alex.portfolio.domain.assetTypeCounts
import com.dev.alex.portfolio.domain.barScaleMax
import com.dev.alex.portfolio.domain.currencyContextFor
import com.dev.alex.portfolio.domain.currencySymbol
import com.dev.alex.portfolio.domain.dashboardStats
import com.dev.alex.portfolio.domain.formatNumber
import com.dev.alex.portfolio.domain.formatPercent
import com.dev.alex.portfolio.domain.formatShares
import com.dev.alex.portfolio.domain.formatSignedPercent
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.portfolioPercents
import com.dev.alex.portfolio.domain.prefsFor
import com.dev.alex.portfolio.domain.sortedByValue
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.domain.valueSeries
import com.dev.alex.portfolio.domain.visibleSeries
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.optional
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.AreaChart
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpChip
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.Segmented
import com.dev.alex.portfolio.ui.components.StatCell
import com.dev.alex.portfolio.ui.components.TargetWeight
import com.dev.alex.portfolio.ui.components.TickerAvatar
import com.dev.alex.portfolio.ui.components.Trend
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.MobileShell
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import com.dev.alex.portfolio.ui.theme.gainLoss
import com.dev.alex.portfolio.ui.transactions.AddTransactionFab
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class DashboardData(
    /** largest value first */
    val holdings: List<Holding>,
    val ctx: CurrencyContext,
    val stats: DashboardStats,
    val percents: Map<String, Double>,
    val targets: Map<String, Double>,
    val series: List<ValuePoint>,
    val defaultRange: String,
    val cash: List<CashHoldingDto>,
    /** DEPOSIT − WITHDRAWAL per currency, shown only when no manual cash is entered */
    val cashBalance: Map<String, Double>,
    /** shared scale of the weight bars without a target */
    val barScale: Double,
)

class DashboardViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<DashboardData>(app) {
    override suspend fun load(tracker: StaleTracker): DashboardData = coroutineScope {
        val repo = app.repository
        val holdingsCall = async { tracker.take(repo.holdings(portfolioId)) }
        val ratesCall = async { tracker.take(repo.fxRates()).toFxRates() }
        val settingsCall = async { optional { tracker.take(repo.settings()) } }
        val historyCall = async { optional { tracker.take(repo.portfolioHistory(portfolioId)) }.orEmpty() }
        val realizedCall = async { optional { tracker.take(repo.realizedPnl(portfolioId)) }.orEmpty() }
        val cashCall = async { optional { tracker.take(repo.cashHoldings(portfolioId)) }.orEmpty() }
        val balanceCall = async { optional { tracker.take(repo.cashBalance(portfolioId)) }.orEmpty() }

        val rates = ratesCall.await()
        val holdings = holdingsCall.await().map { it.normalize(rates) }
        val prefs = settingsCall.await().prefsFor(portfolioId)
        val ctx = currencyContextFor(prefs, holdings, rates)
        val cash = cashCall.await()
        val percents = portfolioPercents(holdings, ctx)
        // a target on a ticker no longer held is stale (the backend drops it on sale)
        val targets = prefs.targets.filterKeys { ticker -> holdings.any { it.ticker == ticker } }
        DashboardData(
            holdings = sortedByValue(holdings, ctx),
            ctx = ctx,
            stats = dashboardStats(holdings, ctx, cash, realizedCall.await()),
            percents = percents,
            targets = targets,
            series = valueSeries(historyCall.await(), ctx),
            defaultRange = prefs.chartRange?.takeIf { it in CHART_RANGES } ?: "YTD",
            cash = cash.filter { it.amount != 0.0 },
            cashBalance = balanceCall.await().mapNotNull { (k, v) -> v?.takeIf { it != 0.0 }?.let { k to it } }.toMap(),
            barScale = barScaleMax(percents.values, targets.values),
        )
    }
}

@Composable
fun DashboardScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("dashboard:$portfolioId") { DashboardViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val data = state.data

    MobileShell(
        nav = nav,
        title = nav.portfolioName(portfolioId),
        subtitle = data?.let { "${it.holdings.size} assets · in ${it.ctx.base}" },
    ) {
        Box(Modifier.fillMaxSize()) {
            PageBody(state, onRefresh = { vm.refresh(force = true) }) { loaded ->
                if (loaded.holdings.isEmpty()) {
                    EmptyState(FpIcons.Pie, "No holdings yet", "Tap + to add your first transaction.")
                    return@PageBody
                }
                // the mockup's (and the desktop's) order: KPIs → cash → chart → holdings
                HeroCard(loaded)
                VSpace(12.dp)
                CashCard(loaded)
                if (loaded.series.size > 1) {
                    ValueChartCard(loaded)
                    VSpace(12.dp)
                }
                HoldingsCard(loaded)
                // keeps the last card clear of the FAB at the end of the scroll
                VSpace(64.dp)
            }
            AddTransactionFab(
                portfolioId = portfolioId,
                nav = nav,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(18.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(data: DashboardData) {
    val colors = Fp.colors
    val stats = data.stats
    val ctx = data.ctx
    FpCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FpLabel("Total value", modifier = Modifier.weight(1f))
            Trend(stats.dayChangePct, suffix = "today")
        }
        VSpace(4.dp)
        Text(ctx.money(stats.totalWithCash), style = FpType.number(28.sp, FontWeight.Bold, colors.text))
        if (stats.cash != 0.0) {
            Text("incl. ${ctx.money(stats.cash)} cash", color = colors.textMuted, fontSize = 11.sp)
        }
        VSpace(2.dp)
        Row {
            Text("Total P&L ", color = colors.textMuted, fontSize = 12.sp)
            Text(
                "${ctx.signedMoney(stats.totalProfit)} (${formatSignedPercent(stats.totalProfitPct)})",
                style = FpType.number(12.sp, FontWeight.SemiBold, colors.gainLoss(stats.totalProfit)),
            )
        }
        if (stats.realized != 0.0) {
            Text("incl. ${ctx.signedMoney(stats.realized)} realized", color = colors.textSubtle, fontSize = 11.sp)
        }
        VSpace(12.dp)
        Divider()
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                FpLabel("Cost basis")
                VSpace(3.dp)
                Text(ctx.money(stats.totalCost), style = FpType.number(13.sp, FontWeight.SemiBold, colors.text), maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                FpLabel("Avg yield")
                VSpace(3.dp)
                Text(formatPercent(stats.avgYield), style = FpType.number(13.sp, FontWeight.SemiBold, colors.text))
            }
            Column(Modifier.weight(1f)) {
                FpLabel("Holdings")
                VSpace(3.dp)
                Text(stats.holdingsCount.toString(), style = FpType.number(13.sp, FontWeight.SemiBold, colors.text))
            }
        }
    }
}

@Composable
private fun ValueChartCard(data: DashboardData) {
    val colors = Fp.colors
    var range by rememberSaveable { mutableStateOf(data.defaultRange) }
    var selected by remember(range) { mutableStateOf<Int?>(null) }
    val points = visibleSeries(data.series, range)
    FpCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Portfolio value over time",
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text("in ${data.ctx.base}", color = colors.textMuted, fontSize = 10.5.sp)
        }
        VSpace(8.dp)
        Segmented(CHART_RANGES, range, { range = it }, fill = true)
        VSpace(6.dp)
        val shown = selected?.let { points.getOrNull(it) } ?: points.lastOrNull()
        if (shown != null) {
            Text(
                "${shown.month.title} · ${data.ctx.money(shown.value)}",
                style = FpType.number(11.sp, FontWeight.Medium, colors.textMuted),
            )
        }
        VSpace(4.dp)
        AreaChart(
            values = points.map { it.value },
            selectedIndex = selected,
            onSelect = { selected = it },
        )
    }
}

@Composable
private fun HoldingsCard(data: DashboardData) {
    val colors = Fp.colors
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val counts = assetTypeCounts(data.holdings)
    val rows = if (filter == "ALL") data.holdings else data.holdings.filter { (it.assetType ?: "CUSTOM") == filter }

    FpCard(padding = 0.dp) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
            Text("Holdings", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Sorted by total value", color = colors.textMuted, fontSize = 11.sp)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            FpChip("ALL", active = filter == "ALL", count = data.holdings.size) { filter = "ALL" }
            counts.forEach { (type, count) ->
                FpChip(type, active = filter == type, count = count) { filter = type }
            }
        }
        rows.forEach { holding ->
            Divider()
            HoldingRow(holding, data)
        }
    }
}

@Composable
private fun HoldingRow(h: Holding, data: DashboardData) {
    val colors = Fp.colors
    val ctx = data.ctx
    val currency = h.currency ?: ctx.base
    val percent = data.percents[h.ticker]
    val target = data.targets[h.ticker]
    val profit = h.totalProfit
    Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TickerAvatar(h.ticker, size = 28.dp, color = avatarColor(h.assetType), assetType = h.assetType)
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                Text(h.ticker, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    h.name ?: (h.assetType ?: ""),
                    color = colors.textMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(ctx.money(h.totalValue, currency), style = FpType.mono(13.sp, FontWeight.SemiBold, colors.text))
                if (profit != null) {
                    Text(
                        "${ctx.signedMoney(profit, currency)} · ${formatSignedPercent(h.totalProfitPercentage)}",
                        style = FpType.mono(11.sp, FontWeight.SemiBold, colors.gainLoss(profit)),
                    )
                }
            }
        }
        // every desktop column, three to a row
        VSpace(10.dp)
        val income = (h.dividend ?: 0.0) * h.shareAmount
        val dividendYield = h.dividendYield
        val yieldOnCost = h.dividendYieldOnCost
        val day = h.dayChangePercent
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("Shares", formatShares(h.shareAmount), modifier = Modifier.weight(1f))
            StatCell(
                "Avg price",
                h.costPerShare?.let { ctx.money(it, currency) } ?: "—",
                if (h.costPerShare == null) colors.textSubtle else colors.text,
                Modifier.weight(1f),
            )
            // yearly income of the whole position, as the desktop Dividends column
            StatCell(
                "Dividends",
                if (income > 0) ctx.money(income, currency) else "—",
                if (income > 0) colors.text else colors.textSubtle,
                Modifier.weight(1f),
            )
        }
        VSpace(9.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(
                "Yield",
                if (dividendYield != null && dividendYield > 0) formatPercent(dividendYield) else "—",
                if (dividendYield != null && dividendYield > 0) colors.text else colors.textSubtle,
                Modifier.weight(1f),
            )
            StatCell(
                "Yield on cost",
                if (yieldOnCost != null && yieldOnCost > 0) formatPercent(yieldOnCost) else "—",
                if (yieldOnCost != null && yieldOnCost > 0) colors.text else colors.textSubtle,
                Modifier.weight(1f),
            )
            StatCell(
                "Daily change",
                formatSignedPercent(day),
                if (day == null) colors.textSubtle else colors.gainLoss(day),
                Modifier.weight(1f),
            )
        }
        if (percent != null) {
            VSpace(10.dp)
            TargetWeight("% of portfolio", percent, target, data.barScale)
        }
    }
}

private fun avatarColor(assetType: String?): Color =
    if (assetType == null || assetType == "STOCK") Brand.Primary else Color(ASSET_COLORS[assetType] ?: 0xFF94A3B8)

@Composable
private fun CashCard(data: DashboardData) {
    val colors = Fp.colors
    val ctx = data.ctx
    // manual cash is what Total Value counts; the derived balance is the fallback view
    val manual = data.cash.isNotEmpty()
    val entries: List<Pair<String, Double>> = if (manual) {
        data.cash.map { it.currency to it.amount }
    } else {
        data.cashBalance.entries.map { it.key to it.value }
    }
    if (entries.isEmpty()) return
    val inBase = entries.map { (currency, amount) -> ctx.toBase(amount, currency) }
    val total = inBase.filter { it > 0 }.sum()

    FpCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (manual) "Cash position" else "Cash balance",
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            FpLabel("${entries.size} ${if (entries.size == 1) "currency" else "currencies"}")
        }
        if (!manual) {
            Text("Deposits minus withdrawals", color = colors.textSubtle, fontSize = 10.5.sp)
        }
        VSpace(10.dp)
        entries.zip(inBase).chunked(3).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                chunk.forEach { (entry, base) ->
                    val (currency, amount) = entry
                    val share = if (total > 0 && base > 0) base / total * 100 else 0.0
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${currencySymbol(currency).trim()} ${formatNumber(amount, 2)}",
                            style = FpType.number(13.sp, FontWeight.SemiBold, if (amount < 0) colors.loss else colors.text),
                            maxLines = 1,
                        )
                        Text(
                            "$currency · ${formatNumber(share, 0)}%",
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(colors.border),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((share / 100).toFloat().coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Brand.Primary),
                            )
                        }
                    }
                }
                repeat(3 - chunk.size) { Box(Modifier.weight(1f)) }
            }
        }
        if (manual) {
            Text(
                "Entered by hand · counted in Total Value, not in the chart",
                color = colors.textSubtle,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Start,
            )
        }
    }
    // the gap travels with the card: no cash, no card, no double gap above Holdings
    VSpace(12.dp)
}
