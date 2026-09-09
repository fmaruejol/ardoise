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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What a suggested payment leaves behind once it is made. */
enum class Clears {
    Both,
    From,
    To,
    Neither,
}

/** One suggested payment, with enough about it to say what it achieves. */
data class Transfer(
    val fromId: String,
    val fromName: String,
    /** Position in the group, so an avatar keeps its colour across screens. */
    val fromIndex: Int,
    val toId: String,
    val toName: String,
    val toIndex: Int,
    val amount: Long,
    val fromIsYou: Boolean,
    val toIsYou: Boolean,
    val clears: Clears,
    /** What is still owed by whichever side this does not clear. */
    val leftOver: Long,
)

data class SettleUpUiState(
    val isLoading: Boolean = true,
    val currency: GroupCurrency = GroupCurrency.of(null, "€"),
    val transfers: List<Transfer> = emptyList(),
    /** Read only to raise the offline banner over rows from the cache. */
    val isOffline: Boolean = false,
    val error: SpliitError? = null,
) {
    val isSettled: Boolean get() = !isLoading && transfers.isEmpty() && error == null
}

/**
 * The payments that would clear the group.
 *
 * The suggestions are the server's, so the app never proposes a payment the
 * web client would not.
 *
 * **Nothing here writes.** "Mark as paid" opens the expense form filled in, as
 * the web client's reimbursement link does: settling up creates a
 * reimbursement expense, and `groups.expenses.create` cannot be taken back.
 */
class SettleUpViewModel(
    private val groupId: String,
    groups: GroupRepository,
    private val balances: BalanceRepository,
    private val connectivity: Connectivity,
) : ViewModel() {
    private val _state = MutableStateFlow(SettleUpUiState())
    val state: StateFlow<SettleUpUiState> = _state.asStateFlow()

    private val retry = Retry()

    init {
        collectOffline(connectivity, _state) { copy(isOffline = it) }
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

    fun onRetry() = retry.again()

    private fun SettleUpUiState.from(
        group: Group,
        balances: GroupBalances,
        me: String?,
    ): SettleUpUiState {
        val names = group.participants.associate { it.id to it.name }
        val positions = group.participants.mapIndexed { index, it -> it.id to index }.toMap()
        // What each participant still owes or is owed as the list is walked.
        // The suggestions are a chain, where each one is meant to be made after
        // the ones above it, so what a payment clears only reads correctly when
        // the earlier payments have already been taken off.
        val remaining = balances.balances.associate { it.participantId to abs(it.total) }
            .toMutableMap()

        return copy(
            isLoading = false,
            error = null,
            currency = GroupCurrency.of(group.currencyCode, group.currencySymbol),
            transfers = balances.reimbursements.map { reimbursement ->
                val from = reimbursement.fromParticipantId
                val to = reimbursement.toParticipantId
                val fromLeft = ((remaining[from] ?: 0L) - reimbursement.amount)
                    .coerceAtLeast(0L)
                val toLeft = ((remaining[to] ?: 0L) - reimbursement.amount)
                    .coerceAtLeast(0L)
                remaining[from] = fromLeft
                remaining[to] = toLeft
                Transfer(
                    fromId = from,
                    fromName = names[from].orEmpty(),
                    fromIndex = positions[from] ?: 0,
                    toId = to,
                    toName = names[to].orEmpty(),
                    toIndex = positions[to] ?: 0,
                    amount = reimbursement.amount,
                    fromIsYou = from == me,
                    toIsYou = to == me,
                    clears = when {
                        fromLeft == 0L && toLeft == 0L -> Clears.Both
                        fromLeft == 0L -> Clears.From
                        toLeft == 0L -> Clears.To
                        else -> Clears.Neither
                    },
                    // Whichever side is closer to square is the one still
                    // waiting on this payment to finish the job.
                    leftOver = minOf(fromLeft, toLeft),
                )
            },
        )
    }
}
