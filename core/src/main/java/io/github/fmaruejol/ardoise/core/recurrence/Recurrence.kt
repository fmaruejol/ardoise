package io.github.fmaruejol.ardoise.core.recurrence

import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import java.time.LocalDate

/**
 * When a recurring expense comes round next. A port of upstream's
 * `calculateNextDate`: the server creates the copies, so this only predicts
 * them and has to agree. Null for [RecurrenceRule.NONE].
 */
fun nextOccurrence(after: LocalDate, rule: RecurrenceRule): LocalDate? = when (rule) {
    RecurrenceRule.NONE -> null

    RecurrenceRule.DAILY -> after.plusDays(1)

    RecurrenceRule.WEEKLY -> after.plusDays(7)

    // Upstream walks the day of the month down until it exists, so 31 January
    // becomes 28 February. `plusMonths` clamps the same way.
    RecurrenceRule.MONTHLY -> after.plusMonths(1)
}

/** The next [count] occurrences after [after], soonest first. */
fun nextOccurrences(
    after: LocalDate,
    rule: RecurrenceRule,
    count: Int,
): List<LocalDate> {
    if (rule == RecurrenceRule.NONE || count <= 0) return emptyList()

    val dates = mutableListOf<LocalDate>()
    var date = after
    repeat(count) {
        date = nextOccurrence(date, rule) ?: return dates
        dates += date
    }
    return dates
}
