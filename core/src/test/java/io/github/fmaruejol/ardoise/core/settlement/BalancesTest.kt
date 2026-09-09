package io.github.fmaruejol.ardoise.core.settlement

import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `src/lib/balances.test.ts` in `spliit-app/spliit`, including the
 * randomised property tests and their seeds. What matters most is that the
 * totals sum to exactly zero.
 */
class BalancesTest {
    private fun expense(
        paidBy: String,
        amount: Long,
        paidFor: List<String>,
        id: String? = null,
    ) = BalanceInput(
        amount = amount,
        splitMode = SplitMode.EVENLY,
        paidById = paidBy,
        paidFor = paidFor.map { PaidFor(it, 100) },
        id = id,
    )

    private fun splitExpense(
        id: String,
        paidBy: String,
        amount: Long,
        paidFor: List<Pair<String, Long>>,
        splitMode: SplitMode = SplitMode.EVENLY,
    ) = BalanceInput(
        amount = amount,
        splitMode = splitMode,
        paidById = paidBy,
        paidFor = paidFor.map { (participantId, shares) -> PaidFor(participantId, shares) },
        id = id,
    )

    private fun evenly(id: String, paidBy: String, amount: Long, paidFor: List<String>) =
        splitExpense(id, paidBy, amount, paidFor.map { it to 1L })

    private fun Map<String, io.github.fmaruejol.ardoise.core.model.Balance>.sumOfTotals(): Long =
        values.sumOf { it.total }

    // --- balances ----------------------------------------------------------

    @Test
    fun `splits an expense evenly between the participants`() {
        val result = balances(listOf(expense("alice", 3000, listOf("alice", "bob"))))

        assertEquals(3000L, result.getValue("alice").paid)
        assertEquals(1500L, result.getValue("alice").paidFor)
        assertEquals(1500L, result.getValue("alice").total)
        assertEquals(0L, result.getValue("bob").paid)
        assertEquals(1500L, result.getValue("bob").paidFor)
        assertEquals(-1500L, result.getValue("bob").total)
    }

    @Test
    fun `counts a reimbursement like any other expense`() {
        val result = balances(
            listOf(
                expense("alice", 3000, listOf("alice", "bob")),
                expense("bob", 1500, listOf("alice")),
            ),
        )

        assertEquals(0L, result.getValue("alice").total)
        assertEquals(0L, result.getValue("bob").total)
    }

    // --- settling up -------------------------------------------------------

    @Test
    fun `brings every balance to zero once the suggestions are booked`() {
        val expenses = listOf(
            expense("alice", 500000, listOf("alice", "bob", "carol")),
            expense("bob", 12345, listOf("alice", "bob", "carol")),
            expense("carol", 999, listOf("alice", "bob")),
        )

        val reimbursements = suggestedReimbursements(balances(expenses))
        assertTrue(reimbursements.isNotEmpty())

        val settled = expenses + reimbursements.map {
            expense(it.fromParticipantId, it.amount, listOf(it.toParticipantId))
        }

        balances(settled).values.forEach { assertEquals(0L, it.total) }
    }

    @Test
    fun `leaves no further reimbursement to suggest`() {
        val expenses = listOf(expense("alice", 7777, listOf("alice", "bob")))
        val reimbursements = suggestedReimbursements(balances(expenses))

        val settled = expenses + reimbursements.map {
            expense(it.fromParticipantId, it.amount, listOf(it.toParticipantId))
        }

        assertEquals(emptyList<Any>(), suggestedReimbursements(balances(settled)))
    }

    // --- balances sum to zero ----------------------------------------------

    @Test
    fun `holds for an expense that does not divide evenly`() {
        // 100 split three ways used to leave a stranded minor unit upstream
        // that no reimbursement would ever offer back.
        val result = balances(listOf(evenly("e1", "alice", 100, listOf("alice", "bob", "carol"))))

        assertEquals(0L, result.sumOfTotals())
        assertEquals(100L, result.values.sumOf { it.paidFor })
    }

    @Test
    fun `holds for an expense split seven ways`() {
        val participants = listOf("a", "b", "c", "d", "e", "f", "g")
        val result = balances(listOf(evenly("e1", "a", 1000, participants)))

        assertEquals(0L, result.sumOfTotals())
        assertEquals(1000L, result.values.sumOf { it.paidFor })
    }

    @Test
    fun `holds across randomised amounts, participants and split modes`() {
        val random = Lcg(20260804)
        var runsWithDebt = 0

        repeat(500) { run ->
            val participants = List(2 + random.next(7)) { "participant-$it" }
            val expenses = List(1 + random.next(6)) { index ->
                randomExpense("run-$run-expense-$index", participants, random)
            }

            val result = balances(expenses)
            assertEquals("run $run", 0L, result.sumOfTotals())
            if (result.values.any { it.total != 0L }) runsWithDebt++
        }

        // Zero totals summing to zero would pass vacuously; most runs must
        // actually have someone owing someone.
        assertTrue("only $runsWithDebt runs produced a debt", runsWithDebt > 400)
    }

    @Test
    fun `offers the whole credit back through the suggested reimbursements`() {
        val expenses = listOf(evenly("e1", "alice", 100, listOf("alice", "bob", "carol")))
        val result = balances(expenses)
        val reimbursements = suggestedReimbursements(result)

        val offeredToAlice = reimbursements
            .filter { it.toParticipantId == "alice" }
            .sumOf { it.amount }

        assertEquals(result.getValue("alice").total, offeredToAlice)
    }

    @Test
    fun `settles completely once every suggestion is booked`() {
        val random = Lcg(1312)
        var settledRunsWithReimbursements = 0

        repeat(100) { run ->
            val participants = List(2 + random.next(5)) { "participant-$it" }
            val expenses = List(1 + random.next(4)) { index ->
                randomExpense("run-$run-expense-$index", participants, random)
            }

            val reimbursements = suggestedReimbursements(balances(expenses))
            settledRunsWithReimbursements += if (reimbursements.isNotEmpty()) 1 else 0
            val settled = expenses + reimbursements.mapIndexed { index, reimbursement ->
                evenly(
                    "run-$run-settle-$index",
                    reimbursement.fromParticipantId,
                    reimbursement.amount,
                    listOf(reimbursement.toParticipantId),
                )
            }

            balances(settled).values.forEach { assertEquals("run $run", 0L, it.total) }
        }

        assertTrue(
            "only $settledRunsWithReimbursements runs needed settling",
            settledRunsWithReimbursements > 80,
        )
    }

    // --- remainder distribution --------------------------------------------

    @Test
    fun `does not always hand the extra minor unit to the same participant`() {
        val participants = listOf("alice", "bob", "carol")
        val receivers = mutableSetOf<String>()

        repeat(50) { index ->
            val result = balances(listOf(evenly("expense-$index", "alice", 100, participants)))
            participants.filterTo(receivers) { result.getValue(it).paidFor == 34L }
        }

        assertEquals(participants.toSet(), receivers)
    }

    @Test
    fun `gives the same expense the same split every time`() {
        fun split() = balances(
            listOf(evenly("expense-1", "alice", 100, listOf("alice", "bob", "carol"))),
        )

        assertEquals(split(), split())
    }

    @Test
    fun `does not depend on the order the participants come back from the database`() {
        val paidFor = listOf("alice" to 1L, "bob" to 1L, "carol" to 1L)
        val inOrder = balances(listOf(splitExpense("e1", "alice", 100, paidFor)))
        val reversed = balances(listOf(splitExpense("e1", "alice", 100, paidFor.reversed())))

        assertEquals(inOrder, reversed)
    }

    @Test
    fun `splits an expense entered without an id`() {
        val result = balances(listOf(expense("alice", 100, listOf("alice", "bob", "carol"))))

        assertEquals(0L, result.sumOfTotals())
    }

    // --- split modes -------------------------------------------------------

    @Test
    fun `leaves exact BY_AMOUNT shares untouched`() {
        val result = balances(
            listOf(
                splitExpense(
                    "e1",
                    "alice",
                    1000,
                    listOf("alice" to 333L, "bob" to 333L, "carol" to 334L),
                    SplitMode.BY_AMOUNT,
                ),
            ),
        )

        assertEquals(333L, result.getValue("alice").paidFor)
        assertEquals(333L, result.getValue("bob").paidFor)
        assertEquals(334L, result.getValue("carol").paidFor)
        assertEquals(0L, result.sumOfTotals())
    }

    @Test
    fun `rounds BY_PERCENTAGE shares without losing a minor unit`() {
        val result = balances(
            listOf(
                splitExpense(
                    "e1",
                    "alice",
                    100,
                    listOf("alice" to 3333L, "bob" to 3333L, "carol" to 3334L),
                    SplitMode.BY_PERCENTAGE,
                ),
            ),
        )

        assertEquals(0L, result.sumOfTotals())
        assertEquals(100L, result.values.sumOf { it.paidFor })
    }

    @Test
    fun `splits BY_SHARES proportionally and to the last minor unit`() {
        val result = balances(
            listOf(
                splitExpense(
                    "e1",
                    "alice",
                    100,
                    listOf("alice" to 1L, "bob" to 2L),
                    SplitMode.BY_SHARES,
                ),
            ),
        )

        assertEquals(33L, result.getValue("alice").paidFor)
        assertEquals(67L, result.getValue("bob").paidFor)
        assertEquals(0L, result.sumOfTotals())
    }

    @Test
    fun `splits an income without losing a minor unit`() {
        val result = balances(listOf(evenly("e1", "alice", -100, listOf("alice", "bob", "carol"))))

        assertEquals(0L, result.sumOfTotals())
        assertEquals(-100L, result.values.sumOf { it.paidFor })
    }

    // --- settle ------------------------------------------------------------

    @Test
    fun `settle returns the public balances derived from the reimbursements`() {
        val expenses = listOf(expense("alice", 3000, listOf("alice", "bob")))

        val settled = settle(expenses)

        assertEquals(1, settled.reimbursements.size)
        val reimbursement = settled.reimbursements.single()
        assertEquals("bob", reimbursement.fromParticipantId)
        assertEquals("alice", reimbursement.toParticipantId)
        assertEquals(1500L, reimbursement.amount)
        // Public balances read as "will receive" / "will pay", not as spending.
        assertEquals(1500L, settled.balances.single { it.participantId == "alice" }.paid)
        assertEquals(1500L, settled.balances.single { it.participantId == "bob" }.paidFor)
        assertEquals(0L, settled.balances.sumOf { it.total })
    }

    // --- helpers -----------------------------------------------------------

    /** A seeded LCG, so a failing case is reproducible from the seed alone. */
    private class Lcg(seed: Int) {
        private var state: Int = seed

        fun next(bound: Int): Int {
            state = state * 1664525 + 1013904223
            return ((state.toLong() and 0xFFFFFFFFL) % bound).toInt()
        }
    }

    /** Splits [total] into [count] positive integers adding up to exactly [total]. */
    private fun distribute(total: Long, count: Int): List<Long> {
        val base = total / count
        val shares = MutableList(count) { base }
        for (i in 0 until (total - base * count).toInt()) shares[i] += 1
        return shares
    }

    private fun randomExpense(
        id: String,
        participants: List<String>,
        random: Lcg,
    ): BalanceInput {
        val splitMode = SplitMode.entries[random.next(SplitMode.entries.size)]
        val paidFor = participants.filter { random.next(2) == 0 }
            .ifEmpty { listOf(participants[random.next(participants.size)]) }
        // BY_AMOUNT shares must add up to the amount and none may be zero, so
        // the amount has to be at least the number of participants.
        val amount = paidFor.size + random.next(100000).toLong()
        val paidBy = participants[random.next(participants.size)]

        return when (splitMode) {
            SplitMode.BY_SHARES -> {
                splitExpense(
                    id,
                    paidBy,
                    amount,
                    paidFor.map { it to 1L + random.next(10) },
                    splitMode,
                )
            }

            SplitMode.BY_PERCENTAGE, SplitMode.BY_AMOUNT -> {
                // Percentages are stored times 100, so a full split is 10000.
                val shares = distribute(
                    if (splitMode == SplitMode.BY_PERCENTAGE) 10000L else amount,
                    paidFor.size,
                )
                splitExpense(
                    id,
                    paidBy,
                    amount,
                    paidFor.mapIndexed { index, participant -> participant to shares[index] },
                    splitMode,
                )
            }

            SplitMode.EVENLY -> {
                splitExpense(id, paidBy, amount, paidFor.map { it to 1L }, splitMode)
            }
        }
    }

    // --- one expense, one participant --------------------------------------

    @Test
    fun `paying for others puts you up by what they owe you`() {
        // 480 paid, split five ways: 96 of it is your own share, so you are up
        // the other 384.
        val airbnb = expense("ana", 48_000, listOf("ana", "ben", "cleo", "dan", "eli"))

        assertEquals(38_400, participantBalanceChange("ana", airbnb))
    }

    @Test
    fun `somebody else paying puts you down by your share`() {
        val surf = expense("ben", 14_000, listOf("ana", "ben", "cleo", "dan"))

        assertEquals(-3_500, participantBalanceChange("ana", surf))
    }

    @Test
    fun `an expense you are not part of leaves you where you were`() {
        val taxi = expense("dan", 1_850, listOf("ben", "cleo", "dan"))

        assertEquals(0, participantBalanceChange("ana", taxi))
    }

    @Test
    fun `paying for only yourself is a wash`() {
        val coffee = expense("ana", 350, listOf("ana"))

        assertEquals(0, participantBalanceChange("ana", coffee))
    }

    @Test
    fun `nobody is worth nothing`() {
        val dinner = expense("ana", 9_600, listOf("ana", "ben"))

        assertEquals(0, participantBalanceChange(null, dinner))
    }

    @Test
    fun `is exactly what balances says for that one expense`() {
        // The feed and the balances screen must never disagree, so this is the
        // same definition rather than a second one that happens to match.
        val expenses = listOf(
            expense("ana", 48_000, listOf("ana", "ben", "cleo", "dan", "eli")),
            expense("ben", 14_000, listOf("ana", "ben", "cleo", "dan")),
            splitExpense(
                id = "x1",
                paidBy = "cleo",
                amount = 9_601,
                paidFor = listOf("ana" to 100L, "ben" to 200L, "cleo" to 300L),
                splitMode = SplitMode.BY_SHARES,
            ),
        )

        for (expense in expenses) {
            for (participant in listOf("ana", "ben", "cleo", "dan", "eli")) {
                assertEquals(
                    balances(listOf(expense))[participant]?.total ?: 0,
                    participantBalanceChange(participant, expense),
                )
            }
        }
    }

    @Test
    fun `the changes on one expense sum to zero across the group`() {
        // An odd amount over three people leaves a minor unit to rotate; it
        // has to land on somebody, and never outside the group.
        val awkward = expense("ana", 1_000, listOf("ana", "ben", "cleo"))

        val total = listOf("ana", "ben", "cleo")
            .sumOf { participantBalanceChange(it, awkward) }
        assertEquals(0, total)
    }
}
