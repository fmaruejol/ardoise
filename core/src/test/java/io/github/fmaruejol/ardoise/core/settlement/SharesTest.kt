package io.github.fmaruejol.ardoise.core.settlement

import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ported from `src/lib/shares.test.ts` in `spliit-app/spliit`, expected values
 * included. A failure here means this client and the web app no longer agree.
 */
class SharesTest {
    private fun expense(
        id: String?,
        amount: Long,
        paidFor: List<Pair<String, Long>>,
        splitMode: SplitMode = SplitMode.EVENLY,
    ) = ShareInput(
        amount = amount,
        splitMode = splitMode,
        paidFor = paidFor.map { (participantId, shares) -> PaidFor(participantId, shares) },
        id = id,
    )

    private fun Map<String, Long>.sum(): Long = values.sum()

    /** The shares themselves, smallest first, who gets which is a separate test. */
    private fun Map<String, Long>.sorted(): List<Long> = values.sorted()

    @Test
    fun `splits the whole amount and nothing more, in every split mode`() {
        for (splitMode in SplitMode.entries) {
            val shares = expenseShares(
                expense(
                    "e1",
                    9500,
                    listOf("alice" to 1L, "bob" to 1L, "carol" to 1L),
                    splitMode,
                ),
            )

            assertEquals("split mode $splitMode", 9500L, shares.sum())
        }
    }

    @Test
    fun `does not leave a stranded minor unit on an even split`() {
        val shares = expenseShares(
            expense("e1", 9500, listOf("alice" to 1L, "bob" to 1L, "carol" to 1L)),
        )

        assertEquals(listOf(3166L, 3167L, 3167L), shares.sorted())
    }

    @Test
    fun `ignores the stored shares in EVENLY mode`() {
        val shares = expenseShares(expense("e1", 100, listOf("alice" to 7L, "bob" to 1L)))

        assertEquals(50L, shares["alice"])
        assertEquals(50L, shares["bob"])
    }

    @Test
    fun `splits BY_SHARES proportionally`() {
        val shares = expenseShares(
            expense("e1", 100, listOf("alice" to 1L, "bob" to 2L), SplitMode.BY_SHARES),
        )

        assertEquals(33L, shares["alice"])
        assertEquals(67L, shares["bob"])
    }

    @Test
    fun `leaves exact BY_AMOUNT shares untouched`() {
        val shares = expenseShares(
            expense(
                "e1",
                1000,
                listOf("alice" to 333L, "bob" to 333L, "carol" to 334L),
                SplitMode.BY_AMOUNT,
            ),
        )

        assertEquals(333L, shares["alice"])
        assertEquals(333L, shares["bob"])
        assertEquals(334L, shares["carol"])
    }

    @Test
    fun `normalises BY_PERCENTAGE shares that do not add up to 100 percent`() {
        // A row that predates the schema refinement, or came in through an
        // import: 60/20 of a 100 expense.
        val shares = expenseShares(
            expense(
                "e1",
                100,
                listOf("alice" to 6000L, "bob" to 2000L),
                SplitMode.BY_PERCENTAGE,
            ),
        )

        assertEquals(75L, shares["alice"])
        assertEquals(25L, shares["bob"])
        assertEquals(100L, shares.sum())
    }

    @Test
    fun `normalises BY_AMOUNT shares that do not add up to the amount`() {
        val shares = expenseShares(
            expense("e1", 100, listOf("alice" to 30L, "bob" to 30L), SplitMode.BY_AMOUNT),
        )

        assertEquals(100L, shares.sum())
    }

    @Test
    fun `splits an income without losing a minor unit`() {
        val shares = expenseShares(
            expense("e1", -9500, listOf("alice" to 1L, "bob" to 1L, "carol" to 1L)),
        )

        assertEquals(-9500L, shares.sum())
        assertEquals(listOf(-3167L, -3167L, -3166L), shares.sorted())
    }

    @Test
    fun `gives everyone nothing when the shares add up to zero`() {
        val shares = expenseShares(
            expense("e1", 100, listOf("alice" to 0L, "bob" to 0L), SplitMode.BY_SHARES),
        )

        assertEquals(0L, shares["alice"])
        assertEquals(0L, shares["bob"])
    }

    @Test
    fun `handles an expense nobody was paid for`() {
        assertEquals(0, expenseShares(expense("e1", 100, emptyList())).size)
    }

    @Test
    fun `does not depend on the order the participants come back from the database`() {
        val paidFor = listOf("alice" to 1L, "bob" to 1L, "carol" to 1L)

        assertEquals(
            expenseShares(expense("e1", 100, paidFor)),
            expenseShares(expense("e1", 100, paidFor.reversed())),
        )
    }

    @Test
    fun `gives the same expense the same split every time`() {
        fun split() = expenseShares(
            expense("e1", 100, listOf("alice" to 1L, "bob" to 1L, "carol" to 1L)),
        )

        assertEquals(split(), split())
    }

    @Test
    fun `does not always offer the leftover minor unit to the same participant`() {
        val participants = listOf("alice", "bob", "carol")
        val receivers = mutableSetOf<String>()

        repeat(50) { index ->
            val shares = expenseShares(
                expense("expense-$index", 100, participants.map { it to 1L }),
            )
            participants.filterTo(receivers) { shares[it] == 34L }
        }

        assertEquals(participants.toSet(), receivers)
    }

    @Test
    fun `starts at the first participant for an expense without an id`() {
        val shares = expenseShares(
            expense(null, 100, listOf("bob" to 1L, "alice" to 1L, "carol" to 1L)),
        )

        assertEquals(34L, shares["alice"])
        assertEquals(100L, shares.sum())
    }

    // --- participantShare --------------------------------------------------

    private val evenly = expense("e1", 100, listOf("alice" to 1L, "bob" to 1L))

    @Test
    fun `returns the participant's share`() {
        assertEquals(50L, participantShare("alice", evenly))
    }

    @Test
    fun `returns zero for someone the expense was not paid for`() {
        assertEquals(0L, participantShare("carol", evenly))
    }

    @Test
    fun `returns zero when there is no active participant`() {
        assertEquals(0L, participantShare(null, evenly))
    }

    // --- distributeAmount --------------------------------------------------

    @Test
    fun `distributes an amount that does not divide evenly`() {
        assertEquals(listOf(3167L, 3167L, 3166L), distributeAmount(9500, 3))
    }

    @Test
    fun `divides an even amount evenly`() {
        assertEquals(listOf(300L, 300L, 300L), distributeAmount(900, 3))
    }

    @Test
    fun `distributes a negative amount`() {
        assertEquals(listOf(-3166L, -3167L, -3167L), distributeAmount(-9500, 3))
    }

    @Test
    fun `has nothing to distribute over nobody`() {
        assertEquals(emptyList<Long>(), distributeAmount(100, 0))
    }
}
