package com.dev.alex.portfolio.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.data.api.StatisticsDto
import com.dev.alex.portfolio.domain.Holding
import com.dev.alex.portfolio.domain.RECOMMENDATION_LABEL
import com.dev.alex.portfolio.domain.STAT_GROUP_ORDER
import com.dev.alex.portfolio.domain.StatGroup
import com.dev.alex.portfolio.domain.StatKind
import com.dev.alex.portfolio.domain.StatMoney
import com.dev.alex.portfolio.domain.StatSubhead
import com.dev.alex.portfolio.domain.StatValue
import com.dev.alex.portfolio.domain.formatNumber
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.statFraction
import com.dev.alex.portfolio.domain.statNumber
import com.dev.alex.portfolio.domain.statPercent
import com.dev.alex.portfolio.domain.statisticsGroups
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.ErrorState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.LoadingSkeleton
import com.dev.alex.portfolio.ui.components.Segmented
import com.dev.alex.portfolio.ui.components.TickerAvatar
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.MobileShell
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val DASH = "—"

/** The tickers that have a Yahoo snapshot at all: stocks and coins, never custom assets. */
class StatisticsHoldingsViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<List<Holding>>(app) {
    override suspend fun load(tracker: StaleTracker): List<Holding> = coroutineScope {
        val holdings = async { tracker.take(app.repository.holdings(portfolioId)) }
        val rates = tracker.take(app.repository.fxRates()).toFxRates()
        holdings.await()
            .map { it.normalize(rates) }
            .filter { it.assetType == "STOCK" || it.assetType == "CRYPTO" }
            .sortedBy { it.ticker }
    }
}

/** null [stats] = nothing stored for the ticker yet (the endpoint's 404). */
data class TickerStatistics(val stats: StatisticsDto?)

class TickerStatisticsViewModel(app: AppContainer, private val ticker: String) : LoadViewModel<TickerStatistics>(app) {
    override suspend fun load(tracker: StaleTracker): TickerStatistics =
        TickerStatistics(tracker.take(app.repository.statistics(ticker)))
}

/**
 * Yahoo key statistics for one ticker: `MobileStatistics` (responsive-more.jsx) over the
 * web `StatisticsPage`'s groups ([statisticsGroups]). Read-only: the mockup has no
 * refresh, so a ticker with nothing stored points at the web's "Load from Yahoo".
 */
@Composable
fun StatisticsScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("statistics:$portfolioId") { StatisticsHoldingsViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    var chosen by rememberSaveable(portfolioId) { mutableStateOf<String?>(null) }
    val selectable = state.data.orEmpty()
    val active = selectable.firstOrNull { it.ticker == chosen } ?: selectable.firstOrNull()
    val statsVm = active?.let { holding ->
        screenViewModel("statistics:$portfolioId:${holding.ticker}") { TickerStatisticsViewModel(it, holding.ticker) }
    }
    statsVm?.let { AutoLoad(it) }

    MobileShell(nav = nav, title = "Statistics", subtitle = active?.let { "Key statistics · ${it.ticker}" } ?: "Key statistics") {
        PageBody(
            state,
            onRefresh = {
                vm.refresh(force = true)
                statsVm?.refresh(force = true)
            },
        ) { holdings ->
            if (active == null) {
                EmptyState(
                    FpIcons.Hash,
                    "No tickers to analyse",
                    "Statistics reads a Yahoo Finance snapshot per ticker. Add a stock or crypto holding to this portfolio first.",
                )
                return@PageBody
            }
            val statsState = statsVm?.state?.collectAsStateWithLifecycle()?.value
            TickerPicker(active, holdings, statsState?.data?.stats?.exchange) { chosen = it }
            VSpace(10.dp)
            val loaded = statsState?.data
            val failure = statsState?.error
            when {
                loaded != null -> {
                    val stats = loaded.stats
                    if (stats == null) {
                        NoStatistics(active.ticker)
                    } else {
                        StatisticsBody(stats, active.quoteShareValue ?: active.currentShareValue)
                    }
                }
                failure != null -> ErrorState(failure) { statsVm?.refresh(force = true) }
                else -> LoadingSkeleton()
            }
        }
    }
}

@Composable
private fun TickerPicker(active: Holding, holdings: List<Holding>, exchange: String?, onPick: (String) -> Unit) {
    val colors = Fp.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, colors.border, shape)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            TickerAvatar(active.ticker, size = 28.dp, color = Brand.Primary, assetType = active.assetType)
            HSpace(10.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(active.ticker, style = FpType.mono(13.5.sp, FontWeight.Bold, colors.text))
                    if (!exchange.isNullOrBlank()) {
                        HSpace(6.dp)
                        Text(exchange, style = FpType.mono(10.sp, FontWeight.Normal, colors.textSubtle))
                    }
                }
                Text(active.name.orEmpty(), color = colors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FpIcon(FpIcons.ChevDown, size = 15.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            holdings.forEach { holding ->
                DropdownMenuItem(
                    leadingIcon = { TickerAvatar(holding.ticker, size = 22.dp, color = Brand.Primary, assetType = holding.assetType) },
                    text = {
                        Column {
                            Text(holding.ticker, style = FpType.mono(12.5.sp, FontWeight.Bold, colors.text))
                            Text(holding.name.orEmpty(), color = colors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    onClick = {
                        open = false
                        onPick(holding.ticker)
                    },
                )
            }
        }
    }
}

@Composable
private fun NoStatistics(ticker: String) {
    FpCard(padding = 18.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            FpIcon(FpIcons.Hash, size = 26.dp, tint = Fp.colors.textSubtle)
            VSpace(10.dp)
            Text("No statistics yet", color = Fp.colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            VSpace(6.dp)
            Text(
                "Nothing has been fetched for $ticker yet. Use \"Load from Yahoo\" on the web app's Statistics page.",
                color = Fp.colors.textMuted,
                fontSize = 12.5.sp,
            )
        }
    }
}

@Composable
private fun StatisticsBody(stats: StatisticsDto, price: Double?) {
    val colors = Fp.colors
    val groups = statisticsGroups(stats)
    val all = groups.sumOf { it.values.size }
    val reported = groups.sumOf { it.reported }
    // thin coverage (normal outside the US) opens on the reported fields only
    var view by rememberSaveable(stats.updatedAt, all) { mutableStateOf(if (reported * 2 < all) "Reported" else "All fields") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        stats.updatedAt?.let { AsOf(it) }
        Box(Modifier.weight(1f))
    }
    VSpace(12.dp)
    KpiGrid(stats)
    VSpace(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Bold)) { append(reported.toString()) }
                append(" of $all reported")
            },
            color = colors.textMuted,
            fontSize = 11.5.sp,
            modifier = Modifier.weight(1f),
        )
        Segmented(listOf("All fields", "Reported"), view, { view = it })
    }
    VSpace(12.dp)
    AnalystTargets(stats, price)
    STAT_GROUP_ORDER.mapNotNull { id -> groups.firstOrNull { it.id == id } }.forEach { group ->
        VSpace(12.dp)
        StatGroupCard(group, hideMissing = view == "Reported")
    }
    VSpace(12.dp)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceMuted)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        FpIcon(FpIcons.Info, size = 13.dp, modifier = Modifier.padding(top = 1.dp))
        HSpace(8.dp)
        Text("$DASH means the figure isn't reported. It is never a zero.", color = colors.textMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun AsOf(stamp: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Fp.colors.gain),
        )
        HSpace(7.dp)
        Text("as of ${stamp.take(16).replace('T', ' ')}", style = FpType.mono(11.5.sp, FontWeight.Normal, Fp.colors.textMuted))
    }
}

@Composable
private fun KpiGrid(s: StatisticsDto) {
    val colors = Fp.colors
    val money = StatMoney(s.currency)
    val change = s.fiftyTwoWeekChange
    val tiles = listOf(
        Tile("Market cap", money.big(s.marketCap), if (s.enterpriseValue == null) "EV not reported" else "EV ${money.big(s.enterpriseValue)}"),
        Tile("Trailing P/E", statNumber(s.trailingPE), s.forwardPE?.let { "fwd ${statNumber(it)}" }),
        Tile("Profit margin", statFraction(s.profitMargin), s.operatingMargin?.let { "op ${statFraction(it)}" }),
        Tile("Return on equity", statFraction(s.returnOnEquity), s.returnOnAssets?.let { "ROA ${statFraction(it)}" }),
        Tile("Dividend yield", statPercent(s.dividendYield), s.payoutRatio?.let { "payout ${statFraction(it, 1)}" }),
        Tile(
            "Beta (5y)",
            statNumber(s.beta),
            change?.let { "52w ${if (it >= 0) "+" else ""}${statFraction(it)}" },
            change?.let { if (it >= 0) colors.gain else colors.loss },
        ),
    )
    FpCard(padding = 0.dp) {
        tiles.chunked(2).forEachIndexed { index, pair ->
            if (index > 0) Divider()
            Row {
                pair.forEach { TileCell(it, Modifier.weight(1f)) }
            }
        }
    }
}

private data class Tile(val label: String, val value: String?, val sub: String?, val subColor: Color? = null)

@Composable
private fun TileCell(tile: Tile, modifier: Modifier) {
    val colors = Fp.colors
    Column(modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        FpLabel(tile.label, color = colors.textSubtle)
        VSpace(4.dp)
        Text(tile.value ?: DASH, style = FpType.mono(17.sp, FontWeight.Bold, if (tile.value == null) colors.textMuted else colors.text), maxLines = 1)
        if (tile.sub != null) {
            Text(
                tile.sub,
                color = tile.subColor ?: colors.textMuted,
                fontSize = 10.5.sp,
                fontWeight = if (tile.subColor != null) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AnalystTargets(s: StatisticsDto, price: Double?) {
    val colors = Fp.colors
    FpCard(padding = 0.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            FpIcon(FpIcons.Target, size = 14.dp, tint = Brand.Primary)
            HSpace(8.dp)
            Text("Analyst price targets", color = colors.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            s.recommendationKey?.let { key ->
                Text(
                    (RECOMMENDATION_LABEL[key] ?: key).uppercase(),
                    color = colors.gain,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.gainBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Divider()
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
            TargetRange(s, price)
            s.numberOfAnalystOpinions?.let { count ->
                VSpace(12.dp)
                Divider()
                VSpace(4.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatLineRow(StatValue("Analysts covering", formatNumber(count, 0)), Modifier.weight(1f))
                    StatLineRow(StatValue("Mean rating", statNumber(s.recommendationMean, 1)), Modifier.weight(1f))
                }
            }
        }
    }
}

/** Low → high analyst targets, the mean as a bar, today's quoted price as a ring. */
@Composable
private fun TargetRange(s: StatisticsDto, price: Double?) {
    val colors = Fp.colors
    val money = StatMoney(s.currency)
    val low = s.targetLowPrice
    val high = s.targetHighPrice
    val mean = s.targetMeanPrice
    if (low == null || high == null || high <= low) {
        Text("No analyst coverage for this listing", color = colors.textSubtle, fontSize = 12.sp)
        return
    }
    fun at(v: Double) = ((v - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    val ring = if (colors.isDark) Color.White else Color(0xFF0F172A)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(34.dp),
    ) {
        val top = 13.dp.toPx()
        val barHeight = 8.dp.toPx()
        val radius = CornerRadius(barHeight / 2, barHeight / 2)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0x33EF4444), Color(0x33F59E0B), Color(0x4D22C55E))),
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = radius,
        )
        drawRoundRect(colors.border, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius, style = Stroke(1.dp.toPx()))
        if (mean != null) {
            val x = size.width * at(mean)
            drawRoundRect(Brand.Primary, topLeft = Offset(x - 1.5.dp.toPx(), 6.dp.toPx()), size = Size(3.dp.toPx(), 22.dp.toPx()), cornerRadius = CornerRadius(1.dp.toPx()))
        }
        if (price != null) {
            val center = Offset((size.width * at(price)).coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), top + barHeight / 2)
            drawCircle(if (colors.isDark) colors.surface else Color.White, radius = 6.dp.toPx(), center = center)
            drawCircle(ring, radius = 4.5.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append(money.price(low) ?: DASH)
                withStyle(SpanStyle(color = colors.textSubtle)) { append(" low") }
            },
            style = FpType.mono(11.5.sp, FontWeight.Normal, colors.textMuted),
        )
        Box(Modifier.weight(1f))
        Text("${money.price(mean) ?: DASH} mean", style = FpType.mono(11.5.sp, FontWeight.Bold, colors.link))
        Box(Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.textSubtle)) { append("high ") }
                append(money.price(high) ?: DASH)
            },
            style = FpType.mono(11.5.sp, FontWeight.Normal, colors.textMuted),
        )
    }
    if (mean != null && price != null && price > 0) {
        val upside = (mean / price - 1) * 100
        VSpace(10.dp)
        Text(
            buildAnnotatedString {
                append("Consensus implies ")
                withStyle(SpanStyle(color = if (upside >= 0) colors.gain else colors.loss, fontWeight = FontWeight.Bold)) {
                    append("${if (upside >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", upside)}%")
                }
                append(" from the last price (${money.price(price)}).")
            },
            color = colors.textMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

private fun groupIcon(id: String): ImageVector = when (id) {
    "valuation" -> FpIcons.Target
    "profit" -> FpIcons.Pie
    "profile" -> FpIcons.Building
    "income" -> FpIcons.Coins
    "balance" -> FpIcons.Wallet
    "dividends" -> FpIcons.Calendar
    "trading" -> FpIcons.Trending
    else -> FpIcons.Hash
}

@Composable
private fun StatGroupCard(group: StatGroup, hideMissing: Boolean) {
    val colors = Fp.colors
    var open by rememberSaveable(group.id) { mutableStateOf(true) }
    val lines = if (hideMissing) group.lines.filter { it !is StatValue || it.value != null } else group.lines
    val visible = lines.filterIsInstance<StatValue>()
    FpCard(padding = 0.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceMuted)
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            FpIcon(groupIcon(group.id), size = 14.dp, tint = Brand.Primary)
            HSpace(8.dp)
            Text(group.title, color = colors.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            group.hint?.let {
                HSpace(6.dp)
                FpLabel(it, color = colors.textSubtle)
            }
            Box(Modifier.weight(1f))
            Text("${group.reported}/${group.values.size}", style = FpType.mono(10.5.sp, FontWeight.Normal, colors.textSubtle))
            HSpace(6.dp)
            FpIcon(if (open) FpIcons.ChevUp else FpIcons.ChevDown, size = 14.dp, tint = colors.textSubtle)
        }
        if (open) {
            Divider()
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp)) {
                if (visible.isEmpty()) {
                    Text(
                        "Nothing reported in this group",
                        color = colors.textSubtle,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    lines.forEachIndexed { index, line ->
                        when (line) {
                            is StatSubhead -> FpLabel(line.text, color = colors.textSubtle, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                            is StatValue -> {
                                StatLineRow(line)
                                if (index < lines.lastIndex && lines[index + 1] is StatValue) Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatLineRow(line: StatValue, modifier: Modifier = Modifier) {
    val colors = Fp.colors
    val value = line.value
    val tone = when {
        value == null -> colors.textSubtle
        line.kind == StatKind.Link -> colors.link
        line.kind == StatKind.Signed -> if (value.startsWith("-")) colors.loss else colors.gain
        else -> colors.text
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Text(line.label, color = colors.textMuted, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        HSpace(12.dp)
        Text(
            value ?: DASH,
            style = FpType.mono(13.sp, if (value == null) FontWeight.Normal else FontWeight.SemiBold, tone),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
