package io.github.fmaruejol.ardoise.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.recurrence.nextOccurrence
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.settlement.expenseShares
import io.github.fmaruejol.ardoise.core.settlement.toBalanceInput
import io.github.fmaruejol.ardoise.core.settlement.toShareInput
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.ui.Retry
import io.github.fmaruejol.ardoise.ui.collectOffline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One participant's line on the expense: what they are down for. */
data class ExpenseShareRow(
    val participantId: String,
    val name: String,
    val amount: Long,
    val isYou: Boolean,
)

/**
 * The two ends of a reimbursement. Set only when it really is a payment from
 * one person to one other; anything else keeps the ordinary split card.
 */
data class TransferParties(
    val payerName: String,
    /** Position in the group, so an avatar keeps its colour across screens. */
    val payerIndex: Int,
    val payerIsYou: Boolean,
    val payeeName: String,
    val payeeIndex: Int,
    val payeeIsYou: Boolean,
)

/**
 * What an expense was paid in, when that is not what the group counts in. The
 * rate reads "1 [currency] = [rate] group currency", as it is stored.
 */
data class OriginalAmount(
    val amount: Long,
    val currency: GroupCurrency,
    /** Exact decimal text, as the server holds it. Never a Double. */
    val rate: String,
)

data class ExpenseDetailUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val amount: Long = 0,
    val currency: GroupCurrency = GroupCurrency.of(null, "€"),
    val category: Category? = null,
    val date: LocalDate = LocalDate.now(),
    val payerName: String = "",
    val isPaidByYou: Boolean = false,
    val splitMode: SplitMode = SplitMode.EVENLY,
    /**
     * How often it repeats. Shown here because it is the one thing about an
     * expense the feed cannot show: two identical rows a week apart are a
     * standing order or a coincidence.
     */
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    /** Set only for an expense entered in another currency, which is stored. */
    val original: OriginalAmount? = null,
    /**
     * When the server will make the next copy, or null when this screen cannot
     * honestly say. A prediction, dropped once the date has passed: only the
     * chain the server keeps knows where the series has got to.
     */
    val nextCopy: LocalDate? = null,
    /** Every participant on the expense, with the part of it that is theirs. */
    val shares: List<ExpenseShareRow> = emptyList(),
    val notes: String = "",
    /** Money moving between two people: the screen reads differently. */
    val isReimbursement: Boolean = false,
    /** Who paid whom, when the expense is a reimbursement that names two people. */
    val transfer: TransferParties? = null,
    val isDeleting: Boolean = false,
    val confirmingDelete: Boolean = false,
    val loadError: SpliitError? = null,
    val notFound: Boolean = false,
    val deleteError: SpliitError? = null,
    /** Set once it is gone; consumed by the screen to leave. */
    val isDeleted: Boolean = false,
    /**
     * True once the expense has been read, from the cache or the server.
     * Distinct from `loadError == null`: a cached expense followed by a failed
     * refresh sets both, and keying on the error blanked a good expense.
     */
    val isLoaded: Boolean = false,
    /** Read only to raise the offline banner, never to block anything. */
    val isOffline: Boolean = false,
)

/**
 * One expense, read. The per-participant amounts are [expenseShares], the
 * same function the balances are built on, so this screen cannot apportion an
 * expense its own way.
 */
class ExpenseDetailViewModel(
    private val groupId: String,
    private val expenseId: String,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(ExpenseDetailUiState())
    val state: StateFlow<ExpenseDetailUiState> = _state.asStateFlow()

    private val retry = Retry()

    init {
        collectOffline(connectivity, _state) { copy(isOffline = it) }
    }

    init {
        viewModelScope.launch {
            retry.restarting {
                combine(
                    groups.group(groupId),
                    expenses.expense(groupId, expenseId),
                    groups.activeParticipantId(groupId),
                ) { group, expense, me -> Triple(group, expense, me) }
            }
                .collect { (groupResult, expenseResult, me) ->
                    val group = (groupResult as? SpliitResult.Success)?.value
                    when {
                        expenseResult is SpliitResult.Failure -> {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    // A deleted expense is a state to render.
                                    notFound = expenseResult.error is SpliitError.NotFound,
                                    loadError = expenseResult.error
                                        .takeUnless { e -> e is SpliitError.NotFound },
                                )
                            }
                        }

                        group == null -> {
                            _state.update { it.copy(isLoading = false, notFound = true) }
                        }

                        expenseResult is SpliitResult.Success -> {
                            _state.update { it.from(group, expenseResult.value, me) }
                        }
                    }
                }
        }
    }

    fun onDeleteClick() = _state.update { it.copy(confirmingDelete = true) }

    fun onDeleteDismiss() = _state.update { it.copy(confirmingDelete = false) }

    fun onDeleteConfirm() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(confirmingDelete = false, isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = expenses.delete(groupId, expenseId)) {
                is SpliitResult.Success -> {
                    _state.update { it.copy(isDeleting = false, isDeleted = true) }
                }

                is SpliitResult.Failure -> {
                    _state.update { it.copy(isDeleting = false, deleteError = result.error) }
                }
            }
        }
    }

    fun onNavigationHandled() = _state.update { it.copy(isDeleted = false) }

    fun onRetry() = retry.again()

    private fun ExpenseDetailUiState.from(
        group: Group,
        expense: Expense,
        me: String?,
    ): ExpenseDetailUiState {
        val currency = GroupCurrency.of(group.currencyCode, group.currencySymbol)
        val shares = expenseShares(expense.toBalanceInput().toShareInput())
        return copy(
            isLoading = false,
            isLoaded = true,
            notFound = false,
            loadError = null,
            title = expense.title,
            amount = expense.amount,
            currency = currency,
            category = expense.category,
            date = expense.expenseDate,
            payerName = group.participant(expense.paidById)?.name.orEmpty(),
            isPaidByYou = me != null && expense.paidById == me,
            splitMode = expense.splitMode,
            recurrenceRule = expense.recurrenceRule,
            original = expense.original(),
            nextCopy = nextOccurrence(expense.expenseDate, expense.recurrenceRule)
                ?.takeUnless { it.isBefore(LocalDate.now()) },
            isReimbursement = expense.isReimbursement,
            transfer = expense.transferParties(group, me),
            // The group's order, not the order the database returned.
            shares = group.participants
                .filter { it.id in shares.keys }
                .map { participant ->
                    ExpenseShareRow(
                        participantId = participant.id,
                        name = participant.name,
                        amount = shares[participant.id] ?: 0L,
                        isYou = participant.id == me,
                    )
                },
            notes = expense.notes.orEmpty(),
        )
    }

    /**
     * What was paid, in what, at what rate, or null unless all three are
     * there: an amount with no currency names nothing.
     */
    private fun Expense.original(): OriginalAmount? {
        val amount = originalAmount ?: return null
        val code = originalCurrency ?: return null
        val rate = conversionRate ?: return null
        return OriginalAmount(amount, GroupCurrency.of(code, code), rate)
    }

    /**
     * The payer and the one person paid, or null when this is not a
     * two-person payment.
     */
    private fun Expense.transferParties(group: Group, me: String?): TransferParties? {
        if (!isReimbursement) return null
        val payeeId = paidFor.singleOrNull()?.participantId ?: return null
        // Paying yourself is not a transfer: "Ana pays Ana" with a minus and a
        // plus against one name.
        if (payeeId == paidById) return null
        val payerIndex = group.participants.indexOfFirst { it.id == paidById }
        val payeeIndex = group.participants.indexOfFirst { it.id == payeeId }
        if (payerIndex < 0 || payeeIndex < 0) return null

        return TransferParties(
            payerName = group.participants[payerIndex].name,
            payerIndex = payerIndex,
            payerIsYou = paidById == me,
            payeeName = group.participants[payeeIndex].name,
            payeeIndex = payeeIndex,
            payeeIsYou = payeeId == me,
        )
    }
}
