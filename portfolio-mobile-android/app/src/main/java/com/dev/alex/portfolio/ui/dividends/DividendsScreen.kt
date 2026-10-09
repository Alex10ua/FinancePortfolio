package com.dev.alex.portfolio.ui.dividends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.domain.CurrencyContext
import com.dev.alex.portfolio.domain.DividendIncome
import com.dev.alex.portfolio.domain.IncomeBar
import com.dev.alex.portfolio.domain.LastBatch
import com.dev.alex.portfolio.domain.MonthKey
import com.dev.alex.portfolio.domain.TopPayer
import com.dev.alex.portfolio.domain.currencyContextFor
import com.dev.alex.portfolio.domain.formatShares
import com.dev.alex.portfolio.domain.formatSignedPercent
import com.dev.alex.portfolio.domain.incomeByMonth
import com.dev.alex.portfolio.domain.lastBatch
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.prefsFor
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.domain.topPayers
import com.dev.alex.portfolio.domain.yearlyProjection
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.optional
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.BarChart
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.Segmented
import com.dev.alex.portfolio.ui.components.TickerAvatar
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.MobileShell
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import com.dev.alex.portfolio.ui.theme.Semantic
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

data class DividendsData(
    val ctx: CurrencyContext,
    /** next-twelve-month projection, base currency */
    val yearly: Double,
    val income: DividendIncome,
    val now: MonthKey,
    val top: List<TopPayer>,
    val batch: LastBatch?,
)

class DividendsViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<DividendsData>(app) {
    override suspend fun load(tracker: StaleTracker): DividendsData = coroutineScope {
        val repo = app.repository
        val dividendsCall = async { tracker.take(repo.dividends(portfolioId)) }
        val holdingsCall = async { tracker.take(repo.holdings(portfolioId)) }
        val ratesCall = async { tracker.take(repo.fxRates()).toFxRates() }
        val settingsCall = async { optional { tracker.take(repo.settings()) } }
        val transactionsCall = async { optional { tracker.take(repo.transactions(portfolioId)) }.orEmpty() }

        val rates = ratesCall.await()
        val holdings = holdingsCall.await().map { it.normalize(rates) }
        val ctx = currencyContextFor(settingsCall.await().prefsFor(portfolioId), holdings, rates)
        val dividends = dividendsCall.await()
        val now = MonthKey.of(LocalDate.now())
        DividendsData(
            ctx = ctx,
            yearly = yearlyProjection(dividends, ctx),
            income = DividendIncome(incomeByMonth(dividends, ctx), now),
            now = now,
            top = topPayers(dividends, ctx, holdings.filter { it.shareAmount > 0 }.map { it.ticker }.toSet()),
            batch = lastBatch(transactionsCall.await(), holdings, ctx, now.year),
        )
    }
}

private data class Kpi(val label: String, val icon: ImageVector, val accent: Color, val perYear: Int, val decimals: Int, val sub: String)

private val KPIS = listOf(
    Kpi("Yearly", FpIcons.Trending, Brand.Primary, 1, 2, "Next 12 months"),
    Kpi("Monthly avg", FpIcons.Calendar, Semantic.Teal, 12, 2, "Projected"),
    Kpi("Daily avg", FpIcons.Coins, Semantic.Success, 365, 2, "Calendar daily"),
    Kpi("Hourly avg", FpIcons.Sparkle, Semantic.Purple, 8760, 4, "While you sleep"),
)

@Composable
fun DividendsScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("dividends:$portfolioId") { DividendsViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    val data = state.data

    MobileShell(
        nav = nav,
        title = "Dividends",
        subtitle = data?.let { "Annual projection ${it.ctx.money(it.yearly, decimals = 0)}" },
    ) {
        PageBody(state, onRefresh = { vm.refresh(force = true) }) { loaded ->
            if (loaded.yearly <= 0 && loaded.income.years().isEmpty() && loaded.top.isEmpty()) {
                EmptyState(FpIcons.Coins, "No dividend data", "Nothing in this portfolio has paid or is projected to pay a dividend yet.")
                return@PageBody
            }
            KpiGrid(loaded)
            BatchNote(loaded)
            IncomeCard(loaded)
            if (loaded.top.isNotEmpty()) {
                VSpace(12.dp)
                TopPayersCard(loaded)
            }
        }
    }
}

@Composable
private fun KpiGrid(data: DividendsData) {
    val colors = Fp.colors
    KPIS.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            pair.forEach { kpi ->
                val delta = data.batch?.yearlyDelta?.div(kpi.perYear)?.takeIf { it != 0.0 }
                FpCard(padding = 0.dp, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(kpi.accent.copy(alpha = if (colors.isDark) 0.15f else 0.10f)),
                            ) {
                                FpIcon(kpi.icon, size = 12.dp, tint = kpi.accent)
                            }
                            HSpace(7.dp)
                            FpLabel(kpi.label)
                        }
                        VSpace(8.dp)
                        Text(
                            data.ctx.money(data.yearly / kpi.perYear, decimals = kpi.decimals),
                            style = FpType.number(18.sp, FontWeight.SemiBold, colors.text),
                            maxLines = 1,
                        )
                        if (delta != null) {
                            Text(
                                "(${data.ctx.signedMoney(delta, decimals = kpi.decimals)})",
                                style = FpType.number(12.sp, FontWeight.SemiBold, if (delta < 0) Semantic.Danger else Semantic.Success),
                                maxLines = 1,
                            )
                        }
                        Text(
                            kpi.sub,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchNote(data: DividendsData) {
    val batch = data.batch
    val colors = Fp.colors
    if (batch == null || batch.yearlyDelta == 0.0) {
        VSpace(4.dp)
        return
    }
    Row(modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 14.dp)) {
        FpIcon(FpIcons.Info, size = 13.dp, modifier = Modifier.padding(top = 2.dp))
        HSpace(8.dp)
        Column {
            Text(
                "( ) = change from your last transaction batch, ${batch.date}",
                color = colors.textMuted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
            VSpace(6.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                batch.moves.forEach { move ->
                    val tone = if (move.sell) colors.loss else colors.gain
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (move.sell) colors.lossBg else colors.gainBg)
                            .padding(start = 3.dp, end = 7.dp, top = 3.dp, bottom = 3.dp),
                    ) {
                        TickerAvatar(move.ticker, size = 14.dp, color = tone)
                        HSpace(5.dp)
                        Text(
                            "${if (move.sell) "−" else "+"}${formatShares(move.quantity)} ${move.ticker}",
                            style = FpType.mono(11.sp, FontWeight.SemiBold, tone),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeCard(data: DividendsData) {
    val colors = Fp.colors
    var view by rememberSaveable { mutableStateOf("Year") }
    val years = data.income.years().sortedDescending()
    var year by rememberSaveable { mutableStateOf(data.now.year) }
    var selected by remember(view, year) { mutableStateOf<Int?>(null) }

    val bars: List<IncomeBar> = when (view) {
        "Quarter" -> data.income.byQuarter(year)
        "Month" -> data.income.byMonth(year)
        else -> data.income.byYear()
    }
    val best = bars.filter { it.amount > 0 }.maxByOrNull { it.amount }
    val title = when (view) {
        "Quarter" -> "Income by quarter"
        "Month" -> "Income by month · $year"
        else -> "Income by year"
    }
    val subtitle = when (view) {
        "Quarter" -> if (best != null) "$year · ${best.label} highest" else "$year · nothing paid yet"
        "Month" -> if (best != null) "${best.label} was the record month" else "Nothing paid in $year yet"
        else -> "${bars.size}-year history · all currencies in ${data.ctx.base}"
    }
    val color = if (view == "Quarter") Semantic.Teal else Brand.Primary

    FpCard(padding = 12.dp) {
        Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp, bottom = 10.dp))
        Segmented(listOf("Year", "Quarter", "Month"), view, { view = it }, fill = true)
        if (view != "Year" && years.size > 1) {
            VSpace(8.dp)
            Segmented(years.take(6).map { it.toString() }, year.toString(), { year = it.toInt() })
        }
        VSpace(10.dp)
        BarChart(
            values = bars.map { it.amount },
            labels = bars.map { it.label },
            color = color,
            selectedIndex = selected,
            valueLabel = { data.ctx.money(it, decimals = 0) },
            onSelect = { selected = it },
        )
        val pick = selected?.let { bars.getOrNull(it) }
        VSpace(6.dp)
        if (pick == null) {
            Text("Tap a bar for its total and the change on a year earlier", color = colors.textSubtle, fontSize = 11.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${pick.title}${pick.tag?.let { " $it" } ?: ""} · ${data.ctx.money(pick.amount)}",
                    style = FpType.number(12.sp, FontWeight.SemiBold, colors.text),
                )
                val change = pick.change
                if (change != null) {
                    HSpace(6.dp)
                    Text(
                        "${formatSignedPercent(change.pct, 1)} vs ${change.vs}${change.months?.let { " ($it)" } ?: ""}",
                        style = FpType.number(11.5.sp, FontWeight.Medium, if (change.pct < 0) colors.loss else colors.gain),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopPayersCard(data: DividendsData) {
    val colors = Fp.colors
    val top = data.top.firstOrNull()?.amount ?: 0.0
    FpCard(padding = 12.dp) {
        Text("Top dividend payers", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("All time · still held", color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp, bottom = 12.dp))
        data.top.forEachIndexed { index, payer ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Text("#${index + 1}", style = FpType.mono(11.sp, FontWeight.Normal, colors.textSubtle), modifier = Modifier.width(24.dp))
                TickerAvatar(payer.ticker, size = 24.dp, color = Brand.Primary)
                HSpace(10.dp)
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(payer.ticker, color = colors.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(data.ctx.money(payer.amount, decimals = 0), style = FpType.number(12.sp, FontWeight.SemiBold, colors.text))
                    }
                    VSpace(4.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceMuted),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (top > 0) (payer.amount / top).toFloat().coerceIn(0f, 1f) else 0f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Brand.Primary),
                        )
                    }
                }
            }
        }
    }
}
