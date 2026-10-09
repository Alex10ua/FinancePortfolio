package com.dev.alex.portfolio.ui.watchlist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.data.api.RequestRejectedException
import com.dev.alex.portfolio.data.api.SessionExpiredException
import com.dev.alex.portfolio.data.api.TickerSuggestionDto
import com.dev.alex.portfolio.data.api.WatchlistEntryDto
import com.dev.alex.portfolio.data.api.describeError
import com.dev.alex.portfolio.data.api.wasNeverSent
import com.dev.alex.portfolio.domain.YIELD_FLOORS
import com.dev.alex.portfolio.domain.YieldStats
import com.dev.alex.portfolio.domain.YieldTimeframe
import com.dev.alex.portfolio.domain.formatMoney
import com.dev.alex.portfolio.domain.formatPercent
import com.dev.alex.portfolio.domain.formatSignedPercent
import com.dev.alex.portfolio.domain.normalizeCurrency
import com.dev.alex.portfolio.domain.ordinal
import com.dev.alex.portfolio.domain.parseDecimal
import com.dev.alex.portfolio.domain.statDate
import com.dev.alex.portfolio.domain.toMajorUnits
import com.dev.alex.portfolio.domain.yieldStats
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.optional
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpChip
import com.dev.alex.portfolio.ui.components.FpFab
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpLabel
import com.dev.alex.portfolio.ui.components.FpTextField
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.SecondaryButton
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAB_LIST = "Watchlist"
private const val TAB_YIELD = "Yield Target"

data class WatchlistData(
    val entries: List<WatchlistEntryDto>,
    /** ticker → its tags; the chips filter by these, as on the web */
    val tagsByTicker: Map<String, List<String>>,
)

/**
 * Tickers watched for the dividend yield that makes them a buy: `MobileWatchlist`
 * (responsive-more.jsx) over the web `WatchlistPage`. Its three writes (add, target yield,
 * remove) are each sent once and the list reloads after; none touches holdings, so the
 * other screens are left alone.
 */
class WatchlistViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<WatchlistData>(app) {
    var tab by mutableStateOf(TAB_LIST)
    /** null = all */
    var activeTag by mutableStateOf<String?>(null)
    var timeframe by mutableStateOf(YieldTimeframe.Y5)
    var floor by mutableStateOf(90)
    var expanded by mutableStateOf<String?>(null)

    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var suggestions by mutableStateOf<List<TickerSuggestionDto>>(emptyList())
        private set

    private var searchJob: Job? = null

    override suspend fun load(tracker: StaleTracker): WatchlistData = coroutineScope {
        val tags = async { optional { tracker.take(app.repository.tags(portfolioId)) }.orEmpty() }
        val entries = tracker.take(app.repository.watchlist(portfolioId))
        WatchlistData(entries, tags.await().associate { it.ticker to it.tags })
    }

    fun dismissMessage() {
        message = null
    }

    /** Saved only when it differs and parses above zero, as the web field does. */
    fun setTarget(entry: WatchlistEntryDto, text: String) {
        val value = parseDecimal(text) ?: return
        if (value.signum() <= 0) return
        val current = entry.targetYield
        if (current != null && kotlin.math.abs(value.toDouble() - current) < 1e-9) return
        write { app.repository.setTargetYield(portfolioId, entry.ticker, value.toPlainString()) }
    }

    fun remove(ticker: String) {
        write { app.repository.removeFromWatchlist(portfolioId, ticker) }
    }

    /** Blank target = the backend's 5-year 90th percentile. */
    fun add(ticker: String, target: String, onAdded: () -> Unit) {
        val symbol = ticker.trim().uppercase()
        if (symbol.isEmpty()) return
        val yieldText = target.takeIf { it.isNotBlank() }?.let { parseDecimal(it)?.toPlainString() }
        write(onAdded) { app.repository.addToWatchlist(portfolioId, symbol, yieldText) }
    }

    fun search(text: String) {
        searchJob?.cancel()
        val query = text.trim()
        if (query.length < 2) {
            suggestions = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            suggestions = try {
                app.repository.searchTickers(portfolioId, query).filter { !it.watched }.take(6)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun clearSuggestions() {
        searchJob?.cancel()
        suggestions = emptyList()
    }

    private fun write(onDone: () -> Unit = {}, action: suspend () -> Unit) {
        if (saving) return
        saving = true
        message = null
        viewModelScope.launch {
            try {
                action()
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                app.sessionExpired.tryEmit(Unit)
            } catch (e: RequestRejectedException) {
                message = e.message
            } catch (e: Exception) {
                message = if (wasNeverSent(e)) "Not saved. ${describeError(e)}" else "No answer from the server; the list below shows what it has."
            } finally {
                saving = false
                refresh(force = true)
            }
        }
    }
}

@Composable
fun WatchlistScreen(portfolioId: String, nav: ShellNav) {
    val vm = screenViewModel("watchlist:$portfolioId") { WatchlistViewModel(it, portfolioId) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    MobileShell(nav = nav, title = "Watchlist", subtitle = "Watched for the yield you'd buy at") {
        Box(Modifier.fillMaxSize()) {
            PageBody(state, onRefresh = { vm.refresh(force = true) }) { data ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Segmented(listOf(TAB_LIST, TAB_YIELD), vm.tab, { vm.tab = it })
                    HSpace(10.dp)
                    PricesAsOf(data.entries)
                }
                VSpace(12.dp)
                vm.message?.let {
                    MessageLine(it, vm::dismissMessage)
                    VSpace(10.dp)
                }
                if (data.entries.isEmpty()) {
                    EmptyState(FpIcons.Eye, "Nothing watched yet", "Tap + to watch a ticker for the dividend yield you'd buy it at.")
                    return@PageBody
                }
                TagChips(data, vm.activeTag) { vm.activeTag = it }
                val tag = vm.activeTag
                val scoped = if (tag == null) data.entries else data.entries.filter { tag in data.tagsByTicker[it.ticker].orEmpty() }
                if (vm.tab == TAB_LIST) {
                    WatchlistTab(scoped, vm)
                } else {
                    YieldTargetTab(scoped, vm)
                }
                VSpace(64.dp)
            }
            FpFab(
                "Add ticker",
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(18.dp),
            ) { adding = true }
        }
    }

    if (adding) {
        AddTickerDialog(vm, onClose = {
            adding = false
            vm.clearSuggestions()
        })
    }
}

@Composable
private fun PricesAsOf(entries: List<WatchlistEntryDto>) {
    val latest = entries.mapNotNull { it.priceUpdatedAt }.maxOrNull() ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Fp.colors.gain),
        )
        HSpace(5.dp)
        Text("prices ${statDate(latest) ?: latest}", color = Fp.colors.textMuted, fontSize = 10.5.sp, maxLines = 1)
    }
}

@Composable
private fun MessageLine(text: String, onDismiss: () -> Unit) {
    val colors = Fp.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.lossBg)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = colors.loss, fontSize = 12.sp, modifier = Modifier.weight(1f))
        FpIcon(FpIcons.X, size = 13.dp, tint = colors.loss)
    }
}

/** "All" plus only the tags that sit on a watched ticker — the rest belong to holdings. */
@Composable
private fun TagChips(data: WatchlistData, active: String?, onPick: (String?) -> Unit) {
    val counts = data.entries.flatMap { data.tagsByTicker[it.ticker].orEmpty() }.groupingBy { it }.eachCount().toSortedMap()
    if (counts.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        FpChip("All", active = active == null, count = data.entries.size) { onPick(null) }
        counts.forEach { (tag, count) -> FpChip("#$tag", active = active == tag, count = count) { onPick(tag) } }
    }
    VSpace(12.dp)
}

@Composable
private fun WatchlistTab(entries: List<WatchlistEntryDto>, vm: WatchlistViewModel) {
    val colors = Fp.colors
    val buys = entries.count { it.atTarget }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${entries.size} tickers", color = colors.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (buys > 0) {
            Text("$buys at target yield", color = colors.gain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text("Nothing at target yet", color = colors.textSubtle, fontSize = 12.sp)
        }
    }
    VSpace(8.dp)
    entries.forEach { entry ->
        var confirmRemove by remember(entry.ticker) { mutableStateOf(false) }
        FpCard(padding = 12.dp, background = if (entry.atTarget) colors.gainBg else null) {
            TickerHeader(entry) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(quoteMoney(entry.price, entry.currency), style = FpType.mono(13.5.sp, FontWeight.SemiBold, colors.text))
                    Text(
                        formatSignedPercent(entry.dayChangePercent),
                        style = FpType.mono(11.sp, FontWeight.SemiBold, moveColor(entry.dayChangePercent)),
                    )
                }
            }
            VSpace(10.dp)
            Grid3(
                listOf(
                    Kv("Fwd yield", formatPercent(entry.forwardYield)),
                    Kv("Buy below", quoteMoney(entry.buyBelowPrice, entry.currency), colors.textMuted),
                    toTargetKv(entry),
                    Kv("Pays", entry.dividendFrequency ?: "—", colors.textMuted),
                    Kv("Next ex-div", statDate(entry.exDividendDate) ?: "—", colors.textMuted),
                ),
            ) { Signal(entry, null) }
            VSpace(10.dp)
            Divider()
            VSpace(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Target yield", color = colors.textMuted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                TargetField(entry.targetYield, enabled = !vm.saving) { vm.setTarget(entry, it) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !vm.saving, onClickLabel = "Remove ${entry.ticker}") { confirmRemove = true },
                ) {
                    FpIcon(FpIcons.Trash, size = 15.dp, tint = colors.textSubtle)
                }
            }
        }
        VSpace(8.dp)
        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("Stop watching ${entry.ticker}?") },
                text = { Text("Its target yield is forgotten. Its tags stay.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRemove = false
                        vm.remove(entry.ticker)
                    }) { Text("Remove", color = colors.loss) }
                },
                dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun YieldTargetTab(entries: List<WatchlistEntryDto>, vm: WatchlistViewModel) {
    val colors = Fp.colors
    val ranked = entries.mapNotNull { entry ->
        yieldStats(entry.yieldHistory.map { it.value }, entry.forwardYield, vm.timeframe)?.let { entry to it }
    }.sortedByDescending { it.second.percentile }
    val unranked = entries.size - ranked.size
    val passing = ranked.filter { it.second.percentile >= vm.floor }
    val floorLabel = YIELD_FLOORS.firstOrNull { it.second == vm.floor }?.first ?: "Any"

    FilterRow("Baseline") {
        Segmented(YieldTimeframe.entries.map { it.label }, vm.timeframe.label, { label ->
            vm.timeframe = YieldTimeframe.entries.first { it.label == label }
        })
    }
    VSpace(8.dp)
    FilterRow("Floor") {
        Segmented(YIELD_FLOORS.map { it.first }, floorLabel, { label ->
            vm.floor = YIELD_FLOORS.first { it.first == label }.second
        })
    }
    VSpace(12.dp)
    val span = if (vm.timeframe == YieldTimeframe.All) "all-time" else vm.timeframe.label
    Text(
        buildAnnotatedString {
            if (vm.floor > 0) {
                append("At or above the ")
                withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Bold)) { append("${vm.floor}th") }
                append(" percentile")
            } else {
                append("All, richest yield first")
            }
            append(" · $span")
        },
        color = colors.textMuted,
        fontSize = 12.sp,
    )
    VSpace(8.dp)

    if (passing.isEmpty()) {
        FpCard(padding = 16.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (ranked.isEmpty()) "No yield history to rank" else "Nothing above the ${vm.floor}th percentile",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                VSpace(6.dp)
                Text(
                    if (ranked.isEmpty()) "These tickers have no stored dividend and price history yet."
                    else "No watched ticker is that cheap on a $span yield basis right now.",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                if (ranked.isNotEmpty()) {
                    VSpace(14.dp)
                    val lower = if (vm.floor > 75) 75 else 0
                    SecondaryButton(if (lower == 75) "Drop floor to 75th" else "Show all", onClick = { vm.floor = lower })
                }
            }
        }
    } else {
        passing.forEach { (entry, stats) ->
            YieldCard(entry, stats, vm)
            VSpace(8.dp)
        }
    }
    if (passing.isNotEmpty() && passing.size < ranked.size) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
            FpIcon(FpIcons.Filter, size = 12.dp, tint = colors.textSubtle)
            HSpace(6.dp)
            Text("${ranked.size - passing.size} below the floor hidden · ", color = colors.textMuted, fontSize = 11.5.sp)
            Text(
                "Show all",
                color = colors.link,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { vm.floor = 0 },
            )
        }
    }
    if (unranked > 0) {
        Text(
            "$unranked ${if (unranked == 1) "ticker has" else "tickers have"} no stored yield history and cannot be ranked.",
            color = colors.textSubtle,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FpLabel(label, modifier = Modifier.width(62.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun YieldCard(entry: WatchlistEntryDto, stats: YieldStats, vm: WatchlistViewModel) {
    val colors = Fp.colors
    val open = vm.expanded == entry.ticker
    FpCard(padding = 0.dp, background = if (entry.atTarget) colors.gainBg else null) {
        Column(
            Modifier
                .clickable { vm.expanded = if (open) null else entry.ticker }
                .padding(12.dp),
        ) {
            TickerHeader(entry) {
                Column(horizontalAlignment = Alignment.End) {
                    PercentileText(stats.percentile)
                    Text("pctile", color = colors.textSubtle, fontSize = 9.5.sp)
                }
            }
            VSpace(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                YieldStrip(stats, threshold = if (vm.floor > 0) vm.floor else 90, modifier = Modifier.weight(1f))
                HSpace(8.dp)
                Text(
                    "${formatPercent(stats.min, 1)}–${formatPercent(stats.max, 1)}",
                    style = FpType.mono(10.5.sp, FontWeight.Normal, colors.textSubtle),
                )
            }
            VSpace(8.dp)
            Grid3(
                listOf(
                    Kv("Fwd yield", formatPercent(stats.current)),
                    Kv("Median ${vm.timeframe.label}", formatPercent(stats.median), colors.textMuted),
                    Kv("vs median", formatSignedPercent(stats.vsMedian, 1), moveColor(stats.vsMedian)),
                    Kv("Buy below", quoteMoney(entry.buyBelowPrice, entry.currency), colors.textMuted),
                    toTargetKv(entry),
                ),
            ) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Signal(entry, stats.percentile) }
                    FpIcon(if (open) FpIcons.ChevDown else FpIcons.ChevRight, size = 14.dp, tint = colors.textSubtle)
                }
            }
        }
        if (open) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceMuted)
                    .padding(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KvCell(Kv("Min", formatPercent(stats.min), colors.textMuted), Modifier.weight(1f))
                    KvCell(Kv("25th", formatPercent(stats.p25), colors.textMuted), Modifier.weight(1f))
                    KvCell(Kv("75th", formatPercent(stats.p75), colors.textMuted), Modifier.weight(1f))
                    KvCell(Kv("90th", formatPercent(stats.p90)), Modifier.weight(1f))
                }
                VSpace(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target yield", color = colors.textMuted, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                    TargetField(entry.targetYield, enabled = !vm.saving) { vm.setTarget(entry, it) }
                    HSpace(8.dp)
                    SecondaryButton("Use 90th", onClick = { vm.setTarget(entry, String.format(java.util.Locale.US, "%.2f", stats.p90)) })
                }
            }
        }
    }
}

@Composable
private fun TickerHeader(entry: WatchlistEntryDto, right: @Composable () -> Unit) {
    val colors = Fp.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        TickerAvatar(entry.ticker, size = 28.dp, color = Brand.Primary)
        HSpace(10.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.ticker, style = FpType.mono(13.sp, FontWeight.Bold, colors.link), maxLines = 1)
                if (entry.held) {
                    HSpace(6.dp)
                    Text(
                        "HELD",
                        color = colors.over,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Semantic.Warning.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Text(entry.name ?: "—", color = colors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        right()
    }
}

private data class Kv(val label: String, val value: String, val color: Color? = null)

@Composable
private fun toTargetKv(entry: WatchlistEntryDto): Kv {
    val colors = Fp.colors
    return if (entry.atTarget) {
        Kv("To target", "reached", colors.gain)
    } else {
        Kv("To target", formatSignedPercent(entry.toTargetPercent, 1), colors.textMuted)
    }
}

/** Two rows of three; the sixth cell is the caller's (signal badge). */
@Composable
private fun Grid3(cells: List<Kv>, last: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.take(3).forEach { KvCell(it, Modifier.weight(1f)) }
    }
    VSpace(9.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        cells.drop(3).forEach { KvCell(it, Modifier.weight(1f)) }
        Box(Modifier.weight(1f)) { last() }
    }
}

@Composable
private fun KvCell(kv: Kv, modifier: Modifier) {
    Column(modifier) {
        FpLabel(kv.label, color = Fp.colors.textSubtle, size = 9.5.sp)
        VSpace(2.dp)
        Text(
            kv.value,
            style = FpType.mono(12.5.sp, FontWeight.SemiBold, kv.color ?: Fp.colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** BUY at or past the target yield; CHEAP at the 90th percentile or richer; else watching. */
@Composable
private fun Signal(entry: WatchlistEntryDto, percentile: Double?) {
    val colors = Fp.colors
    when {
        entry.atTarget -> Badge("BUY", colors.gain, colors.gainBg)
        percentile != null && percentile >= 90 -> Badge("CHEAP", Semantic.Info, Semantic.Info.copy(alpha = 0.12f))
        else -> Text("Watching", color = colors.textSubtle, fontSize = 11.5.sp)
    }
}

@Composable
private fun Badge(text: String, fg: Color, bg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** "93rd": green at the 90th and up, blue from the 75th, grey at the bottom quarter. */
@Composable
private fun PercentileText(percentile: Double) {
    val colors = Fp.colors
    val tone = when {
        percentile >= 90 -> colors.gain
        percentile >= 75 -> Semantic.Info
        percentile <= 25 -> colors.textSubtle
        else -> colors.textMuted
    }
    Text(
        buildAnnotatedString {
            if (percentile >= 99.5) {
                append("99+")
            } else {
                append(percentile.roundToInt().toString())
                withStyle(SpanStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)) { append(ordinal(percentile)) }
            }
        },
        style = FpType.mono(14.sp, FontWeight.Bold, tone),
    )
}

/**
 * Where today's yield sits in the ticker's own distribution: track min→max, green tail
 * from the floor percentile up, darker block 25th–75th, a hairline at the median, and a
 * solid marker for today (green inside the tail).
 */
@Composable
private fun YieldStrip(stats: YieldStats, threshold: Int, modifier: Modifier) {
    val dark = Fp.colors.isDark
    val trackColor = if (dark) Color(0x33949FB8) else Color(0xFFE2E8F0)
    val bandColor = if (dark) Color(0x4D34D399) else Color(0x38059669)
    val iqrColor = if (dark) Color(0x57949FB8) else Color(0xFFCBD5E1)
    val medianColor = if (dark) Color(0xFF64748B) else Color(0xFF94A3B8)
    val inBand = stats.percentile >= threshold
    val markerColor = if (inBand) (if (dark) Color(0xFF34D399) else Color(0xFF059669)) else (if (dark) Color(0xFF94A3B8) else Color(0xFF64748B))
    val bandStart = stats.yieldAt(threshold.toDouble())
    Canvas(modifier.height(22.dp)) {
        val pad = 3.dp.toPx()
        val lo0 = minOf(stats.min, stats.current)
        val hi0 = maxOf(stats.max, stats.current)
        val spread = (hi0 - lo0).takeIf { it > 0 } ?: 1.0
        val lo = lo0 - spread * 0.07
        val hi = hi0 + spread * 0.07
        fun x(v: Double): Float = (pad + (v - lo) / (hi - lo) * (size.width - pad * 2)).toFloat()
        val mid = size.height / 2
        val barTop = mid - 3.dp.toPx()
        val barHeight = 6.dp.toPx()
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        drawRoundRect(trackColor, Offset(pad, barTop), Size(size.width - pad * 2, barHeight), radius)
        drawRoundRect(bandColor, Offset(x(bandStart), barTop), Size(maxOf(2f, x(hi) - x(bandStart)), barHeight), radius)
        drawRect(iqrColor, Offset(x(stats.p25), barTop), Size(maxOf(1f, x(stats.p75) - x(stats.p25)), barHeight))
        drawRect(medianColor, Offset(x(stats.median) - 0.75.dp.toPx(), mid - 7.dp.toPx()), Size(1.5.dp.toPx(), 14.dp.toPx()))
        val markerX = x(stats.current).coerceIn(pad, size.width - pad - 3.dp.toPx())
        drawRoundRect(markerColor, Offset(markerX - 1.5.dp.toPx(), 2.dp.toPx()), Size(3.dp.toPx(), size.height - 4.dp.toPx()), CornerRadius(1.5.dp.toPx()))
    }
}

/**
 * Inline target-yield field: typing stays local, and the value is saved on Done or when
 * the field loses focus, as the web's `TargetYieldField` (a save per keystroke would
 * re-rank the list mid-edit).
 */
@Composable
private fun TargetField(value: Double?, enabled: Boolean, onCommit: (String) -> Unit) {
    val colors = Fp.colors
    val focus = LocalFocusManager.current
    val shown = value?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: ""
    var draft by remember(value) { mutableStateOf(shown) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(colors.surfaceMuted)
            .border(1.dp, if (focused) Brand.Primary else colors.border, shape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            textStyle = FpType.mono(13.sp, FontWeight.Bold, colors.text).copy(textAlign = TextAlign.End),
            cursorBrush = SolidColor(Brand.Primary),
            modifier = Modifier
                .width(44.dp)
                .onFocusChanged {
                    if (focused && !it.isFocused) {
                        if (draft != shown) onCommit(draft) else draft = shown
                    }
                    focused = it.isFocused
                },
        )
        HSpace(2.dp)
        Text("%", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Add a ticker: search what the backend knows, or type any symbol and let it fetch. */
@Composable
private fun AddTickerDialog(vm: WatchlistViewModel, onClose: () -> Unit) {
    val colors = Fp.colors
    var ticker by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    // close once the add went through; an error keeps the dialog open with its reason
    LaunchedEffect(vm.saving, vm.message) {
        if (submitted && !vm.saving) {
            submitted = false
            if (vm.message == null) onClose()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!vm.saving) onClose() },
        title = { Text("Watch a ticker") },
        text = {
            Column {
                FpTextField(
                    value = ticker,
                    onValueChange = {
                        ticker = it
                        vm.search(it)
                    },
                    label = "Ticker",
                    placeholder = "Ticker or company, e.g. KO",
                    icon = FpIcons.Search,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                )
                vm.suggestions.forEach { hit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ticker = hit.ticker
                                vm.clearSuggestions()
                            }
                            .padding(vertical = 7.dp),
                    ) {
                        TickerAvatar(hit.ticker, size = 22.dp, color = Brand.Primary)
                        HSpace(8.dp)
                        Text(hit.ticker, style = FpType.mono(12.5.sp, FontWeight.Bold, colors.text))
                        HSpace(6.dp)
                        Text(hit.name.orEmpty(), color = colors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                VSpace(12.dp)
                FpTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = "Target yield (%)",
                    placeholder = "Blank = 5-year 90th percentile",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
                vm.message?.let {
                    VSpace(10.dp)
                    Text(it, color = colors.loss, fontSize = 12.sp)
                }
                if (vm.saving) {
                    VSpace(10.dp)
                    Text("Adding… a symbol the server hasn't seen takes a few seconds.", color = colors.textMuted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !vm.saving && ticker.isNotBlank(),
                onClick = {
                    submitted = true
                    vm.add(ticker, target) {}
                },
            ) { Text("Watch") }
        },
        dismissButton = { TextButton(enabled = !vm.saving, onClick = onClose) { Text("Cancel") } },
    )
}

/** Watchlist money stays in the ticker's quote currency; pence read as pounds. */
private fun quoteMoney(value: Double?, currency: String?): String =
    if (value == null) "—" else formatMoney(toMajorUnits(value, currency), normalizeCurrency(currency))

@Composable
private fun moveColor(value: Double?): Color = when {
    value == null -> Fp.colors.textSubtle
    value >= 0 -> Fp.colors.gain
    else -> Fp.colors.loss
}
