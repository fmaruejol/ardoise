package io.github.fmaruejol.ardoise.core.settlement

import io.github.fmaruejol.ardoise.core.model.Balance
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.Reimbursement
import io.github.fmaruejol.ardoise.core.model.SplitMode

/** [ShareInput] plus who paid, the minimum for a balance. */
data class BalanceInput(
    val amount: Long,
    val splitMode: SplitMode,
    val paidById: String,
    val paidFor: List<PaidFor>,
    val id: String? = null,
)

fun BalanceInput.toShareInput(): ShareInput = ShareInput(
    amount = amount,
    splitMode = splitMode,
    paidFor = paidFor,
    id = id,
)

/**
 * Each participant's real position: what they paid out, what they were paid
 * for, and the difference. Reimbursements count like any other expense, and
 * the totals sum to zero across the group. Mirrors upstream's `getBalances`.
 */
fun balances(expenses: List<BalanceInput>): Map<String, Balance> {
    val accumulated = LinkedHashMap<String, Accumulator>()

    for (expense in expenses) {
        accumulated.getOrPut(expense.paidById) { Accumulator() }.paid += expense.amount

        for ((participantId, share) in expenseShares(expense.toShareInput())) {
            accumulated.getOrPut(participantId) { Accumulator() }.paidFor += share
        }
    }

    return accumulated.mapValues { (participantId, accumulator) ->
        Balance(
            participantId = participantId,
            paid = accumulator.paid,
            paidFor = accumulator.paidFor,
            total = accumulator.paid - accumulator.paidFor,
        )
    }
}

/**
 * What one expense does to one participant's balance, [balances] narrowed to
 * a single expense, so the feed's number cannot disagree with the balances
 * screen. A null [participantId] is worth zero.
 */
fun participantBalanceChange(participantId: String?, expense: BalanceInput): Long {
    if (participantId == null) return 0
    val paid = if (expense.paidById == participantId) expense.amount else 0
    return paid - participantShare(participantId, expense.toShareInput())
}

/**
 * The payments that would settle the group, largest creditor against largest
 * debtor. The order is stable, positive balances first, ties on participant
 * id, never on iteration order. Mirrors `getSuggestedReimbursements`.
 */
fun suggestedReimbursements(balances: Map<String, Balance>): List<Reimbursement> {
    val open = ArrayDeque(
        balances.values
            .filter { it.total != 0L }
            .map { Position(it.participantId, it.total) }
            .sortedWith(
                compareBy<Position> { if (it.total > 0) 0 else 1 }.thenBy { it.participantId },
            ),
    )

    val reimbursements = mutableListOf<Reimbursement>()
    while (open.size > 1) {
        val first = open.first()
        val last = open.last()
        val combined = first.total + last.total

        if (first.total > -last.total) {
            reimbursements += Reimbursement(
                fromParticipantId = last.participantId,
                toParticipantId = first.participantId,
                amount = -last.total,
            )
            first.total = combined
            open.removeLast()
        } else {
            reimbursements += Reimbursement(
                fromParticipantId = last.participantId,
                toParticipantId = first.participantId,
                amount = first.total,
            )
            last.total = combined
            open.removeFirst()
        }
    }
    return reimbursements.filter { it.amount != 0L }
}

/**
 * Balances derived from the suggested reimbursements rather than raw totals,
 * what `groups.balances.list` returns, where `paid` and `paidFor` read as
 * "will receive" and "will pay". Mirrors `getPublicBalances`.
 */
fun publicBalances(reimbursements: List<Reimbursement>): Map<String, Balance> {
    val accumulated = LinkedHashMap<String, Accumulator>()

    for (reimbursement in reimbursements) {
        accumulated.getOrPut(reimbursement.fromParticipantId) { Accumulator() }
            .paidFor += reimbursement.amount
        accumulated.getOrPut(reimbursement.toParticipantId) { Accumulator() }
            .paid += reimbursement.amount
    }

    return accumulated.mapValues { (participantId, accumulator) ->
        Balance(
            participantId = participantId,
            paid = accumulator.paid,
            paidFor = accumulator.paidFor,
            total = accumulator.paid - accumulator.paidFor,
        )
    }
}

/** `groups.balances.list` computed locally, from expenses already in hand. */
fun settle(expenses: List<BalanceInput>): GroupBalances {
    val reimbursements = suggestedReimbursements(balances(expenses))
    return GroupBalances(
        balances = publicBalances(reimbursements).values.toList(),
        reimbursements = reimbursements,
    )
}

private class Accumulator(var paid: Long = 0, var paidFor: Long = 0)

private class Position(val participantId: String, var total: Long)

fun ExpenseSummary.toBalanceInput(): BalanceInput = BalanceInput(
    amount = amount,
    splitMode = splitMode,
    paidById = paidBy.id,
    paidFor = paidFor.map { PaidFor(participantId = it.participant.id, shares = it.shares) },
    id = id,
)

fun Expense.toBalanceInput(): BalanceInput = BalanceInput(
    amount = amount,
    splitMode = splitMode,
    paidById = paidById,
    paidFor = paidFor,
    id = id,
)
