package io.github.fmaruejol.ardoise.core.stats

import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.PaidForWithParticipant
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StatsTest {
    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")
    private val names = mapOf("p1" to "Ana", "p2" to "Ben")

    private val housing = Category(1, "Home", "Rent")
    private val food = Category(2, "Food and Drink", "Dining Out")

    @Test
    fun `adds up what the group spent`() {
        val stats = groupStats(
            listOf(
                expense("e1", 48_000, "2026-09-10", housing, ana),
                expense("e2", 9_600, "2026-09-11", food, ben),
            ),
            names,
        )

        assertEquals(57_600, stats.total)
    }

    @Test
    fun `leaves reimbursements out of every figure`() {
        val stats = groupStats(
            listOf(
                expense("e1", 9_600, "2026-09-11", food, ana),
                expense("e2", 4_800, "2026-09-12", null, ben, isReimbursement = true),
            ),
            names,
        )

        // Settling up is not the group spending more.
        assertEquals(9_600, stats.total)
        assertEquals(0, stats.byParticipant.first { it.participantId == "p2" }.paid)
        assertEquals(1, stats.byCategory.size)
    }

    @Test
    fun `counts both ends of the span`() {
        val stats = groupStats(
            listOf(
                expense("e1", 100, "2026-09-10", null, ana),
                expense("e2", 100, "2026-09-14", null, ana),
            ),
            names,
        )

        // The 10th to the 14th is five days, not four.
        assertEquals(5, stats.days)
    }

    @Test
    fun `two expenses on one day are one day`() {
        val stats = groupStats(
            listOf(
                expense("e1", 100, "2026-09-10", null, ana),
                expense("e2", 100, "2026-09-10", null, ben),
            ),
            names,
        )

        assertEquals(1, stats.days)
    }

    @Test
    fun `divides the total between the people and the days`() {
        val stats = groupStats(
            listOf(
                expense("e1", 48_000, "2026-09-10", housing, ana),
                expense("e2", 9_600, "2026-09-14", food, ben),
            ),
            names,
        )

        assertEquals(28_800, stats.perPerson)
        // 576.00 over five days is 115.20 a day, 57.60 each.
        assertEquals(5_760, stats.perDayEach)
    }

    @Test
    fun `ranks categories by what they came to`() {
        val stats = groupStats(
            listOf(
                expense("e1", 9_600, "2026-09-11", food, ana),
                expense("e2", 48_000, "2026-09-10", housing, ana),
                expense("e3", 1_000, "2026-09-11", food, ben),
            ),
            names,
        )

        assertEquals(listOf("Rent", "Dining Out"), stats.byCategory.map { it.category?.name })
        assertEquals(listOf(48_000L, 10_600L), stats.byCategory.map { it.total })
    }

    @Test
    fun `groups the expenses with no category together`() {
        val stats = groupStats(
            listOf(
                expense("e1", 100, "2026-09-11", null, ana),
                expense("e2", 200, "2026-09-11", null, ben),
            ),
            names,
        )

        assertEquals(1, stats.byCategory.size)
        assertEquals(300, stats.byCategory.single().total)
    }

    @Test
    fun `ranks who paid, and keeps whoever paid for nothing`() {
        val stats = groupStats(
            listOf(expense("e1", 9_600, "2026-09-11", food, ana)),
            names,
        )

        // "Who paid most" only reads against the whole group; dropping the
        // people at zero would hide that they have paid for nothing.
        assertEquals(listOf("Ana", "Ben"), stats.byParticipant.map { it.name })
        assertEquals(listOf(9_600L, 0L), stats.byParticipant.map { it.paid })
    }

    @Test
    fun `an empty group divides by nothing without falling over`() {
        val stats = groupStats(emptyList(), emptyMap())

        assertEquals(0, stats.total)
        assertEquals(0, stats.days)
        assertEquals(0, stats.perPerson)
        assertEquals(0, stats.perDayEach)
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

    private fun expense(
        id: String,
        amount: Long,
        date: String,
        category: Category?,
        paidBy: Participant,
        isReimbursement: Boolean = false,
    ) = ExpenseSummary(
        id = id,
        title = id,
        amount = amount,
        originalAmount = null,
        originalCurrency = null,
        expenseDate = LocalDate.parse(date),
        createdAt = Instant.parse("${date}T12:00:00Z"),
        category = category,
        paidBy = paidBy,
        paidFor = listOf(PaidForWithParticipant(ana, 1), PaidForWithParticipant(ben, 1)),
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = isReimbursement,
        documentCount = 0,
    )
}
