package io.github.fmaruejol.ardoise.core.stats

import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What one category came to, and how much of the group's spending that is. */
data class CategoryTotal(
    val category: Category?,
    val total: Long,
)

/** What one participant put in, money they paid out, not their share. */
data class ParticipantTotal(
    val participantId: String,
    val name: String,
    val paid: Long,
)

/**
 * A group's spending, added up. Minor units throughout; [perPerson] and
 * [perDayEach] are rounded display aggregates that nothing else reads.
 */
data class GroupStats(
    val total: Long,
    val days: Int,
    val participantCount: Int,
    val perPerson: Long,
    val perDayEach: Long,
    val byCategory: List<CategoryTotal>,
    val byParticipant: List<ParticipantTotal>,
)

/**
 * Adds a group's expenses up for the totals screen. Here rather than through
 * `groups.stats.overview` because it is the same arithmetic, and `:core` is
 * where it can be tested beside the settlement maths it must not contradict.
 *
 * **Reimbursements are excluded throughout**: they are people settling up, not
 * the group spending more.
 *
 * @param names participant id to name, for [byParticipant].
 */
fun groupStats(
    expenses: List<ExpenseSummary>,
    names: Map<String, String>,
): GroupStats {
    val spending = expenses.filterNot { it.isReimbursement }
    val total = spending.sumOf { it.amount }
    val participantCount = names.size

    return GroupStats(
        total = total,
        days = spending.daysCovered(),
        participantCount = participantCount,
        perPerson = divideRounded(total, participantCount),
        perDayEach = divideRounded(divideRounded(total, spending.daysCovered()), participantCount),
        byCategory = spending
            .groupBy { it.category?.id }
            .map { (_, group) ->
                CategoryTotal(category = group.first().category, total = group.sumOf { it.amount })
            }
            .sortedByDescending { it.total },
        // Including whoever has paid for nothing: "who paid most" is only
        // readable against the whole list.
        byParticipant = names
            .map { (id, name) ->
                ParticipantTotal(
                    participantId = id,
                    name = name,
                    paid = spending.filter { it.paidBy.id == id }.sumOf { it.amount },
                )
            }
            .sortedByDescending { it.paid },
    )
}

/** How many days the spending covers, counting both ends. */
private fun List<ExpenseSummary>.daysCovered(): Int {
    if (isEmpty()) return 0
    val dates = map { it.expenseDate }
    val first = dates.min()
    val last = dates.max()
    return ChronoUnit.DAYS.between(first, last).toInt() + 1
}

/**
 * Integer division, rounding halves away from zero, because a "per person"
 * figure that truncated would read as an error. Zero for a zero divisor.
 */
internal fun divideRounded(total: Long, by: Int): Long {
    if (by == 0) return 0
    val quotient = total / by
    val remainder = total % by
    if (2 * abs(remainder) < abs(by)) return quotient
    return if ((total xor by.toLong()) < 0) quotient - 1 else quotient + 1
}
