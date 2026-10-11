package io.github.fmaruejol.ardoise.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    @Test
    fun `counts both ends of the span`() {
        val averages = stats(57_600, "2026-09-10", "2026-09-14").averages(participantCount = 2)

        // The 10th to the 14th is five days, not four.
        assertEquals(5, averages.days)
    }

    @Test
    fun `spending on one day is one day`() {
        assertEquals(1, stats(100, "2026-09-10", "2026-09-10").averages(2).days)
    }

    @Test
    fun `divides the total between the people and the days`() {
        val averages = stats(57_600, "2026-09-10", "2026-09-14").averages(participantCount = 2)

        assertEquals(28_800, averages.perPerson)
        // 576.00 over five days is 115.20 a day, 57.60 each.
        assertEquals(5_760L, averages.perDayEach)
    }

    @Test
    fun `an empty group divides by nothing without falling over`() {
        val averages = stats(0, null, null).averages(participantCount = 0)

        assertEquals(0, averages.days)
        assertEquals(0, averages.perPerson)
        assertEquals(0L, averages.perDayEach)
    }

    @Test
    fun `an instance that gave only the total has no day figures`() {
        val averages = GroupStats(total = 9_000, breakdown = null).averages(participantCount = 3)

        assertEquals(3_000, averages.perPerson)
        assertNull(averages.days)
        assertNull(averages.perDayEach)
    }

    // --- the rounding these figures are allowed to do ----------------------

    @Test
    fun `rounds a half away from zero`() {
        assertEquals(3, divideRounded(5, 2))
        assertEquals(-3, divideRounded(-5, 2))
        assertEquals(2, divideRounded(5, 3))
    }

    @Test
    fun `rounds rather than truncating`() {
        // 100.00 between six is 16.667, which reads as 16.67. Truncating to
        // 16.66 looks like an error to anyone who multiplies it back.
        assertEquals(1_667, divideRounded(10_000, 6))
        // And it really does round down when it should: 33.33, not 33.34.
        assertEquals(3_333, divideRounded(10_000, 3))
    }

    private fun stats(total: Long, first: String?, last: String?) = GroupStats(
        total = total,
        breakdown = StatsBreakdown(
            firstDate = first?.let(LocalDate::parse),
            lastDate = last?.let(LocalDate::parse),
            byCategory = emptyList(),
            byParticipant = emptyList(),
        ),
    )
}
