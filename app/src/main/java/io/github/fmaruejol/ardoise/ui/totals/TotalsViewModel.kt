package io.github.fmaruejol.ardoise.ui.totals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.stats.GroupStats
import io.github.fmaruejol.ardoise.core.stats.groupStats
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

/** One category's bar: its share of the group's spending, and its name. */
data class CategoryBar(
    val label: String?,
    val total: Long,
    /** 0f to 1f against the largest category. */
    val weight: Float,
)

data class PayerRow(
    val participantId: String,
    val name: String,
    val paid: Long,
    val isYou: Boolean,
    /** Position in the group, so the avatar keeps its colour across screens. */
    val index: Int,
)

data class TotalsUiState(
    val isLoading: Boolean = true,
    val currency: GroupCurrency = GroupCurrency.of(null, "€"),
    val stats: GroupStats? = null,
    val categories: List<CategoryBar> = emptyList(),
    val payers: List<PayerRow> = emptyList(),
    /**
     * True while the platform says there is no network.
     *
     * Read only to put the offline banner up over rows that came out of the
     * cache, never to block, hide or skip anything. A screen already open
     * when the phone loses signal has nothing else to tell it: no read has
     * failed, because no read has been made since.
     */
    val isOffline: Boolean = false,
    val error: SpliitError? = null,
) {
    val isEmpty: Boolean get() = !isLoading && stats?.total == 0L && categories.isEmpty()
}

/**
 * A group's spending, added up.
 *
 * The arithmetic is `:core`'s [groupStats] over the group's expenses rather
 * than `groups.stats.overview`. Upstream's stats procedure derives the same
 * numbers from the same list, and keeping it here means the totals cannot drift
 * from the settlement maths they sit next to.
 *
 * The cost is one large page. Should a group ever outgrow [ALL_EXPENSES], the
 * stats procedure is the way out and this becomes a mapping instead.
 */
class TotalsViewModel(
    private val groupId: String,
    groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(TotalsUiState())
    val state: StateFlow<TotalsUiState> = _state.asStateFlow()

    private val retry = Retry()

    init {
        collectOffline(connectivity, _state) { copy(isOffline = it) }
    }

    init {
        viewModelScope.launch {
            retry.restarting {
                combine(
                    groups.group(groupId),
                    expenses.expenses(groupId, limit = ALL_EXPENSES),
                    groups.activeParticipantId(groupId),
                ) { group, page, me -> Triple(group, page, me) }
            }
                .collect { (groupResult, pageResult, me) ->
                    val group = (groupResult as? SpliitResult.Success)?.value
                    _state.update { current ->
                        when {
                            pageResult is SpliitResult.Failure -> {
                                current.copy(isLoading = false, error = pageResult.error)
                            }

                            group == null -> {
                                current.copy(isLoading = false)
                            }

                            pageResult is SpliitResult.Success -> {
                                val positions = group.participants
                                    .mapIndexed { index, it -> it.id to index }
                                    .toMap()
                                val names = group.participants.associate { it.id to it.name }
                                val stats = groupStats(pageResult.value.expenses, names)
                                val widest = stats.byCategory.maxOfOrNull { it.total } ?: 0L

                                current.copy(
                                    isLoading = false,
                                    error = null,
                                    currency = GroupCurrency.of(
                                        group.currencyCode,
                                        group.currencySymbol,
                                    ),
                                    stats = stats,
                                    categories = stats.byCategory.map {
                                        CategoryBar(
                                            label = it.category?.name,
                                            total = it.total,
                                            weight = if (widest == 0L) {
                                                0f
                                            } else {
                                                it.total.toFloat() / widest
                                            },
                                        )
                                    },
                                    payers = stats.byParticipant.map {
                                        PayerRow(
                                            participantId = it.participantId,
                                            name = it.name,
                                            paid = it.paid,
                                            isYou = it.participantId == me,
                                            index = positions[it.participantId] ?: 0,
                                        )
                                    },
                                )
                            }

                            else -> {
                                current
                            }
                        }
                    }
                }
        }
    }

    fun onRetry() = retry.again()

    companion object {
        /**
         * Big enough that no realistic group pages. The totals are wrong rather
         * than merely incomplete if an expense is missing, so this asks for all
         * of them in one go instead of adding up whatever the first page held.
         */
        const val ALL_EXPENSES: Int = 1000
    }
}
