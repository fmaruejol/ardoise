package io.github.fmaruejol.ardoise.core.stats

import io.github.fmaruejol.ardoise.core.model.Category
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A group's spending as the server adds it up, through `groups.stats.*`.
 * Minor units throughout, and **reimbursements are excluded from every
 * figure** by the server: they are people settling up, not the group spending
 * more.
 */
data class GroupStats(
    val total: Long,
    /**
     * Null from an instance older than `groups.stats.overview` (Spliit 1.23),
     * whose `groups.stats.get` answers the total alone.
     */
    val breakdown: StatsBreakdown?,
)

data class StatsBreakdown(
    /** The first and last day anything was spent; null when nothing was. */
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    /** Highest first. Categories that net to zero are left out. */
    val byCategory: List<CategoryTotal>,
    /** Every participant, whoever has paid for nothing included, highest first. */
    val byParticipant: List<ParticipantTotal>,
)

data class CategoryTotal(
    val category: Category,
    val total: Long,
)

/** What one participant put in, money they paid out, not their share. */
data class ParticipantTotal(
    val participantId: String,
    val name: String,
    val paid: Long,
)

/**
 * The figures printed under the total. Rounded display aggregates that nothing
 * else reads; the day figures are null when the server gave no dates.
 */
data class StatsAverages(
    val days: Int?,
    val perPerson: Long,
    val perDayEach: Long?,
)

fun GroupStats.averages(participantCount: Int): StatsAverages {
    val days = breakdown?.daysCovered()
    return StatsAverages(
        days = days,
        perPerson = divideRounded(total, participantCount),
        perDayEach = days?.let { divideRounded(divideRounded(total, it), participantCount) },
    )
}

/** Counting both ends: the 10th to the 14th is five days. */
private fun StatsBreakdown.daysCovered(): Int {
    if (firstDate == null || lastDate == null) return 0
    return ChronoUnit.DAYS.between(firstDate, lastDate).toInt() + 1
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
