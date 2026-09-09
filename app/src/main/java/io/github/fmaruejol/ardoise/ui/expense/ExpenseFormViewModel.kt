package io.github.fmaruejol.ardoise.ui.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.api.ExchangeRates
import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.currency.asStoredRate
import io.github.fmaruejol.ardoise.core.currency.convertToGroupCurrency
import io.github.fmaruejol.ardoise.core.currency.formatForEditing
import io.github.fmaruejol.ardoise.core.currency.formatPlain
import io.github.fmaruejol.ardoise.core.currency.parse
import io.github.fmaruejol.ardoise.core.currency.parseConversionRate
import io.github.fmaruejol.ardoise.core.currency.parseDecimal
import io.github.fmaruejol.ardoise.core.currency.toMinorUnits
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.settlement.distributeAmount
import io.github.fmaruejol.ardoise.data.CategoryRepository
import io.github.fmaruejol.ardoise.data.Created
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

sealed interface ExpenseFormError {
    data object Title : ExpenseFormError

    data object Amount : ExpenseFormError

    /** An expense in another currency, with no usable rate to convert it. */
    data object Rate : ExpenseFormError

    data object NobodyPaidFor : ExpenseFormError

    /** The shares do not add up to what the server requires for this mode. */
    data class SplitTotal(val assigned: Long, val required: Long) : ExpenseFormError

    data class Failed(val error: SpliitError) : ExpenseFormError

    /** Where the field sits on the form, top first: a set has no order. */
    val fieldOrder: Int
        get() = when (this) {
            Amount -> 0

            Rate -> 1

            Title -> 2

            NobodyPaidFor, is SplitTotal -> 3

            // Belongs to no field; reported at the foot of the form.
            is Failed -> Int.MAX_VALUE
        }
}

data class ExpenseFormUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /**
     * True when the form was opened on a payment from "Mark as paid".
     * The amount is filled in, so the keyboard does not come up on it.
     */
    val isPrefilled: Boolean = false,
    val title: String = "",
    /** As typed, in major units of [entryCurrency]. Never a Double. */
    val amountText: String = "",
    /** The group's currency: what the expense is stored and split in. */
    val currency: GroupCurrency = GroupCurrency.of(null, "€"),
    /**
     * The currency the amount is *typed* in: the group's unless the user
     * picked another, in which case the original is stored beside the
     * converted amount, as upstream does.
     */
    val entryCurrency: GroupCurrency = GroupCurrency.of(null, "€"),
    val pickingEntryCurrency: Boolean = false,
    /**
     * The rate as the user's locale writes it. **"1 [entryCurrency] = rate
     * [currency]"**, upstream's direction, and the one it is stored in.
     */
    val conversionRateText: String = "",
    /** Whether the rate dialog is open. */
    val editingRate: Boolean = false,
    /** The dialog's own copy, so leaving it alone leaves the rate alone. */
    val rateDraft: String = "",
    val fetchingRate: Boolean = false,
    /** Set when a look-up came back with nothing; cleared by typing. */
    val rateUnavailable: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val categoryId: Int = 0,
    val categories: List<Category> = emptyList(),
    val participants: List<Participant> = emptyList(),
    val paidById: String = "",
    /**
     * Which participant this device says the user is. Distinct from
     * [paidById]: marking the payer instead would call whoever paid "you".
     */
    val activeParticipantId: String? = null,
    val splitMode: SplitMode = SplitMode.EVENLY,
    /**
     * Who the expense is for. The value is the share **in the stored
     * representation for [splitMode]**. See `PaidFor` in `:core`.
     */
    val paidFor: Map<String, Long> = emptyMap(),
    /** What is typed in the split editor, before it becomes a share. */
    val splitText: Map<String, String> = emptyMap(),
    val notes: String = "",
    val showNotes: Boolean = false,
    /** The server creates the copies on the day; this only says what it should do. */
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    /** Money moving between two people. It moves balances but is not spending. */
    val isReimbursement: Boolean = false,
    val pickingCategory: Boolean = false,
    val pickingDate: Boolean = false,
    val pickingPayer: Boolean = false,
    val editingSplit: Boolean = false,
    val editingRecurrence: Boolean = false,
    val isSaving: Boolean = false,
    val loadError: SpliitError? = null,
    /**
     * Everything wrong with the form, not merely the first thing: a form with
     * no title and no amount marks both.
     */
    val errors: Set<ExpenseFormError> = emptySet(),
    /**
     * Refused saves. The screen scrolls to [firstError] when this changes.
     * Saving twice unchanged has the same errors, so they cannot say it.
     */
    val refusedSaves: Int = 0,
    /** Set once saved; consumed by the screen to leave. */
    val savedId: String? = null,
    /** True when the save was queued rather than sent; the screen says so. */
    val wasQueued: Boolean = false,
    /** Set once deleted; consumed by the screen to leave. */
    val isDeleted: Boolean = false,
) {
    /**
     * True when the amount is typed in something other than the group's
     * currency. Both sides need an ISO code: a group with only a custom symbol
     * has no scale to convert into.
     */
    val isConverted: Boolean
        get() = currency.code != null &&
            entryCurrency.code != null &&
            entryCurrency.code != currency.code

    /** Whether the currency can be changed at all. See [isConverted]. */
    val canChooseCurrency: Boolean get() = currency.code != null

    /** What was typed, in minor units of [entryCurrency]. */
    val enteredAmount: Long? get() = entryCurrency.parse(amountText)

    val conversionRate: BigDecimal? get() = parseConversionRate(conversionRateText)

    /**
     * The amount in the group's currency, what is saved, split and balanced,
     * so nothing downstream needs to know a conversion happened.
     */
    val amount: Long
        get() = when {
            !isConverted -> enteredAmount ?: 0L

            else -> conversionRate?.let { rate ->
                convertToGroupCurrency(enteredAmount ?: 0L, entryCurrency, rate, currency)
            } ?: 0L
        }

    val category: Category? get() = categories.firstOrNull { it.id == categoryId }

    val payer: Participant? get() = participants.firstOrNull { it.id == paidById }

    fun participant(id: String): Participant? = participants.firstOrNull { it.id == id }

    val canSave: Boolean get() = !isSaving && !isLoading

    /**
     * The topmost field that is wrong, or null. [Failed][ExpenseFormError.Failed]
     * is left out: it is reported at the foot of the form.
     */
    val firstError: ExpenseFormError?
        get() = errors.filterNot { it is ExpenseFormError.Failed }.minByOrNull { it.fieldOrder }

    /** The one complaint about [field], or null when there is none. */
    fun errorOf(field: ExpenseFormError): ExpenseFormError? = field.takeIf { it in errors }

    /** How much the editor's rows currently add up to, in the mode's own unit. */
    val assigned: Long get() = paidFor.values.sum()

    /** What they have to add up to, asked of [SplitMode.requiredTotal]. */
    val requiredTotal: Long? get() = splitMode.requiredTotal(amount)

    val excluded: List<Participant>
        get() = participants.filterNot { it.id in paidFor.keys }
}

/**
 * Creating or editing an expense.
 *
 * Money goes through `:core`'s currency helpers: integers in the group's minor
 * unit, the scale from its ISO code, and a share whose meaning depends on the
 * split mode. Getting any of it wrong is a quietly wrong balance.
 */
class ExpenseFormViewModel(
    private val groupId: String,
    private val expenseId: String?,
    /** Set when the form opened on a queued expense. Not an expense id. */
    private val pendingId: String?,
    /** Set when the form was opened from "Mark as paid" on the settle-up screen. */
    private val prefill: ReimbursementPrefill?,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val categories: CategoryRepository,
    private val rates: ExchangeRates,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(ExpenseFormUiState(isNew = expenseId == null))
    val state: StateFlow<ExpenseFormUiState> = _state.asStateFlow()

    private val restored: ExpenseFormDraft? = savedState[DRAFT]

    init {
        viewModelScope.launch {
            when (val result = categories.categories().first()) {
                is SpliitResult.Success -> _state.update { it.copy(categories = result.value) }

                // The form works without them; the category stays "General".
                is SpliitResult.Failure -> Unit
            }
        }
        viewModelScope.launch {
            load()
            _state.collect { savedState[DRAFT] = it.draft() }
        }
    }

    private suspend fun load() {
        val group = (groups.group(groupId).first() as? SpliitResult.Success)?.value
        if (group == null) {
            // Nothing failed: this group is simply not on this device.
            _state.update { it.copy(isLoading = false, loadError = SpliitError.NotFound) }
            return
        }

        val queued = pendingId?.let { expenses.pendingExpense(it) }
        if (queued != null) {
            val me = groups.activeParticipantId(groupId).first()
            _state.update { it.forQueued(group, queued.input, me).withDraft(restored) }
            return
        }

        val existing = expenseId?.let {
            when (val result = expenses.expense(groupId, it).first()) {
                is SpliitResult.Success -> {
                    result.value
                }

                is SpliitResult.Failure -> {
                    _state.update { s -> s.copy(isLoading = false, loadError = result.error) }
                    return
                }
            }
        }

        val me = groups.activeParticipantId(groupId).first()
        _state.update { current ->
            if (existing == null) {
                current.forNewExpense(group, me, prefill)
            } else {
                current.forEditing(group, existing, me)
            }.withDraft(restored)
        }
    }

    private fun ExpenseFormUiState.forNewExpense(
        group: Group,
        me: String?,
        prefill: ReimbursementPrefill?,
    ): ExpenseFormUiState {
        val currency = GroupCurrency.of(group.currencyCode, group.currencySymbol)
        val blank = copy(
            isLoading = false,
            currency = currency,
            entryCurrency = currency,
            participants = group.participants,
            // "Paid by" starts on whoever this device says it is, which is the
            // reason the app asks.
            paidById = me ?: group.participants.firstOrNull()?.id.orEmpty(),
            activeParticipantId = me,
            splitMode = SplitMode.EVENLY,
            paidFor = group.participants.associate { it.id to 1L },
        )

        // Both sides have to still be in the group: a prefill naming somebody
        // removed would save against an id the server rejects.
        val ids = group.participants.map { it.id }.toSet()
        if (prefill == null || prefill.payerId !in ids || prefill.payeeId !in ids) return blank

        return blank.copy(
            isPrefilled = true,
            title = prefill.title,
            // Major units, like everything else the user edits here.
            amountText = currency.formatPlain(prefill.amount),
            paidById = prefill.payerId,
            // Split across the group it would move everyone else's balance.
            splitMode = SplitMode.EVENLY,
            paidFor = mapOf(prefill.payeeId to 1L),
            isReimbursement = true,
        )
    }

    /** An expense that never reached the server, read back out of the queue. */
    private fun ExpenseFormUiState.forQueued(
        group: Group,
        input: ExpenseInput,
        me: String?,
    ): ExpenseFormUiState {
        val currency = GroupCurrency.of(group.currencyCode, group.currencySymbol)
        val entry = entryCurrencyFor(input.originalCurrency, currency)
        return copy(
            isLoading = false,
            isNew = false,
            activeParticipantId = me,
            currency = currency,
            entryCurrency = entry,
            conversionRateText = rateText(input.conversionRate),
            participants = group.participants,
            title = input.title,
            amountText = entry.formatPlain(input.originalAmount ?: input.amount),
            date = input.expenseDate,
            categoryId = input.categoryId,
            paidById = input.paidById,
            splitMode = input.splitMode,
            paidFor = input.paidFor.associate { it.participantId to it.shares },
            notes = input.notes.orEmpty(),
            showNotes = !input.notes.isNullOrBlank(),
            recurrenceRule = input.recurrenceRule,
            isReimbursement = input.isReimbursement,
        )
    }

    private fun ExpenseFormUiState.forEditing(
        group: Group,
        expense: Expense,
        me: String?,
    ): ExpenseFormUiState {
        val currency = GroupCurrency.of(group.currencyCode, group.currencySymbol)
        // An expense entered in another currency comes back in that one: the
        // original is what its author typed.
        val entry = entryCurrencyFor(expense.originalCurrency, currency)
        return copy(
            isLoading = false,
            activeParticipantId = me,
            isNew = false,
            currency = currency,
            entryCurrency = entry,
            conversionRateText = rateText(expense.conversionRate),
            participants = group.participants,
            title = expense.title,
            amountText = entry.formatPlain(expense.originalAmount ?: expense.amount),
            date = expense.expenseDate,
            categoryId = expense.category?.id ?: 0,
            paidById = expense.paidById,
            splitMode = expense.splitMode,
            paidFor = expense.paidFor.associate { it.participantId to it.shares },
            notes = expense.notes.orEmpty(),
            showNotes = !expense.notes.isNullOrBlank(),
            recurrenceRule = expense.recurrenceRule,
            isReimbursement = expense.isReimbursement,
        )
    }

    /**
     * The currency an amount was typed in. The symbol is the code, because a
     * currency with an ISO code is formatted from the code.
     */
    private fun entryCurrencyFor(code: String?, group: GroupCurrency): GroupCurrency =
        code?.takeIf { it != group.code }?.let { GroupCurrency.of(it, it) } ?: group

    /** A stored rate as the reader writes numbers, for the field they edit. */
    private fun rateText(stored: String?): String {
        val rate = stored?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return ""
        return formatForEditing(rate)
    }

    private fun ExpenseFormUiState.draft() = ExpenseFormDraft(
        title = title,
        amountText = amountText,
        entryCurrencyCode = entryCurrency.code?.takeIf { it != currency.code },
        conversionRateText = conversionRateText,
        dateEpochDay = date.toEpochDay(),
        categoryId = categoryId,
        paidById = paidById,
        splitMode = splitMode.name,
        paidFor = paidFor,
        splitText = splitText,
        notes = notes,
        showNotes = showNotes,
        recurrenceRule = recurrenceRule.name,
        isReimbursement = isReimbursement,
    )

    private fun ExpenseFormUiState.withDraft(draft: ExpenseFormDraft?): ExpenseFormUiState {
        if (draft == null) return this
        val ids = participants.map { it.id }.toSet()
        return copy(
            title = draft.title,
            amountText = draft.amountText,
            entryCurrency = entryCurrencyFor(draft.entryCurrencyCode, currency),
            conversionRateText = draft.conversionRateText,
            date = draft.date,
            categoryId = draft.categoryId,
            paidById = draft.paidById.takeIf { it in ids } ?: paidById,
            splitMode = enumOrNull<SplitMode>(draft.splitMode) ?: splitMode,
            paidFor = draft.paidFor.filterKeys { it in ids },
            splitText = draft.splitText.filterKeys { it in ids },
            notes = draft.notes,
            showNotes = draft.showNotes,
            recurrenceRule = enumOrNull<RecurrenceRule>(draft.recurrenceRule) ?: recurrenceRule,
            isReimbursement = draft.isReimbursement,
        )
    }

    // --- the form ----------------------------------------------------------

    // Editing a field clears that field's complaint and leaves the others.
    fun onTitleChange(title: String) =
        _state.update { it.copy(title = title, errors = it.errors - ExpenseFormError.Title) }

    fun onAmountChange(text: String) =
        _state.update { it.copy(amountText = text, errors = it.errors - ExpenseFormError.Amount) }

    // --- the currency it was paid in ---------------------------------------

    fun onEntryCurrencyPickerOpen() = _state.update { it.copy(pickingEntryCurrency = true) }

    fun onEntryCurrencyPickerDismiss() = _state.update { it.copy(pickingEntryCurrency = false) }

    /**
     * Choosing the currency the amount is typed in. Back to the group's own
     * clears the rate: it would otherwise be saved with an expense that has no
     * other currency in it.
     */
    fun onEntryCurrencyChange(code: String) {
        val current = _state.value
        val chosen = if (code == current.currency.code) {
            current.currency
        } else {
            GroupCurrency.of(code, code)
        }
        _state.update {
            it.copy(
                entryCurrency = chosen,
                pickingEntryCurrency = false,
                conversionRateText = if (chosen.code == it.currency.code) "" else it.conversionRateText,
                rateUnavailable = false,
                errors = it.errors - ExpenseFormError.Rate,
            )
        }
        // Filled in unasked, as the web client does: the rate is what makes the
        // amount mean anything. The dialog is where it is corrected.
        if (chosen.code != current.currency.code) lookUpRate()
    }

    fun onRateOpen() =
        _state.update { it.copy(editingRate = true, rateDraft = it.conversionRateText) }

    fun onRateDismiss() = _state.update { it.copy(editingRate = false, rateUnavailable = false) }

    fun onRateDraftChange(text: String) =
        _state.update { it.copy(rateDraft = text, rateUnavailable = false) }

    fun onRateSave() {
        _state.update {
            it.copy(
                conversionRateText = it.rateDraft,
                editingRate = false,
                rateUnavailable = false,
                errors = it.errors - ExpenseFormError.Rate,
            )
        }
    }

    /** "Use the day's rate": the one that applied when the money was spent. */
    fun onRateLookUp() = lookUpRate(intoDraft = true)

    private fun lookUpRate(intoDraft: Boolean = false) {
        val current = _state.value
        val base = current.entryCurrency.code ?: return
        val target = current.currency.code ?: return
        if (current.fetchingRate) return

        _state.update { it.copy(fetchingRate = true, rateUnavailable = false) }
        viewModelScope.launch {
            val rate = rates.rate(current.date, base = base, target = target)
            _state.update { state ->
                // The answer is for the currencies chosen when it was asked.
                if (state.entryCurrency.code != base || state.currency.code != target) {
                    return@update state.copy(fetchingRate = false)
                }
                val text = rate?.let { formatForEditing(it) }
                state.copy(
                    fetchingRate = false,
                    rateUnavailable = text == null,
                    rateDraft = if (intoDraft && text != null) text else state.rateDraft,
                    conversionRateText = when {
                        text == null -> state.conversionRateText
                        intoDraft -> state.conversionRateText
                        else -> text
                    },
                    errors = if (text == null) state.errors else state.errors - ExpenseFormError.Rate,
                )
            }
        }
    }

    fun onNotesChange(notes: String) = _state.update { it.copy(notes = notes) }

    fun onNotesToggle() = _state.update { it.copy(showNotes = !it.showNotes) }

    fun onRecurrenceOpen() = _state.update { it.copy(editingRecurrence = true) }

    fun onRecurrenceClose() = _state.update { it.copy(editingRecurrence = false) }

    fun onRecurrenceChange(rule: RecurrenceRule) =
        _state.update { it.copy(recurrenceRule = rule) }

    fun onReimbursementChange(isReimbursement: Boolean) =
        _state.update { it.copy(isReimbursement = isReimbursement) }

    fun onCategoryPickerOpen() = _state.update { it.copy(pickingCategory = true) }

    fun onCategoryPickerDismiss() = _state.update { it.copy(pickingCategory = false) }

    fun onCategoryChange(id: Int) =
        _state.update { it.copy(categoryId = id, pickingCategory = false) }

    fun onDatePickerOpen() = _state.update { it.copy(pickingDate = true) }

    fun onDatePickerDismiss() = _state.update { it.copy(pickingDate = false) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date, pickingDate = false) }

    fun onPayerPickerOpen() = _state.update { it.copy(pickingPayer = true) }

    fun onPayerPickerDismiss() = _state.update { it.copy(pickingPayer = false) }

    fun onPayerChange(participantId: String) =
        _state.update { it.copy(paidById = participantId, pickingPayer = false) }

    // --- the split editor --------------------------------------------------

    fun onSplitEditorOpen() {
        _state.update { it.copy(editingSplit = true, splitText = it.splitTextFor(it.splitMode)) }
    }

    fun onSplitEditorClose() =
        _state.update { it.copy(editingSplit = false, errors = it.errors.withoutSplit()) }

    fun onSplitModeChange(mode: SplitMode) {
        _state.update { current ->
            // Carry the people over, not the numbers: a share of 2 means
            // nothing as a percentage. Everyone kept gets an even value.
            val members = current.paidFor.keys.ifEmpty { current.participants.map { it.id }.toSet() }
            val seeded = current.seed(mode, members)
            current.copy(
                splitMode = mode,
                paidFor = seeded,
                splitText = current.textFrom(mode, seeded),
                errors = current.errors.withoutSplit(),
            )
        }
    }

    /** In `EVENLY`, a participant is either in or out. */
    fun onParticipantToggle(participantId: String) {
        _state.update { current ->
            val paidFor = current.paidFor.toMutableMap()
            if (paidFor.remove(participantId) == null) paidFor[participantId] = 1L
            current.copy(paidFor = paidFor, errors = current.errors.withoutSplit())
        }
    }

    fun onShareChange(participantId: String, text: String) {
        _state.update { current ->
            val shares = current.paidFor.toMutableMap()
            val parsed = current.parseShare(text)
            if (parsed == null || parsed == 0L) shares.remove(participantId) else shares[participantId] = parsed
            current.copy(
                splitText = current.splitText + (participantId to text),
                paidFor = shares,
                errors = current.errors.withoutSplit(),
            )
        }
    }

    // --- saving ------------------------------------------------------------

    fun onSave() {
        val current = _state.value
        if (!current.canSave) return

        // Every rule at once, so the user sees everything that is wrong.
        val errors = mutableSetOf<ExpenseFormError>()

        val title = current.title.trim()
        if (title.isEmpty()) errors += ExpenseFormError.Title

        val entered = current.enteredAmount
        if (entered == null || entered == 0L) errors += ExpenseFormError.Amount

        // A rate is only a rule when there is something to convert.
        val rate = current.conversionRate
        if (current.isConverted && rate == null) errors += ExpenseFormError.Rate

        // What the group is charged, which is what everything below is about.
        val amount = current.amount.takeIf { it != 0L }

        if (current.paidFor.isEmpty()) {
            errors += ExpenseFormError.NobodyPaidFor
        } else if (amount != null && amount != 0L) {
            // The server enforces these sums, so they are checked here where
            // the numbers can be pointed at, and only against an amount there
            // is. The target comes from the same property the editor draws it
            // from, so the two cannot disagree.
            val required = current.requiredTotal
            val assigned = current.paidFor.values.sum()
            if (required != null && assigned != required) {
                errors += ExpenseFormError.SplitTotal(assigned, required)
            }
        }

        if (errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    errors = errors,
                    refusedSaves = it.refusedSaves + 1,
                    // The editor holds the numbers this is about, but only
                    // when it is the whole of what is wrong: over a missing
                    // title it would hide half the answer.
                    editingSplit = errors.singleOrNull() is ExpenseFormError.SplitTotal,
                )
            }
            return
        }

        checkNotNull(amount)

        _state.update { it.copy(isSaving = true, errors = emptySet()) }
        viewModelScope.launch {
            val input = ExpenseInput(
                title = title,
                amount = amount,
                expenseDate = current.date,
                paidById = current.paidById,
                paidFor = current.paidFor.map { (id, share) ->
                    // EVENLY ignores the share, but the field is not optional.
                    PaidFor(id, if (current.splitMode == SplitMode.EVENLY) 1L else share)
                },
                // Kept beside the converted amount as upstream keeps them.
                originalAmount = entered.takeIf { current.isConverted },
                originalCurrency = current.entryCurrency.code.takeIf { current.isConverted },
                conversionRate = rate?.asStoredRate().takeIf { current.isConverted },
                splitMode = current.splitMode,
                categoryId = current.categoryId,
                notes = current.notes.trim().takeIf { it.isNotEmpty() },
                recurrenceRule = current.recurrenceRule,
                isReimbursement = current.isReimbursement,
            )

            when {
                // Editing offline is not queued: the queue holds expenses that
                // do not exist yet, and an edit would need a merge on arrival.
                expenseId != null -> {
                    when (val result = expenses.update(groupId, expenseId, input)) {
                        is SpliitResult.Success -> {
                            _state.update { it.copy(isSaving = false, savedId = result.value) }
                        }

                        is SpliitResult.Failure -> {
                            _state.update {
                                it.copy(isSaving = false, errors = setOf(ExpenseFormError.Failed(result.error)))
                            }
                        }
                    }
                }

                else -> {
                    val result = if (pendingId != null) {
                        expenses.replaceQueued(pendingId, groupId, input)
                    } else {
                        expenses.create(groupId, input)
                    }
                    when (result) {
                        is SpliitResult.Success -> _state.update {
                            it.copy(
                                isSaving = false,
                                savedId = when (val created = result.value) {
                                    is Created.Sent -> created.expenseId
                                    is Created.Queued -> created.pendingId
                                },
                                wasQueued = result.value is Created.Queued,
                            )
                        }

                        is SpliitResult.Failure -> _state.update {
                            it.copy(isSaving = false, errors = setOf(ExpenseFormError.Failed(result.error)))
                        }
                    }
                }
            }
        }
    }

    fun onDelete() {
        // A queued expense is not on the server: throwing the entry away is all.
        pendingId?.let { queued ->
            viewModelScope.launch {
                expenses.discardQueued(queued)
                _state.update { it.copy(isDeleted = true) }
            }
            return
        }

        val id = expenseId ?: return
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, errors = emptySet()) }
        viewModelScope.launch {
            when (val result = expenses.delete(groupId, id)) {
                is SpliitResult.Success -> _state.update { it.copy(isSaving = false, isDeleted = true) }

                is SpliitResult.Failure -> _state.update {
                    it.copy(isSaving = false, errors = setOf(ExpenseFormError.Failed(result.error)))
                }
            }
        }
    }

    fun onNavigationHandled() = _state.update { it.copy(savedId = null, isDeleted = false) }

    // --- turning typed text into stored shares -----------------------------

    /** Reads one row of the split editor into the mode's stored representation. */
    private fun ExpenseFormUiState.parseShare(text: String): Long? = when (splitMode) {
        SplitMode.EVENLY -> {
            1L
        }

        SplitMode.BY_AMOUNT -> {
            currency.parse(text)
        }

        // The scale is the mode's own, read in the locale it was typed in.
        SplitMode.BY_SHARES, SplitMode.BY_PERCENTAGE -> {
            parseDecimal(text)
                ?.multiply(BigDecimal(splitMode.shareScale))
                ?.setScale(0, RoundingMode.HALF_UP)
                ?.toLong()
        }
    }

    private fun ExpenseFormUiState.splitTextFor(mode: SplitMode) = textFrom(mode, paidFor)

    private fun ExpenseFormUiState.textFrom(mode: SplitMode, shares: Map<String, Long>) =
        participants.associate { participant ->
            val share = shares[participant.id]
            participant.id to when {
                share == null -> ""

                mode == SplitMode.BY_AMOUNT -> currency.formatPlain(share)

                mode == SplitMode.EVENLY -> ""

                else -> formatForEditing(
                    BigDecimal(share).divide(BigDecimal(mode.shareScale)),
                )
            }
        }

    /**
     * An even starting point for [members] in [mode], divided by `:core`'s
     * [distributeAmount], the same largest-remainder split the balances use,
     * so the form cannot seed a split it would then refuse.
     */
    private fun ExpenseFormUiState.seed(mode: SplitMode, members: Set<String>): Map<String, Long> =
        when (mode) {
            SplitMode.EVENLY -> members.associateWith { 1L }

            SplitMode.BY_SHARES -> members.associateWith { mode.shareScale }

            // Percentages have a required total, so these add to exactly it.
            SplitMode.BY_PERCENTAGE -> members.evenly(SplitMode.PERCENT_TOTAL)

            SplitMode.BY_AMOUNT -> members.evenly(amount)
        }

    /** [total] divided over [this], adding up to exactly [total]. */
    private fun Set<String>.evenly(total: Long): Map<String, Long> {
        val ids = toList()
        return ids.zip(distributeAmount(total, ids.size)).toMap()
    }

    private companion object {
        const val DRAFT = "expense-form-draft"
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

/** Drops whatever was wrong with how the expense is divided up. */
private fun Set<ExpenseFormError>.withoutSplit(): Set<ExpenseFormError> = filterNot {
    it is ExpenseFormError.NobodyPaidFor || it is ExpenseFormError.SplitTotal
}.toSet()
