package io.github.fmaruejol.ardoise.ui.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.BalanceRepository
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.ui.Retry
import io.github.fmaruejol.ardoise.ui.collectOffline
import io.github.fmaruejol.ardoise.ui.collectRefreshing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One participant's line: their position, and how big it is against the rest. */
data class BalanceRow(
    val participantId: String,
    val name: String,
    val total: Long,
    val isYou: Boolean,
    /** 0f to 1f against the largest position in the group, for the bar. */
    val weight: Float,
)

data class BalancesUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val currency: GroupCurrency = GroupCurrency.of(null, "€"),
    val rows: List<BalanceRow> = emptyList(),
    /** This device's own position, or null until it knows which participant it is. */
    val yourPosition: Long? = null,
    /** How many people are on the other side of your position. */
    val counterparties: Int = 0,
    val canSettle: Boolean = false,
    /** The "which one is you" dialog, opened from this screen's own prompt. */
    val pickingYou: Boolean = false,
    /** Read only to raise the offline banner over rows from the cache. */
    val isOffline: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: SpliitError? = null,
) {
    /**
     * True once the balances are loaded and nobody has said who they are. Not
     * merely `yourPosition == null`, which is also true before they arrive.
     */
    val needsIdentity: Boolean get() = rows.isNotEmpty() && yourPosition == null

    /** Where the chosen name sits in [rows], or -1 for none. */
    val youIndex: Int get() = rows.indexOfFirst { it.isYou }

    val isEmpty: Boolean get() = !isLoading && rows.isEmpty() && error == null

    val isSettled: Boolean get() = rows.isNotEmpty() && rows.all { it.total == 0L }
}

/**
 * Who is up and who is down, from `groups.balances.list` rather than by
 * adding the expenses up here, because `:core`'s `settle` needs every
 * expense in hand and one round trip beats fetching them all.
 *
 * *Public* balances, so the app and the web client always agree.
 */
class BalancesViewModel(
    private val groupId: String,
    private val groups: GroupRepository,
    private val balances: BalanceRepository,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(BalancesUiState())
    val state: StateFlow<BalancesUiState> = _state.asStateFlow()

    private val retry = Retry()

    init {
        collectOffline(connectivity, _state) { copy(isOffline = it) }
        collectRefreshing(retry, _state) { copy(isRefreshing = it) }
    }

    init {
        viewModelScope.launch {
            retry.restarting {
                combine(
                    groups.group(groupId),
                    balances.balances(groupId),
                    groups.activeParticipantId(groupId),
                ) { group, result, me -> Triple(group, result, me) }
            }
                .collect { (groupResult, balanceResult, me) ->
                    val group = (groupResult as? SpliitResult.Success)?.value
                    _state.update { current ->
                        when {
                            balanceResult is SpliitResult.Failure -> {
                                current.copy(isLoading = false, error = balanceResult.error)
                            }

                            group == null -> {
                                current.copy(isLoading = false)
                            }

                            balanceResult is SpliitResult.Success -> {
                                current.from(group, balanceResult.value, me)
                            }

                            else -> {
                                current
                            }
                        }
                    }
                }
        }
    }

    fun onRefresh() = retry.again()

    fun onPickYouOpen() = _state.update { it.copy(pickingYou = true) }

    fun onPickYouDismiss() = _state.update { it.copy(pickingYou = false) }

    /** Saved at once, and the same preference the feed and group settings write. */
    fun onYouChange(index: Int) {
        val row = _state.value.rows.getOrNull(index) ?: return
        _state.update { it.copy(pickingYou = false) }
        viewModelScope.launch { groups.setActiveParticipant(groupId, row.participantId) }
    }

    private fun BalancesUiState.from(
        group: Group,
        balances: GroupBalances,
        me: String?,
    ): BalancesUiState {
        val totals = balances.balances.associate { it.participantId to it.total }
        // The widest bar is the largest position either way, so both sides are
        // drawn to one scale.
        val widest = totals.values.maxOfOrNull { abs(it) } ?: 0L
        val yours = me?.let { totals[it] ?: 0L }

        return copy(
            isLoading = false,
            error = null,
            groupName = group.name,
            currency = GroupCurrency.of(group.currencyCode, group.currencySymbol),
            // Everyone in the group's own order: a participant with nothing
            // owed either way still belongs on the list.
            rows = group.participants.map { participant ->
                val total = totals[participant.id] ?: 0L
                BalanceRow(
                    participantId = participant.id,
                    name = participant.name,
                    total = total,
                    isYou = participant.id == me,
                    weight = if (widest == 0L) 0f else abs(total).toFloat() / widest,
                )
            },
            yourPosition = yours,
            counterparties = when {
                yours == null || yours == 0L -> 0

                // Whoever is on the other side of you.
                yours > 0 -> totals.count { (id, total) -> id != me && total < 0 }

                else -> totals.count { (id, total) -> id != me && total > 0 }
            },
            canSettle = balances.reimbursements.isNotEmpty(),
        )
    }
}
