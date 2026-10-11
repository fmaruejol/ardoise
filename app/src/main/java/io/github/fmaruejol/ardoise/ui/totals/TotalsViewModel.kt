package io.github.fmaruejol.ardoise.ui.totals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.stats.CategoryTotal
import io.github.fmaruejol.ardoise.core.stats.GroupStats
import io.github.fmaruejol.ardoise.core.stats.ParticipantTotal
import io.github.fmaruejol.ardoise.core.stats.StatsAverages
import io.github.fmaruejol.ardoise.core.stats.averages
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.StatsRepository
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
    val label: String,
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
    val averages: StatsAverages? = null,
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

    /** The instance predates `groups.stats.overview` and gave the total alone. */
    val hasNoBreakdown: Boolean get() = stats != null && stats.breakdown == null
}

/**
 * A group's spending, as `groups.stats.overview` adds it up, the procedure the
 * web and iOS clients read. Adding up the cached feed instead would need every
 * expense in the group, and would be a second implementation of the server's
 * arithmetic for the two to disagree over.
 */
class TotalsViewModel(
    private val groupId: String,
    groups: GroupRepository,
    stats: StatsRepository,
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
                    stats.stats(groupId),
                    groups.activeParticipantId(groupId),
                ) { group, stats, me -> Triple(group, stats, me) }
            }
                .collect { (groupResult, statsResult, me) ->
                    val group = (groupResult as? SpliitResult.Success)?.value
                    val stats = (statsResult as? SpliitResult.Success)?.value
                    _state.update { current ->
                        when {
                            statsResult is SpliitResult.Failure -> {
                                current.copy(isLoading = false, error = statsResult.error)
                            }

                            group == null -> {
                                current.copy(isLoading = false)
                            }

                            stats != null -> {
                                current.copy(
                                    isLoading = false,
                                    error = null,
                                    currency = GroupCurrency.of(
                                        group.currencyCode,
                                        group.currencySymbol,
                                    ),
                                    stats = stats,
                                    averages = stats.averages(group.participants.size),
                                    categories = stats.breakdown?.byCategory.orEmpty().bars(),
                                    payers = stats.breakdown?.byParticipant.orEmpty()
                                        .payers(group.participants.map { it.id }, me),
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
}

private fun List<CategoryTotal>.bars(): List<CategoryBar> {
    val widest = maxOfOrNull { it.total } ?: 0L
    return map {
        CategoryBar(
            label = it.category.name,
            total = it.total,
            // A category can net below zero on refunds; it gets no bar.
            weight = if (widest <= 0L) 0f else (it.total.toFloat() / widest).coerceIn(0f, 1f),
        )
    }
}

private fun List<ParticipantTotal>.payers(order: List<String>, me: String?): List<PayerRow> =
    map {
        PayerRow(
            participantId = it.participantId,
            name = it.name,
            paid = it.paid,
            isYou = it.participantId == me,
            index = order.indexOf(it.participantId).coerceAtLeast(0),
        )
    }
