package io.github.fmaruejol.ardoise.core.settlement

import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.SplitMode

/** The minimum an expense has to expose for its shares to be computed. */
data class ShareInput(
    /** In minor units. Negative for income. */
    val amount: Long,
    val splitMode: SplitMode,
    val paidFor: List<PaidFor>,
    /**
     * Seeds the rotation that decides who gets the leftover minor unit.
     * Absent until the expense is saved: ids are minted server-side.
     */
    val id: String? = null,
)

/**
 * The one definition of a participant's share of an expense.
 *
 * Shares are whole minor units adding up to exactly [ShareInput.amount],
 * whatever the mode; `BY_PERCENTAGE` and `BY_AMOUNT` are read as ratios, so
 * rows that break the schema's invariants still split the whole amount.
 * Mirrors `getExpenseShares` in upstream's `src/lib/shares.ts` and has to stay
 * identical to it.
 *
 * @return the share per participant id; participants not paid for are absent.
 */
fun expenseShares(expense: ShareInput): Map<String, Long> {
    // paidFor arrives without an ORDER BY, so sort to keep the split stable.
    val paidFors = expense.paidFor.sortedBy { it.participantId }

    val shares = when (expense.splitMode) {
        SplitMode.EVENLY -> paidFors.map { 1L }
        SplitMode.BY_SHARES, SplitMode.BY_PERCENTAGE, SplitMode.BY_AMOUNT -> paidFors.map { it.shares }
    }
    val totalShares = shares.sum()

    val offset = if (expense.id != null && paidFors.isNotEmpty()) {
        (hashString(expense.id) % paidFors.size).toInt()
    } else {
        0
    }

    val amounts = apportion(expense.amount, shares, totalShares, offset)
    return paidFors.mapIndexed { index, paidFor -> paidFor.participantId to amounts[index] }.toMap()
}

/** One participant's share, in whole minor units. Zero for anyone not paid for. */
fun participantShare(participantId: String?, expense: ShareInput): Long {
    if (participantId == null) return 0
    return expenseShares(expense)[participantId] ?: 0
}

/**
 * Splits [amount] into [count] shares adding up to exactly [amount]. The
 * leftover units go to the first shares, so the caller decides the order.
 */
fun distributeAmount(amount: Long, count: Int): List<Long> =
    apportion(amount, List(count) { 1L }, count.toLong(), offset = 0)

/**
 * Splits [amount] over [shares] so the results always add up to exactly
 * [amount].
 *
 * Every participant first gets the floor of their exact share; the units left
 * over are handed out by largest remainder (Hamilton apportionment). An even
 * split produces identical remainders, so ties are broken by rotating the
 * starting position with [offset], or the same participants would absorb
 * the extra unit every single time.
 *
 * The arithmetic is exact `Long`, where upstream divides in floating point.
 * The two agree for any realistic expense; they can differ by a minor unit
 * only once `amount * share` exceeds 2^53, which needs an expense in the
 * millions split `BY_AMOUNT`. Exactness is the safer side to be wrong on.
 */
internal fun apportion(
    amount: Long,
    shares: List<Long>,
    totalShares: Long,
    offset: Int,
): List<Long> {
    val count = shares.size
    if (count == 0) return emptyList()
    // Shares are validated as positive on write, but legacy rows and rows
    // written directly to the database are not guaranteed to be.
    if (totalShares == 0L) return List(count) { 0L }

    val amounts = LongArray(count)
    val remainders = ArrayList<Remainder>(count)
    var distributed = 0L

    shares.forEachIndexed { index, share ->
        val exact = amount * share
        // Flooring rather than truncating keeps the leftover non-negative for
        // a negative amount (income) just as it is for a positive one, so
        // `remaining` below stays in [0, count).
        val base = exact.floorDiv(totalShares)
        amounts[index] = base
        distributed += base
        remainders += Remainder(index, exact - base * totalShares)
    }

    val remaining = amount - distributed
    if (remaining == 0L) return amounts.toList()

    remainders.sortWith(
        compareByDescending<Remainder> { it.remainder }
            .thenBy { (it.index - offset + count) % count },
    )
    for (i in 0 until remaining.toInt()) {
        amounts[remainders[i].index] += 1
    }
    return amounts.toList()
}

private class Remainder(val index: Int, val remainder: Long)

/**
 * A small deterministic string hash (FNV-1a, 32 bit), ported from upstream so
 * both clients rotate the leftover minor unit to the same participant.
 *
 * Seeding the rotation with the expense id keeps the split a pure function of
 * the expense, no persisted cursor, no randomness, while still moving the
 * extra unit to a different participant from one expense to the next.
 */
private fun hashString(value: String): Long {
    var hash = FNV_OFFSET_BASIS
    for (char in value) {
        hash = hash xor char.code
        // Int multiplication already wraps at 32 bits, which is what JS needs
        // Math.imul for.
        hash *= FNV_PRIME
    }
    // The JS original ends with `>>> 0`, i.e. reads the result as unsigned.
    return hash.toLong() and 0xFFFFFFFFL
}

private const val FNV_OFFSET_BASIS = -0x7EE3623B // 0x811C9DC5 as a signed Int
private const val FNV_PRIME = 0x01000193

fun ExpenseSummary.toShareInput(): ShareInput = ShareInput(
    amount = amount,
    splitMode = splitMode,
    paidFor = paidFor.map { PaidFor(participantId = it.participant.id, shares = it.shares) },
    id = id,
)

fun Expense.toShareInput(): ShareInput = ShareInput(
    amount = amount,
    splitMode = splitMode,
    paidFor = paidFor,
    id = id,
)
