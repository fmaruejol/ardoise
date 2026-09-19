package io.github.fmaruejol.ardoise.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.settlement.participantBalanceChange
import io.github.fmaruejol.ardoise.core.settlement.participantShare
import io.github.fmaruejol.ardoise.core.settlement.toBalanceInput
import io.github.fmaruejol.ardoise.core.settlement.toShareInput
import io.github.fmaruejol.ardoise.data.CategoryRepository
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.PendingExpense
import io.github.fmaruejol.ardoise.data.RefreshScope
import io.github.fmaruejol.ardoise.ui.Retry
import io.github.fmaruejol.ardoise.ui.collectOffline
import io.github.fmaruejol.ardoise.ui.collectRefreshing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The Date chip's presets: the feed answers "what have we spent lately". */
enum class DateFilter {
    AnyTime,
    ThisMonth,
    Last30Days,
    ThisYear,
    ;

    fun matches(date: LocalDate, today: LocalDate): Boolean = when (this) {
        AnyTime -> true

        ThisMonth -> date.year == today.year && date.month == today.month

        // Inclusive of today, so "last 30 days" is 30 and not 31.
        Last30Days -> !date.isBefore(today.minusDays(29)) && !date.isAfter(today)

        ThisYear -> date.year == today.year
    }
}

/** What the chip row is currently narrowing the feed down to. */
data class ExpenseFilters(
    val categoryId: Int? = null,
    val payerId: String? = null,
    val date: DateFilter = DateFilter.AnyTime,
) {
    val isActive: Boolean
        get() = categoryId != null || payerId != null || date != DateFilter.AnyTime
}

/** Expenses of one day, as the feed groups them. */
data class ExpenseDay(
    val date: LocalDate,
    val expenses: List<ExpenseSummary>,
)

data class GroupUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val currency: GroupCurrency = GroupCurrency.of(null, "€"),
    val days: List<ExpenseDay> = emptyList(),
    val expenseCount: Int = 0,
    val totalSpent: Long = 0,
    /** The sum of this device's own shares, or null while nobody has said who. */
    val yourShare: Long? = null,
    val activeParticipantId: String? = null,
    /**
     * What each expense does to this device's balance, by expense id. Not the
     * same as your *share*: paying for four others is both a large share and a
     * large gain.
     */
    val yourBalanceChanges: Map<String, Long> = emptyMap(),
    val hasMore: Boolean = false,
    val isSearching: Boolean = false,
    val query: String = "",
    val filters: ExpenseFilters = ExpenseFilters(),
    /**
     * The categories this group actually uses, for the chip. The server's full
     * list would put dozens in front of someone whose trip has four.
     */
    val categories: List<Category> = emptyList(),
    val participants: List<Participant> = emptyList(),
    /**
     * Expenses typed here while offline. Not in [days]: they are not expenses
     * yet, and the totals would report money the group has not been told of.
     */
    val pending: List<PendingExpense> = emptyList(),
    /** The whole category list, only so a queued expense can be given its icon. */
    val allCategories: List<Category> = emptyList(),
    /** The "which one is you" dialog, opened from the feed's own prompt. */
    val pickingYou: Boolean = false,
    /** Read only to raise the offline banner over rows from the cache. */
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: SpliitError? = null,
) {
    /**
     * True once the group is loaded and nobody has said who they are. Not
     * merely `activeParticipantId == null`, which is also true before the
     * group arrives, and a prompt that flashes teaches people to dismiss it.
     */
    val needsIdentity: Boolean get() = participants.isNotEmpty() && activeParticipantId == null

    /** Where the chosen name sits in [participants], or -1 for none. */
    val youIndex: Int get() = participants.indexOfFirst { it.id == activeParticipantId }

    /** True while the feed is narrowed by anything at all. */
    val isFiltered: Boolean get() = query.isNotBlank() || filters.isActive

    /**
     * Nothing to show at all. A queued expense counts: "No expenses yet" over
     * one just typed would read as having lost it.
     */
    val isEmpty: Boolean
        get() = !isLoading && days.isEmpty() && pending.isEmpty() && !isFiltered && error == null

    val hasNoMatches: Boolean get() = !isLoading && days.isEmpty() && isFiltered

    val categoryFilterName: String?
        get() = categories.firstOrNull { it.id == filters.categoryId }?.name

    val payerFilterName: String?
        get() = participants.firstOrNull { it.id == filters.payerId }?.name
}

/** One read of the feed: [combine] stops at three. */
private data class Feed(
    val text: String,
    val limit: Int,
    val filters: ExpenseFilters,
    val scope: RefreshScope,
)

/**
 * A group's expenses, grouped by day and showing what each did to this
 * device's own balance, the reason the app asks which participant you are.
 * Both numbers come from `:core`, never from arithmetic here.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GroupViewModel(
    private val groupId: String,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    categories: CategoryRepository,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(GroupUiState())
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")
    private val pageSize = MutableStateFlow(FIRST_PAGE)
    private val filters = MutableStateFlow(ExpenseFilters())
    private val retry = Retry()

    init {
        collectOffline(connectivity, _state) { copy(isOffline = it) }
        collectRefreshing(retry, _state) { copy(isRefreshing = it) }
    }

    init {
        viewModelScope.launch {
            expenses.pending(groupId).collect { queued ->
                _state.update { it.copy(pending = queued) }
            }
        }
        viewModelScope.launch {
            categories.categories().collect { result ->
                if (result is SpliitResult.Success) {
                    _state.update { it.copy(allCategories = result.value) }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            groups.group(groupId).collect { result ->
                if (result is SpliitResult.Success) {
                    result.value?.let { group -> _state.update { it.withGroup(group) } }
                }
            }
        }
        viewModelScope.launch {
            combine(
                // Typing must not fire a request per keystroke.
                query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
                    .distinctUntilChanged(),
                pageSize,
                filters,
                retry.restarts,
            ) { text, limit, active, scope -> Feed(text, limit, active, scope) }
                .flatMapLatest { (text, limit, active, scope) ->
                    retry.track(
                        scope,
                        expenses.expenses(
                            groupId = groupId,
                            // The chips are applied here; the server only
                            // filters on text, and "2 matching expenses" has
                            // to be a count across the whole group.
                            limit = if (active.isActive) ALL_EXPENSES else limit,
                            filter = text.trim().takeIf { it.isNotEmpty() },
                        ).map { it to active },
                    )
                }
                .collect { (result, active) ->
                    _state.update { current ->
                        when (result) {
                            is SpliitResult.Success -> {
                                current
                                    .copy(filters = active)
                                    .withExpenses(result.value.expenses)
                                    .copy(
                                        isLoading = false,
                                        // Everything is already here.
                                        hasMore = result.value.hasMore && !active.isActive,
                                        error = null,
                                    )
                            }

                            is SpliitResult.Failure -> {
                                current.copy(isLoading = false, error = result.error)
                            }
                        }
                    }
                }
        }
        viewModelScope.launch {
            groups.activeParticipantId(groupId).collect { id ->
                _state.update { it.copy(activeParticipantId = id).recomputeShares() }
            }
        }
    }

    fun onRefresh() = retry.again()

    /**
     * Sends whatever is queued, then reloads. Called when the feed opens and
     * when the network returns; safe at any time, since an empty outbox does
     * nothing.
     */
    fun onSendQueued() {
        viewModelScope.launch {
            if (expenses.sendQueued() > 0) retry.again()
        }
    }

    fun onPickYouOpen() = _state.update { it.copy(pickingYou = true) }

    fun onPickYouDismiss() = _state.update { it.copy(pickingYou = false) }

    /** Saved the moment it is chosen: it cannot fail and cannot be rolled back. */
    fun onYouChange(index: Int) {
        val participant = _state.value.participants.getOrNull(index) ?: return
        _state.update { it.copy(pickingYou = false) }
        viewModelScope.launch { groups.setActiveParticipant(groupId, participant.id) }
    }

    /** One more page. Deliberately not infinite scrolling. See the repository. */
    fun onLoadMore() {
        pageSize.update { it + PAGE_STEP }
    }

    fun onSearchOpen() {
        _state.update { it.copy(isSearching = true) }
    }

    fun onSearchClose() {
        query.value = ""
        filters.value = ExpenseFilters()
        _state.update { it.copy(isSearching = false, query = "", filters = ExpenseFilters()) }
    }

    /** Null clears the chip rather than selecting an "any" category. */
    fun onCategoryFilter(categoryId: Int?) {
        filters.update { it.copy(categoryId = categoryId) }
    }

    fun onPayerFilter(participantId: String?) {
        filters.update { it.copy(payerId = participantId) }
    }

    fun onDateFilter(date: DateFilter) {
        filters.update { it.copy(date = date) }
    }

    fun onQueryChange(text: String) {
        query.value = text
        _state.update { it.copy(query = text) }
    }

    private fun GroupUiState.withGroup(group: Group) = copy(
        groupName = group.name,
        currency = GroupCurrency.of(group.currencyCode, group.currencySymbol),
        participants = group.participants,
    )

    private fun GroupUiState.withExpenses(all: List<ExpenseSummary>): GroupUiState {
        val today = LocalDate.now()
        val list = all.filter { expense ->
            (filters.categoryId == null || expense.category?.id == filters.categoryId) &&
                (filters.payerId == null || expense.paidBy.id == filters.payerId) &&
                filters.date.matches(expense.expenseDate, today)
        }
        // The server returns them newest first; the feed only breaks that into
        // days.
        val days = list
            .groupBy { it.expenseDate }
            .map { (date, expenses) -> ExpenseDay(date, expenses) }
            .sortedByDescending { it.date }
        return copy(
            days = days,
            // Commonest first, and only the categories this group uses.
            categories = all.mapNotNull { it.category }
                .groupBy { it.id }
                .entries
                .sortedByDescending { it.value.size }
                .map { it.value.first() },
            expenseCount = list.size,
            // Reimbursements are settling up rather than spending, so they
            // would inflate "how much has this trip cost".
            totalSpent = list.filterNot { it.isReimbursement }.sumOf { it.amount },
        ).recomputeShares()
    }

    private fun GroupUiState.recomputeShares(): GroupUiState {
        val me = activeParticipantId
            ?: return copy(yourShare = null, yourBalanceChanges = emptyMap())
        val all = days.flatMap { it.expenses }
        return copy(
            yourBalanceChanges = all.associate { expense ->
                expense.id to participantBalanceChange(me, expense.toBalanceInput())
            },
            // The totals card asks what the trip cost *you*, which is your
            // share and not your balance.
            yourShare = all.filterNot { it.isReimbursement }
                .sumOf { participantShare(me, it.toBalanceInput().toShareInput()) },
        )
    }

    private companion object {
        const val FIRST_PAGE = 30
        const val PAGE_STEP = 30
        const val SEARCH_DEBOUNCE_MS = 300L

        /**
         * Big enough that no realistic group pages. A chip filter counts and
         * totals what it matched, and both are wrong if part of the group is
         * still unfetched.
         */
        const val ALL_EXPENSES = 1000
    }
}
