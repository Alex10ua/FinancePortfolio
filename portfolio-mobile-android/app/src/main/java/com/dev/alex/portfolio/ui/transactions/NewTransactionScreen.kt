package com.dev.alex.portfolio.ui.transactions

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.alex.portfolio.AppContainer
import com.dev.alex.portfolio.Screen
import com.dev.alex.portfolio.data.api.CustomAssetDto
import com.dev.alex.portfolio.data.api.RequestRejectedException
import com.dev.alex.portfolio.data.api.SessionExpiredException
import com.dev.alex.portfolio.data.api.TickerSuggestionDto
import com.dev.alex.portfolio.data.api.describeError
import com.dev.alex.portfolio.data.api.wasNeverSent
import com.dev.alex.portfolio.domain.AssetKind
import com.dev.alex.portfolio.domain.TRANSACTION_CURRENCIES
import com.dev.alex.portfolio.domain.TransactionDraft
import com.dev.alex.portfolio.domain.formatMoney
import com.dev.alex.portfolio.domain.normalizeCurrency
import com.dev.alex.portfolio.domain.toMajorUnits
import com.dev.alex.portfolio.domain.toRequest
import com.dev.alex.portfolio.domain.validate
import com.dev.alex.portfolio.ui.common.screenViewModel
import com.dev.alex.portfolio.ui.components.Divider
import com.dev.alex.portfolio.ui.components.FpCard
import com.dev.alex.portfolio.ui.components.FpIcon
import com.dev.alex.portfolio.ui.components.FpTextField
import com.dev.alex.portfolio.ui.components.HSpace
import com.dev.alex.portfolio.ui.components.PrimaryButton
import com.dev.alex.portfolio.ui.components.SecondaryButton
import com.dev.alex.portfolio.ui.components.Segmented
import com.dev.alex.portfolio.ui.components.TickerAvatar
import com.dev.alex.portfolio.ui.components.VSpace
import com.dev.alex.portfolio.ui.icons.FpIcons
import com.dev.alex.portfolio.ui.shell.IconAction
import com.dev.alex.portfolio.ui.shell.ShellNav
import com.dev.alex.portfolio.ui.theme.Brand
import com.dev.alex.portfolio.ui.theme.Fp
import com.dev.alex.portfolio.ui.theme.FpType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val SEARCH_DEBOUNCE_MS = 250L
private const val MAX_SUGGESTIONS = 8

/** Why the last Save did not simply succeed. */
sealed interface SaveProblem {
    val text: String

    /** Nothing was stored: fix the form, or try again. */
    data class NotSaved(override val text: String) : SaveProblem

    /** The request may have reached the server, and sending it again could book it twice. */
    data object Unknown : SaveProblem {
        override val text =
            "No answer from the server, so it may or may not have been saved. Check Transactions before you save again."
    }
}

/**
 * The add-transaction form. One view-model per opening ([Screen.NewTransaction.openedAt]),
 * so a new form never starts with the last one's fields. A save in flight keeps going if
 * the user leaves: the screen store outlives the page, and the lists reload once it lands.
 */
class NewTransactionViewModel(private val app: AppContainer, private val portfolioId: String) : ViewModel() {
    var draft by mutableStateOf(TransactionDraft(date = LocalDate.now()))
        private set
    var suggestions by mutableStateOf<List<TickerSuggestionDto>>(emptyList())
        private set

    /** The suggestion the ticker came from, kept only for the "last price" hint. */
    var picked by mutableStateOf<TickerSuggestionDto?>(null)
        private set

    /** null while loading */
    var customAssets by mutableStateOf<List<CustomAssetDto>?>(null)
        private set
    var customAssetsError by mutableStateOf<String?>(null)
        private set
    var submitting by mutableStateOf(false)
        private set
    var problem by mutableStateOf<SaveProblem?>(null)
        private set

    /** Set once the server stored it, to the backend's `holdingSynced`. */
    var saved by mutableStateOf<Boolean?>(null)
        private set

    private var searchJob: Job? = null
    private var customJob: Job? = null

    fun edit(change: (TransactionDraft) -> TransactionDraft) {
        draft = change(draft)
        // a validation or refusal message is about the old values; "no answer" still stands
        if (problem is SaveProblem.NotSaved) problem = null
    }

    fun setKind(kind: AssetKind) {
        edit { it.withKind(kind) }
        picked = null
        clearSuggestions()
        if (kind == AssetKind.Custom) loadCustomAssets()
    }

    fun setTicker(text: String) {
        edit { it.copy(ticker = text) }
        if (picked?.ticker != text) picked = null
        search(text)
    }

    fun pick(suggestion: TickerSuggestionDto) {
        picked = suggestion
        edit { it.copy(ticker = suggestion.ticker, currency = bookCurrency(suggestion.currency) ?: it.currency) }
        clearSuggestions()
    }

    /** Only on request, as on the web: a trade dated last year must not take today's price. */
    fun usePickedPrice() {
        val hit = picked ?: return
        val last = hit.price ?: return
        edit { it.copy(price = toMajorUnits(last, hit.currency).toBigDecimal().stripTrailingZeros().toPlainString()) }
    }

    fun pickCustom(asset: CustomAssetDto) {
        edit { it.copy(ticker = asset.ticker, currency = bookCurrency(asset.currency) ?: it.currency) }
    }

    fun retryCustomAssets() = loadCustomAssets()

    /** Transactions book in major units: a GBp quote is a GBP trade. */
    private fun bookCurrency(code: String?): String? =
        code?.let(::normalizeCurrency)?.takeIf { it in TRANSACTION_CURRENCIES }

    private fun clearSuggestions() {
        searchJob?.cancel()
        suggestions = emptyList()
    }

    private fun search(text: String) {
        searchJob?.cancel()
        val query = text.trim()
        val searchable = draft.kind == AssetKind.Stock || draft.kind == AssetKind.Crypto
        if (!searchable || query.length < 2) {
            suggestions = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            suggestions = try {
                app.repository.searchTickers(portfolioId, query).take(MAX_SUGGESTIONS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the field still takes any symbol: the backend fetches one it has not seen
                emptyList()
            }
        }
    }

    private fun loadCustomAssets() {
        if (customAssets != null || customJob?.isActive == true) return
        customAssetsError = null
        customJob = viewModelScope.launch {
            try {
                val list = app.repository.customAssets(portfolioId).value
                    .sortedBy { it.name.ifBlank { it.ticker }.lowercase() }
                customAssets = list
                if (draft.kind == AssetKind.Custom && draft.ticker.isBlank()) list.firstOrNull()?.let(::pickCustom)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                app.sessionExpired.tryEmit(Unit)
            } catch (e: Exception) {
                customAssetsError = describeError(e)
            }
        }
    }

    fun submit() {
        if (submitting || saved != null) return
        val invalid = validate(draft, LocalDate.now())
        if (invalid != null) {
            problem = SaveProblem.NotSaved(invalid)
            return
        }
        val request = draft.toRequest()
        submitting = true
        problem = null
        viewModelScope.launch {
            try {
                saved = app.repository.createTransaction(portfolioId, request)
                app.dataVersion.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                problem = SaveProblem.NotSaved("Your session ended, so nothing was saved. Unlock and try again.")
                app.sessionExpired.tryEmit(Unit)
            } catch (e: RequestRejectedException) {
                problem = SaveProblem.NotSaved("Not saved: ${e.message}")
            } catch (e: Exception) {
                if (wasNeverSent(e)) {
                    problem = SaveProblem.NotSaved("Not saved. ${describeError(e)}")
                } else {
                    problem = SaveProblem.Unknown
                    // if it did land, the lists should show it when the user goes to check
                    app.dataVersion.update { it + 1 }
                }
            } finally {
                submitting = false
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val DECIMAL = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

/**
 * The mockups' "Add Transaction" dialog (component-sheet.jsx) as a full-screen form, opened
 * from the Transactions FAB: title bar with a close X, the fields in a card, Cancel and
 * Save in a footer that stays above the keyboard.
 */
@Composable
fun NewTransactionScreen(screen: Screen.NewTransaction, nav: ShellNav) {
    val vm = screenViewModel("new-transaction:${screen.openedAt}") { NewTransactionViewModel(it, screen.portfolioId) }
    val context = LocalContext.current
    val saved = vm.saved
    LaunchedEffect(saved) {
        if (saved == null) return@LaunchedEffect
        val text = if (saved) "Transaction saved" else "Saved. Its holding updates on the next trade in this ticker."
        Toast.makeText(context, text, if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        nav.back()
    }

    val colors = Fp.colors
    val draft = vm.draft
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.pageBg)
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconAction(FpIcons.X, "Close", onClick = nav.back)
            Column(Modifier.weight(1f)) {
                Text("Add Transaction", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    nav.portfolioName(screen.portfolioId),
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Divider()

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            FpCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field("Asset") {
                        Segmented(
                            options = AssetKind.entries.map { it.label },
                            active = draft.kind.label,
                            onChange = { label -> vm.setKind(AssetKind.entries.first { it.label == label }) },
                            fill = true,
                        )
                    }
                    Field("Type") {
                        Segmented(
                            options = draft.kind.types,
                            active = draft.type,
                            onChange = { type -> vm.edit { it.copy(type = type) } },
                        )
                    }
                    when (draft.kind) {
                        AssetKind.Cash -> FpTextField(
                            value = draft.amount,
                            onValueChange = { value -> vm.edit { it.copy(amount = value) } },
                            label = "Amount",
                            placeholder = "500.00",
                            keyboardOptions = DECIMAL,
                        )
                        AssetKind.Custom -> CustomAssetField(vm)
                        else -> TickerField(vm)
                    }
                    Field("Currency") {
                        Segmented(
                            options = TRANSACTION_CURRENCIES,
                            active = draft.currency,
                            onChange = { code -> vm.edit { it.copy(currency = code) } },
                        )
                    }
                    DateField(draft.date) { day -> vm.edit { it.copy(date = day) } }
                    if (draft.kind != AssetKind.Cash) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FpTextField(
                                value = draft.quantity,
                                onValueChange = { value -> vm.edit { it.copy(quantity = value) } },
                                modifier = Modifier.weight(1f),
                                label = if (draft.kind == AssetKind.Stock) "Shares" else "Quantity",
                                placeholder = "10",
                                keyboardOptions = DECIMAL,
                            )
                            FpTextField(
                                value = draft.price,
                                onValueChange = { value -> vm.edit { it.copy(price = value) } },
                                modifier = Modifier.weight(1f),
                                label = priceLabel(draft),
                                placeholder = "0.00",
                                keyboardOptions = DECIMAL,
                            )
                        }
                        FpTextField(
                            value = draft.commission,
                            onValueChange = { value -> vm.edit { it.copy(commission = value) } },
                            label = "Commission",
                            placeholder = "0.00",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Total",
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                draft.total()?.let { formatMoney(it.toDouble(), draft.currency) } ?: "—",
                                style = FpType.mono(14.sp, FontWeight.SemiBold, colors.text),
                            )
                        }
                    }
                }
            }
        }

        vm.problem?.let { ProblemLine(it) }
        Divider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            SecondaryButton("Cancel", onClick = nav.back)
            PrimaryButton(
                text = when {
                    vm.submitting -> "Saving…"
                    vm.problem == SaveProblem.Unknown -> "Save again"
                    else -> "Save Transaction"
                },
                onClick = vm::submit,
                enabled = !vm.submitting,
            )
        }
    }
}

private fun priceLabel(draft: TransactionDraft): String = when {
    draft.kind != AssetKind.Stock -> "Price"
    draft.type == "DIVIDEND" -> "Dividend per share"
    draft.type == "TAX" -> "Tax per share"
    else -> "Price per share"
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, color = Fp.colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        VSpace(6.dp)
        content()
    }
}

@Composable
private fun TickerField(vm: NewTransactionViewModel) {
    val colors = Fp.colors
    val draft = vm.draft
    Column {
        FpTextField(
            value = draft.ticker,
            onValueChange = vm::setTicker,
            label = "Ticker",
            placeholder = if (draft.kind == AssetKind.Crypto) "e.g. BTC, ETH" else "Ticker or company, e.g. AAPL",
            icon = FpIcons.Search,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
        )
        val suggestions = vm.suggestions
        if (suggestions.isNotEmpty()) {
            VSpace(6.dp)
            val shape = RoundedCornerShape(6.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, colors.border, shape),
            ) {
                suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) Divider()
                    SuggestionRow(suggestion) { vm.pick(suggestion) }
                }
            }
        }
        val hit = vm.picked
        val last = hit?.price
        if (hit != null && last != null) {
            VSpace(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Last ${formatMoney(toMajorUnits(last, hit.currency), normalizeCurrency(hit.currency))}",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                )
                HSpace(6.dp)
                Text(
                    "Use as price",
                    color = Brand.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = vm::usePickedPrice)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: TickerSuggestionDto, onClick: () -> Unit) {
    val colors = Fp.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        TickerAvatar(suggestion.ticker, size = 24.dp, color = Brand.Primary)
        HSpace(10.dp)
        Column(Modifier.weight(1f)) {
            Text(suggestion.ticker, style = FpType.mono(12.5.sp, FontWeight.Bold, colors.text), maxLines = 1)
            val name = suggestion.name
            if (!name.isNullOrBlank()) {
                Text(name, color = colors.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val price = suggestion.price
        if (price != null) {
            HSpace(8.dp)
            Text(
                formatMoney(toMajorUnits(price, suggestion.currency), normalizeCurrency(suggestion.currency)),
                style = FpType.mono(12.sp, FontWeight.Medium, colors.textMuted),
            )
        }
    }
}

/** Custom assets are picked from the portfolio's definitions; creating one stays on the web. */
@Composable
private fun CustomAssetField(vm: NewTransactionViewModel) {
    val colors = Fp.colors
    val assets = vm.customAssets
    val error = vm.customAssetsError
    Field("Custom asset") {
        when {
            error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = colors.loss, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                HSpace(8.dp)
                SecondaryButton("Retry", onClick = vm::retryCustomAssets)
            }
            assets == null -> Text("Loading…", color = colors.textMuted, fontSize = 13.sp)
            assets.isEmpty() -> Text(
                "This portfolio has no custom assets yet. Create one in the web app first.",
                color = colors.textMuted,
                fontSize = 12.5.sp,
            )
            else -> {
                val current = assets.firstOrNull { it.ticker.equals(vm.draft.ticker, ignoreCase = true) }
                SelectField(
                    value = current?.let(::customLabel) ?: "Choose an asset",
                    options = assets.map(::customLabel),
                ) { index -> vm.pickCustom(assets[index]) }
            }
        }
    }
}

private fun customLabel(asset: CustomAssetDto): String =
    if (asset.name.isBlank()) asset.ticker else "${asset.name} (${asset.ticker})"

@Composable
private fun SelectField(value: String, options: List<String>, icon: ImageVector? = null, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FieldBox(value, icon) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        open = false
                        onPick(index)
                    },
                )
            }
        }
    }
}

/** A field that opens a picker instead of a keyboard, drawn like [FpTextField]. */
@Composable
private fun FieldBox(value: String, icon: ImageVector?, onClick: () -> Unit) {
    val colors = Fp.colors
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            FpIcon(icon, size = 14.dp, tint = colors.textSubtle)
            HSpace(10.dp)
        }
        Text(
            value,
            color = colors.text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FpIcon(FpIcons.ChevDown, size = 14.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Field("Date") {
        FieldBox(date.format(DAY), FpIcons.Calendar) { open = true }
    }
    if (open) {
        val today = remember { LocalDate.now() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcMillis(),
            selectableDates = remember(today) { PastDates(today) },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onPick(utcMillisToDate(it)) }
                        open = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** No future days: a trade, dividend or deposit being booked has already happened. */
@OptIn(ExperimentalMaterial3Api::class)
private class PastDates(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today.toUtcMillis()

    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

/**
 * The date picker speaks UTC midnights. Converting through UTC on both sides keeps the
 * calendar day exact in any time zone; local midnight would shift it a day east of UTC.
 */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun ProblemLine(problem: SaveProblem) {
    val colors = Fp.colors
    val tone = if (problem is SaveProblem.Unknown) colors.over else colors.loss
    Text(
        problem.text,
        color = tone,
        fontSize = 12.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
