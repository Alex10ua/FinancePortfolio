package com.dev.alex.portfolio.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.data.StaleTracker
import com.dev.alex.portfolio.domain.CalendarYear
import com.dev.alex.portfolio.domain.CurrencyContext
import com.dev.alex.portfolio.domain.MonthBucket
import com.dev.alex.portfolio.domain.PORTFOLIO_PALETTE
import com.dev.alex.portfolio.domain.Payment
import com.dev.alex.portfolio.domain.buildCalendarYear
import com.dev.alex.portfolio.domain.currencyContextFor
import com.dev.alex.portfolio.domain.formatCalendarShares
import com.dev.alex.portfolio.domain.formatNumber
import com.dev.alex.portfolio.domain.normalize
import com.dev.alex.portfolio.domain.prefsFor
import com.dev.alex.portfolio.domain.toFxRates
import com.dev.alex.portfolio.ui.common.AutoLoad
import com.dev.alex.portfolio.ui.common.LoadViewModel
import com.dev.alex.portfolio.ui.common.PageBody
import com.dev.alex.portfolio.ui.common.optional
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.EmptyState
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.HSpace
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import kotlin.math.abs

data class CalendarData(
    val ctx: CurrencyContext,
    val year: CalendarYear,
    /** % change of the annual total on the year before; null when there is nothing to compare */
    val delta: Double?,
)

/** Year options: from the first trade year to one past this year (a full projected year). */
class FirstTradeYearViewModel(app: AppContainer, private val portfolioId: String) : LoadViewModel<Int>(app) {
    override suspend fun load(tracker: StaleTracker): Int =
        tracker.take(app.repository.firstTradeYear(portfolioId)).firstTradeYear ?: LocalDate.now().year
}

class CalendarViewModel(app: AppContainer, private val portfolioId: String, private val year: Int) : LoadViewModel<CalendarData>(app) {
    override suspend fun load(tracker: StaleTracker): CalendarData = coroutineScope {
        val repo = app.repository
        val thisYear = async { tracker.take(repo.dividendCalendar(portfolioId, year)) }
        val lastYear = async { optional { tracker.take(repo.dividendCalendar(portfolioId, year - 1)) } }
        val holdingsCall = async { tracker.take(repo.holdings(portfolioId)) }
        val ratesCall = async { tracker.take(repo.fxRates()).toFxRates() }
        val settingsCall = async { optional { tracker.take(repo.settings()) } }
        val dividendsCall = async { optional { tracker.take(repo.dividends(portfolioId)) } }

        val rates = ratesCall.await()
        val holdings = holdingsCall.await().map { it.normalize(rates) }
        val ctx = currencyContextFor(settingsCall.await().prefsFor(portfolioId), holdings, rates)
        val tickerCurrency = dividendsCall.await()?.tickerCurrency
        val current = buildCalendarYear(thisYear.await(), year, holdings, tickerCurrency, ctx)
        val previous = lastYear.await()?.let { buildCalendarYear(it, year - 1, holdings, tickerCurrency, ctx) }
        val previousTotal = previous?.total ?: 0.0
        CalendarData(
            ctx = ctx,
            year = current,
            delta = if (previousTotal > 0) (current.total - previousTotal) / previousTotal * 100 else null,
        )
    }
}

@Composable
fun DividendCalendarScreen(portfolioId: String, nav: ShellNav) {
    val today = remember { LocalDate.now() }
    val firstVm = screenViewModel("firstTradeYear:$portfolioId") { FirstTradeYearViewModel(it, portfolioId) }
    AutoLoad(firstVm)
    val first by firstVm.state.collectAsStateWithLifecycle()
    val firstYear = minOf(first.data ?: today.year, today.year)
    val years = (firstYear..today.year + 1).toList()

    var year by rememberSaveable { mutableStateOf(today.year) }
    val vm = screenViewModel("calendar:$portfolioId:$year") { CalendarViewModel(it, portfolioId, year) }
    AutoLoad(vm)
    val state by vm.state.collectAsStateWithLifecycle()

    MobileShell(nav = nav, title = "Dividend Calendar", subtitle = "Every payment of $year") {
        // the year picker stays put through every state, so an empty year is never a dead end
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Segmented(years.map { it.toString() }, year.toString(), { year = it.toInt() })
            Text(
                when {
                    year > today.year -> "Projected from current holdings and the trailing payment pattern"
                    year == today.year -> "Paid to date · remaining months scheduled"
                    else -> "Closed year · actual payments received"
                },
                color = Fp.colors.textSubtle,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            PageBody(state, onRefresh = { vm.refresh(force = true) }) { data ->
                CalendarContent(data, today)
            }
        }
    }
}

@Composable
private fun CalendarContent(data: CalendarData, today: LocalDate) {
    val calendar = data.year
    if (calendar.paymentCount == 0) {
        EmptyState(
            FpIcons.Calendar,
            "No dividends in ${calendar.year}",
            when {
                calendar.year > today.year -> "Nothing to project — no holding has paid a dividend in the last year."
                calendar.year == today.year -> "No payments have been recorded or scheduled for this year yet."
                else -> "No dividend was paid on a position held during this year."
            },
        )
        return
    }
    val defaultMonth = if (calendar.year == today.year) today.monthValue - 1 else (calendar.best?.index ?: 0)
    var selected by rememberSaveable(calendar.year) { mutableStateOf(defaultMonth) }

    SummaryCard(data, today)
    VSpace(12.dp)
    HeatGrid(calendar.months, selected, data.ctx) { selected = it }
    VSpace(12.dp)
    MonthSection(calendar.months[selected], calendar.year, today, data.ctx)
}

@Composable
private fun SummaryCard(data: CalendarData, today: LocalDate) {
    val colors = Fp.colors
    val dark = colors.isDark
    val calendar = data.year
    val heading = when {
        calendar.year > today.year -> "Projected"
        calendar.year == today.year -> "Paid & scheduled"
        else -> "Total received"
    }
    FpCard(
        brush = Brush.linearGradient(
            if (dark) listOf(Color(0xFF1E293B), Brand.Primary900) else listOf(Brand.Primary50, Brand.Primary200),
        ),
        borderColor = if (dark) Brand.Primary900 else Brand.Primary200,
    ) {
        Text(
            "${calendar.year} ${heading.uppercase()}",
            color = if (dark) Brand.Primary200 else Brand.Primary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        VSpace(4.dp)
        Text(data.ctx.money(calendar.total), style = FpType.number(26.sp, FontWeight.Bold, colors.text))
        VSpace(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountText(calendar.paymentCount.toString(), "payments")
            CountText(calendar.payerCount.toString(), "stocks")
            val delta = data.delta
            if (delta != null) {
                Row {
                    Text(
                        "${if (delta >= 0) "+" else ""}${formatNumber(delta, 1)}%",
                        color = if (delta >= 0) colors.gain else colors.loss,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(" vs ${calendar.year - 1}", color = colors.textMuted, fontSize = 11.5.sp)
                }
            } else {
                Text("first tracked year", color = colors.textMuted, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun CountText(count: String, noun: String) {
    Row {
        Text(count, color = Fp.colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        Text(" $noun", color = Fp.colors.textMuted, fontSize = 11.5.sp)
    }
}

/** Twelve tiles, indigo intensity = the month's total; tapping one picks the month below. */
@Composable
private fun HeatGrid(months: List<MonthBucket>, selected: Int, ctx: CurrencyContext, onPick: (Int) -> Unit) {
    val colors = Fp.colors
    val dark = colors.isDark
    val max = maxOf(1.0, months.maxOf { it.total })
    months.chunked(6).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            row.forEach { month ->
                val intensity = (month.total / max).toFloat()
                val on = month.index == selected
                val scheduled = !month.paid && !month.current
                val fill = if (month.payments.isNotEmpty()) {
                    Brand.Primary.copy(alpha = if (dark) 0.15f + intensity * 0.6f else 0.05f + intensity * 0.45f)
                } else {
                    colors.surface
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(fill)
                        .then(
                            when {
                                on -> Modifier.border(2.dp, Brand.Primary, RoundedCornerShape(6.dp))
                                scheduled -> Modifier.dashedBorder(colors.border, 6.dp)
                                else -> Modifier.border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            },
                        )
                        .clickable { onPick(month.index) }
                        .padding(vertical = 7.dp, horizontal = 2.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            month.short.uppercase(),
                            color = if (on) (if (dark) Brand.Primary200 else Brand.Primary) else colors.textMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                        Text(
                            if (month.payments.isEmpty()) "—" else ctx.money(month.total, decimals = 0),
                            style = FpType.number(11.sp, FontWeight.Bold, colors.text),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (month.current) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 3.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Brand.Primary),
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
    )
}

@Composable
private fun MonthSection(month: MonthBucket, year: Int, today: LocalDate, ctx: CurrencyContext) {
    val colors = Fp.colors
    val count = month.payments.size
    FpCard(padding = 0.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(if (colors.isDark) Color(0x0F94A3B8) else colors.surfaceMuted)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(month.label, color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            when {
                month.current -> TagPill("This month", Brand.Primary)
                !month.paid && count > 0 -> TagPill("Scheduled", colors.textMuted, dashed = true)
            }
            Box(Modifier.weight(1f))
            if (count > 0) {
                Text(
                    ctx.money(month.total),
                    style = FpType.number(12.sp, FontWeight.Bold, if (colors.isDark) Brand.Primary200 else Brand.Primary),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Brand.Primary.copy(alpha = if (colors.isDark) 0.2f else 0.08f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        if (count == 0) {
            Divider()
            Text(
                "No payments ${if (year >= today.year) "scheduled" else "received"} this month",
                color = colors.textSubtle,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )
        } else {
            Divider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(12.dp)) {
                Text("$count payment${if (count == 1) "" else "s"}", color = colors.textSubtle, fontSize = 11.5.sp)
                month.payments.forEach { PaymentCard(it, paid = month.paid || month.current, ctx) }
            }
        }
    }
}

/** One payer, in its own currency — rows are never converted, only the month totals are. */
@Composable
private fun PaymentCard(payment: Payment, paid: Boolean, ctx: CurrencyContext) {
    val colors = Fp.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (paid) 1f else 0.72f)
            .clip(shape)
            .background(colors.surface)
            .then(if (paid) Modifier.border(1.dp, colors.border, shape) else Modifier.dashedBorder(colors.border, 8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        TickerAvatar(payment.ticker, size = 32.dp, color = Color(PORTFOLIO_PALETTE[abs(payment.ticker.hashCode()) % PORTFOLIO_PALETTE.size]))
        HSpace(10.dp)
        Column(Modifier.weight(1f)) {
            Text(payment.ticker, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatCalendarShares(payment.shares)} ${if (payment.shares == 1.0) "share" else "shares"} @ ${ctx.money(payment.perShare, payment.currency, 2)}",
                style = FpType.number(11.5.sp, FontWeight.Normal, colors.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HSpace(8.dp)
        Text(
            ctx.money(payment.amount, payment.currency, 2),
            style = FpType.number(13.5.sp, FontWeight.Bold, if (paid) colors.gain else colors.textMuted),
        )
    }
}
