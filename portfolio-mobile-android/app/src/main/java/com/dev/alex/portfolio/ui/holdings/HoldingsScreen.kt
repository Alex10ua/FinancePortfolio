package com.dev.alex.portfolio.ui.holdings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.data.api.SessionExpiredException
import com.dev.alex.portfolio.data.api.StatisticsDto
import com.dev.alex.portfolio.domain.CurrencyContext
import com.dev.alex.portfolio.domain.Holding
import com.dev.alex.portfolio.domain.currencyContextFor
import com.dev.alex.portfolio.domain.driftOf
import com.dev.alex.portfolio.domain.formatMoney
import com.dev.alex.portfolio.domain.formatNumber
import com.dev.alex.portfolio.domain.formatPercent
import com.dev.alex.portfolio.domain.formatShares
import com.dev.alex.portfolio.domain.formatSignedMoney
import com.dev.alex.portfolio.domain.formatSignedPercent
import com.dev.alex.portfolio.domain.formatTarget
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.normalizeCurrency
import com.dev.alex.portfolio.domain.portfolioPercents
import com.dev.alex.portfolio.domain.prefsFor
import com.dev.alex.portfolio.domain.sortedByValue
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.optional
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpTextField
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.RangeBar
import com.dev.alex.portfolio.ui.components.StatCell
import com.dev.alex.portfolio.ui.components.TickerAvatar
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.components.driftColor
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.IconAction
import com.dev.alex.portfolio.ui.shell.MobileShell
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import com.dev.alex.portfolio.ui.theme.gainLoss
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class HoldingsData(
    val holdings: List<Holding>,
    val ctx: CurrencyContext,
    val percents: Map<String, Double>,
    val targets: Map<String, Double>,
) {
    val totalValue: Double get() = holdings.sumOf { ctx.toBase(it.totalValue, it.currency) }
    val totalCost: Double get() = holdings.sumOf { ctx.toBase(it.costBasisValue, it.currency) }
    val totalProfit: Double get() = holdings.sumOf { ctx.toBase(it.totalProfit ?: 0.0, it.currency) }
    val totalDividend: Double get() = holdings.sumOf { ctx.toBase((it.dividend ?: 0.0) * it.shareAmount, it.currency) }
}

class HoldingsViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<HoldingsData>(app) {
    private val mutableRanges = MutableStateFlow<Map<String, StatisticsDto>>(emptyMap())
    /** ticker → 52-week high/low, filled in after the list shows */
    val ranges: StateFlow<Map<String, StatisticsDto>> = mutableRanges.asStateFlow()
    private var rangeJob: Job? = null

    override suspend fun load(tracker: StaleTracker): HoldingsData = coroutineScope {
        val holdingsCall = async { tracker.take(app.repository.holdings(portfolioId)) }
        val ratesCall = async { tracker.take(app.repository.fxRates()).toFxRates() }
        val settingsCall = async { optional { tracker.take(app.repository.settings()) } }
        val rates = ratesCall.await()
        val holdings = holdingsCall.await().map { it.normalize(rates) }
        val prefs = settingsCall.await().prefsFor(portfolioId)
        val ctx = currencyContextFor(prefs, holdings, rates)
        HoldingsData(
            holdings = sortedByValue(holdings, ctx),
            ctx = ctx,
            percents = portfolioPercents(holdings, ctx),
            targets = prefs.targets,
        )
    }

    /**
     * One statistics request per stock (the holdings endpoint has no 52-week range), four
     * at a time, each bar appearing as its answer lands. Crypto and custom assets have no
     * Yahoo statistics, so they are not asked for.
     */
    fun loadRanges(holdings: List<Holding>) {
        if (rangeJob?.isActive == true) return
        val wanted = holdings.filter { it.assetType == "STOCK" && it.ticker !in mutableRanges.value }.map { it.ticker }
        if (wanted.isEmpty()) return
        rangeJob = viewModelScope.launch {
            val gate = Semaphore(4)
            try {
                coroutineScope {
                    wanted.map { ticker ->
                        async {
                            gate.withPermit {
                                val stats = optional { app.repository.statistics(ticker).value }
                                if (stats?.fiftyTwoWeekHigh != null && stats.fiftyTwoWeekLow != null) {
                                    mutableRanges.update { it + (ticker to stats) }
                                }
                            }
                        }
                    }.awaitAll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                app.sessionExpired.tryEmit(Unit)
            } catch (e: Exception) {
                // the bars are decoration; a failure just leaves them out
            }
        }
    }
}

@Composable
fun HoldingsScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("holdings:$portfolioId") { HoldingsViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val ranges by vm.ranges.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.data) { state.data?.let { vm.loadRanges(it.holdings) } }

    MobileShell(
        nav = nav,
        title = "Holdings",
        subtitle = nav.portfolioName(portfolioId),
        actions = {
            IconAction(if (searching) FpIcons.X else FpIcons.Search, if (searching) "Close search" else "Search") {
                searching = !searching
                if (!searching) query = ""
            }
        },
    ) {
        PageBody(state, onRefresh = { vm.refresh(force = true) }) { data ->
            if (data.holdings.isEmpty()) {
                EmptyState(FpIcons.Pie, "No holdings yet", "Add a transaction in the web app to get started.")
                return@PageBody
            }
            if (searching) {
                FpTextField(value = query, onValueChange = { query = it }, placeholder = "Filter ticker or name…", icon = FpIcons.Search)
                VSpace(12.dp)
            }
            SummaryCard(data)
            VSpace(14.dp)
            val needle = query.trim().lowercase()
            data.holdings
                .filter { needle.isEmpty() || it.ticker.lowercase().contains(needle) || (it.name ?: "").lowercase().contains(needle) }
                .forEach { holding ->
                    HoldingCard(holding, data, ranges[holding.ticker])
                    VSpace(10.dp)
                }
        }
    }
}

@Composable
private fun SummaryCard(data: HoldingsData) {
    val ctx = data.ctx
    val profit = data.totalProfit
    val cost = data.totalCost
    val value = data.totalValue
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Brand.Primary, Brand.PrimaryHover)))
            .padding(16.dp),
    ) {
        Text(
            "TOTAL VALUE · ${data.holdings.size} POSITIONS",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        )
        VSpace(4.dp)
        Text(ctx.money(value), style = FpType.mono(28.sp, FontWeight.Bold, Color.White))
        VSpace(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(ctx.signedMoney(profit), style = FpType.mono(12.5.sp, FontWeight.SemiBold, Color.White))
            Text(
                formatSignedPercent(if (cost > 0) profit / cost * 100 else null),
                style = FpType.mono(12.5.sp, FontWeight.Bold, Color.White),
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )
            Text(
                "${formatPercent(if (value > 0) data.totalDividend / value * 100 else null)} yield",
                style = FpType.mono(12.5.sp, FontWeight.Normal, Color.White.copy(alpha = 0.85f)),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HoldingCard(h: Holding, data: HoldingsData, range: StatisticsDto?) {
    val colors = Fp.colors
    val ctx = data.ctx
    val currency = h.currency ?: ctx.base
    val profit = h.totalProfit
    FpCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TickerAvatar(h.ticker, size = 32.dp, color = Brand.Primary, assetType = h.assetType)
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                Text(h.ticker, style = FpType.mono(14.sp, FontWeight.Bold, colors.link), maxLines = 1)
                Text(h.name ?: (h.assetType ?: ""), color = colors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(ctx.money(h.totalValue, currency), style = FpType.mono(15.sp, FontWeight.Bold, colors.text))
                if (profit != null) {
                    Text(
                        "${formatSignedPercent(h.totalProfitPercentage)} · ${formatSignedMoney(profit, currency, ctx.display, 0)}",
                        style = FpType.mono(12.sp, FontWeight.Bold, colors.gainLoss(profit)),
                    )
                }
            }
        }
        val low = range?.fiftyTwoWeekLow
        val high = range?.fiftyTwoWeekHigh
        val quoted = h.quoteShareValue
        if (low != null && high != null && quoted != null && high > low) {
            VSpace(12.dp)
            // the range and the quoted price share the provider's currency (pence stays pence)
            val quote = h.quoteCurrency ?: currency
            RangeBar(low, high, quoted, format = { formatRangeValue(it, quote) })
        }
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("Shares", formatShares(h.shareAmount), modifier = Modifier.weight(1f))
            StatCell("Price", formatNumber(h.currentShareValue ?: 0.0, 2), modifier = Modifier.weight(1f))
            val percent = data.percents[h.ticker]
            val target = data.targets[h.ticker]
            val portText = when {
                percent == null -> "—"
                target != null -> "${formatNumber(percent, 1)}%/${formatTarget(target)}%"
                else -> "${formatNumber(percent, 1)}%"
            }
            val portColor = if (percent != null && target != null) colors.driftColor(driftOf(percent, target)) else colors.text
            StatCell("% Port.", portText, portColor, Modifier.weight(1f))
            val dividendYield = h.dividendYield
            StatCell(
                "Yield",
                if (dividendYield != null && dividendYield > 0) formatPercent(dividendYield) else "—",
                if (dividendYield != null && dividendYield > 0) colors.text else colors.textSubtle,
                Modifier.weight(1f),
            )
        }
    }
}

/** Range ends in the quote's own unit: "78.50p" for pence, plain number otherwise. */
private fun formatRangeValue(value: Double, quoteCurrency: String): String =
    if (quoteCurrency == "GBp" || quoteCurrency == "GBx") {
        "${formatNumber(value, 2)}p"
    } else {
        formatMoney(value, normalizeCurrency(quoteCurrency))
    }
