package com.dev.alex.portfolio.ui.selffunding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.dev.alex.portfolio.domain.CurrencyContext
import com.dev.alex.portfolio.domain.Period
import com.dev.alex.portfolio.domain.SfCalc
import com.dev.alex.portfolio.domain.SfRow
import com.dev.alex.portfolio.domain.Tier
import com.dev.alex.portfolio.domain.buildSelfFundingRows
import com.dev.alex.portfolio.domain.cadenceLabel
import com.dev.alex.portfolio.domain.calcSelfFunding
import com.dev.alex.portfolio.domain.coverageLabel
import com.dev.alex.portfolio.domain.currencyContextFor
import com.dev.alex.portfolio.domain.formatCount
import com.dev.alex.portfolio.domain.formatMoney
import com.dev.alex.portfolio.domain.formatNumber
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.paymentsPerYearByTicker
import com.dev.alex.portfolio.domain.prefsFor
import com.dev.alex.portfolio.domain.rankSelfFunding
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.optional
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.ProgressTrack
import com.dev.alex.portfolio.ui.components.Segmented
import com.dev.alex.portfolio.ui.components.TagPill
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

data class SelfFundingData(
    val rows: List<SfRow>,
    /** held positions with no per-share dividend to divide: non-payers, crypto, custom */
    val excluded: Int,
    val ctx: CurrencyContext,
)

class SelfFundingViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<SelfFundingData>(app) {
    override suspend fun load(tracker: StaleTracker): SelfFundingData = coroutineScope {
        val repo = app.repository
        val holdingsCall = async { tracker.take(repo.holdings(portfolioId)) }
        // the rolling calendar: which months each ticker pays in = its cadence
        val calendarCall = async { optional { tracker.take(repo.dividendCalendar(portfolioId)) } }
        val ratesCall = async { tracker.take(repo.fxRates()).toFxRates() }
        val settingsCall = async { optional { tracker.take(repo.settings()) } }

        val rates = ratesCall.await()
        val holdings = holdingsCall.await().map { it.normalize(rates) }
        val (rows, excluded) = buildSelfFundingRows(holdings, paymentsPerYearByTicker(calendarCall.await()))
        SelfFundingData(
            rows = rows,
            excluded = excluded,
            ctx = currencyContextFor(settingsCall.await().prefsFor(portfolioId), holdings, rates),
        )
    }
}

@Composable
fun SelfFundingScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("selfFunding:$portfolioId") { SelfFundingViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    var periodName by rememberSaveable { mutableStateOf(Period.Quarterly.name) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val period = Period.valueOf(periodName)

    val ranked = state.data?.let { rankSelfFunding(it.rows, period) }.orEmpty()
    val won = ranked.count { it.second.reached }

    MobileShell(
        nav = nav,
        title = "Self-Funding",
        subtitle = if (ranked.isEmpty()) nav.portfolioName(portfolioId) else "$won of ${ranked.size} positions · ${period.name.lowercase()}",
    ) {
        PageBody(state, onRefresh = { vm.refresh(force = true) }) { data ->
            if (data.rows.isEmpty()) {
                EmptyState(
                    FpIcons.Target,
                    "No dividend payers in this portfolio",
                    "Self-funding needs a per-share dividend to divide the price by. Every holding here is a non-payer, crypto or a custom asset, so there is no threshold to compute.",
                )
                return@PageBody
            }
            Segmented(Period.entries.map { it.name }, periodName, { periodName = it }, fill = true)
            VSpace(12.dp)
            KpiGrid(ranked, data.ctx)
            VSpace(4.dp)
            ranked.forEach { (row, calc) ->
                SfCard(
                    row = row,
                    calc = calc,
                    period = period,
                    expanded = open == row.ticker,
                    onToggle = { open = if (open == row.ticker) null else row.ticker },
                )
                VSpace(8.dp)
            }
            if (data.excluded > 0) {
                Text(
                    "${data.excluded} holding${if (data.excluded == 1) "" else "s"} excluded — no dividend, crypto or custom",
                    style = FpType.mono(10.5.sp, FontWeight.Normal, Fp.colors.textSubtle),
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun KpiGrid(ranked: List<Pair<SfRow, SfCalc>>, ctx: CurrencyContext) {
    val won = ranked.count { it.second.reached }
    val toGo = ranked.sumOf { it.second.gapShares }
    // the only figure that converts: every row stays in its own currency
    val capital = ranked.sumOf { (row, calc) -> ctx.toBase(calc.gapCost, row.currency) }
    val closest = ranked.filter { !it.second.reached }.maxByOrNull { it.second.progress }
    val tiles = listOf(
        Triple("Self-funding", "$won / ${ranked.size}", Color(Tier.SelfFunding.color)),
        Triple("Shares to go", formatCount(toGo), Fp.colors.text),
        Triple("Capital to close", ctx.money(capital, decimals = 0), Fp.colors.text),
        Triple("Closest", closest?.first?.ticker ?: "—", Semantic.Teal),
    )
    tiles.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            pair.forEach { (label, value, color) ->
                FpCard(padding = 0.dp, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        FpLabel(label, size = 9.5.sp)
                        VSpace(5.dp)
                        Text(value, style = FpType.mono(17.sp, FontWeight.Bold, color), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SfCard(row: SfRow, calc: SfCalc, period: Period, expanded: Boolean, onToggle: () -> Unit) {
    val colors = Fp.colors
    val color = Color(calc.tier.color)
    val done = calc.reached
    val currency = row.currency ?: "USD"
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (done) colors.gainBg else colors.surface)
            .border(1.dp, if (done) color.copy(alpha = 0.33f) else colors.border, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TickerAvatar(row.ticker, size = 26.dp, color = color)
            HSpace(9.dp)
            Column(Modifier.weight(1f)) {
                Text(row.ticker, style = FpType.mono(12.5.sp, FontWeight.Bold, colors.text), maxLines = 1)
                Text(row.name.orEmpty(), color = colors.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TagPill(calc.tier.label, color)
        }
        VSpace(9.dp)
        Row {
            Stat("Needed", formatCount(calc.needed.toDouble()), colors.text, Modifier.weight(1f))
            Stat("Held", formatCount(row.held), colors.text, Modifier.weight(1f))
            Stat(
                if (done) "Buys" else "To go",
                if (done) "${formatNumber(calc.sharesPerPeriod, if (calc.sharesPerPeriod < 10) 2 else 1)} sh" else formatCount(calc.gapShares),
                if (done) color else colors.text,
                Modifier.weight(1f),
            )
            Stat(
                "Cost",
                if (done) "—" else rowMoney(calc.gapCost, currency),
                colors.text,
                Modifier.weight(1.2f),
                TextAlign.End,
            )
        }
        VSpace(9.dp)
        ProgressTrack(calc.progress, done, color, coverageLabel(calc))
        if (expanded) {
            VSpace(12.dp)
            Divider()
            VSpace(10.dp)
            Detail(row, period)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Column(modifier, horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        FpLabel(label, color = Fp.colors.textSubtle, size = 9.sp)
        Text(value, style = FpType.mono(12.5.sp, FontWeight.Bold, color), maxLines = 1)
    }
}

/** The desktop's expanded row, stacked: all three periods, then the reinvest-only projection. */
@Composable
private fun Detail(row: SfRow, period: Period) {
    val colors = Fp.colors
    val currency = row.currency ?: "USD"
    Period.entries.forEach { p ->
        val calc = calcSelfFunding(row, p)
        val active = p == period
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Text(
                p.name.uppercase(),
                color = if (active) Brand.Primary else colors.textMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (calc.reached) "reached · buys ${formatNumber(calc.sharesPerPeriod, 2)} sh"
                else "${formatCount(calc.needed.toDouble())} needed · ${formatCount(calc.gapShares)} to go · ${rowMoney(calc.gapCost, currency)}",
                style = FpType.mono(11.sp, FontWeight.SemiBold, if (calc.reached) Color(calc.tier.color) else colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    VSpace(10.dp)
    val sel = calcSelfFunding(row, period)
    val years = sel.years
    val headline = when {
        sel.reached -> "Reached"
        years == null -> "Needs a first share"
        years > 200 -> "200+ years"
        years < 10 -> "~${formatNumber(years, 1)} years"
        else -> "~${formatNumber(years, 0)} years"
    }
    Text(
        headline,
        style = FpType.mono(20.sp, FontWeight.Bold, if (sel.reached) Color(Tier.SelfFunding.color) else colors.text),
    )
    VSpace(6.dp)
    Text(
        if (sel.reached) {
            "${row.ticker} already pays for a share every ${period.noun} — every payment from here compounds the position instead of closing a gap."
        } else {
            "Reinvest-only: the position grows at its ${formatNumber(sel.yield * 100, 2)}% yield with every dividend reinvested, no new deposits, price and dividend flat — ${formatCount(row.held)} → ${formatCount(sel.needed.toDouble())} sh."
        },
        color = colors.textMuted,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
    )
    VSpace(6.dp)
    val yearly = calcSelfFunding(row, Period.Yearly)
    val perPayment = sel.perPayment
    Text(
        "Yearly threshold ⌈1 / ${formatNumber(sel.yield * 100, 2)}%⌉ = ${formatCount(yearly.needed.toDouble())} sh · " +
            "pays ${cadenceLabel(row.paymentsPerYear).lowercase()}" +
            (if (perPayment != null) " · ${rowMoney(perPayment, currency)} per payment" else ""),
        color = colors.textMuted,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
    )
}

/** Row money in the holding's own currency, whole units above 1,000. */
private fun rowMoney(value: Double, currency: String): String =
    formatMoney(value, currency, decimals = if (kotlin.math.abs(value) >= 1000) 0 else 2)
