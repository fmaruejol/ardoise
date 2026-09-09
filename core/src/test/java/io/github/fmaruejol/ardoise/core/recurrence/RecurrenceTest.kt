package io.github.fmaruejol.ardoise.core.recurrence

import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * These have to agree with `calculateNextDate` in `spliit-app/spliit`
 * `src/lib/api.ts`, because the server is what actually creates the copies.
 */
class RecurrenceTest {
    @Test
    fun `nothing recurs when the rule is none`() {
        assertNull(nextOccurrence(LocalDate.parse("2026-09-11"), RecurrenceRule.NONE))
        assertEquals(emptyList<LocalDate>(), nextOccurrences(LocalDate.parse("2026-09-11"), RecurrenceRule.NONE, 3))
    }

    @Test
    fun `daily is the next day`() {
        assertEquals(
            LocalDate.parse("2026-09-12"),
            nextOccurrence(LocalDate.parse("2026-09-11"), RecurrenceRule.DAILY),
        )
    }

    @Test
    fun `weekly is seven days, not the same weekday next month`() {
        assertEquals(
            LocalDate.parse("2026-09-18"),
            nextOccurrence(LocalDate.parse("2026-09-11"), RecurrenceRule.WEEKLY),
        )
    }

    @Test
    fun `monthly keeps the day of the month`() {
        assertEquals(
            LocalDate.parse("2026-10-11"),
            nextOccurrence(LocalDate.parse("2026-09-11"), RecurrenceRule.MONTHLY),
        )
    }

    @Test
    fun `monthly walks the day back rather than spilling into the month after`() {
        // Upstream decrements the day until it exists in the next month, so
        // the 31st of January is the 28th of February, never the 3rd of March.
        assertEquals(
            LocalDate.parse("2026-02-28"),
            nextOccurrence(LocalDate.parse("2026-01-31"), RecurrenceRule.MONTHLY),
        )
    }

    @Test
    fun `monthly lands on the 29th in a leap year`() {
        assertEquals(
            LocalDate.parse("2028-02-29"),
            nextOccurrence(LocalDate.parse("2028-01-31"), RecurrenceRule.MONTHLY),
        )
    }

    @Test
    fun `monthly rolls into the next year from December`() {
        assertEquals(
            LocalDate.parse("2027-01-15"),
            nextOccurrence(LocalDate.parse("2026-12-15"), RecurrenceRule.MONTHLY),
        )
    }

    @Test
    fun `daily rolls across a month end`() {
        assertEquals(
            LocalDate.parse("2026-10-01"),
            nextOccurrence(LocalDate.parse("2026-09-30"), RecurrenceRule.DAILY),
        )
    }

    @Test
    fun `lists the next few, soonest first`() {
        assertEquals(
            listOf(
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-12-01"),
            ),
            nextOccurrences(LocalDate.parse("2026-09-01"), RecurrenceRule.MONTHLY, 3),
        )
    }

    @Test
    fun `a shortened month does not shorten the ones after it`() {
        // Each step is taken from the one before it, exactly as the server
        // does, so January's 31st stays the 28th once February has clipped it.
        assertEquals(
            listOf(
                LocalDate.parse("2026-02-28"),
                LocalDate.parse("2026-03-28"),
                LocalDate.parse("2026-04-28"),
            ),
            nextOccurrences(LocalDate.parse("2026-01-31"), RecurrenceRule.MONTHLY, 3),
        )
    }

    @Test
    fun `asking for none gives none`() {
        assertEquals(
            emptyList<LocalDate>(),
            nextOccurrences(LocalDate.parse("2026-09-11"), RecurrenceRule.MONTHLY, 0),
        )
    }
}
