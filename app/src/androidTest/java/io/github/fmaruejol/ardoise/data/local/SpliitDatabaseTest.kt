package io.github.fmaruejol.ardoise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The DAOs against real SQLite on a device.
 *
 * The repository tests run these same queries under Robolectric, which is
 * enough for their logic. This is here for the part Robolectric cannot vouch
 * for: that the schema Room generates is one the device's SQLite will actually
 * accept, and that the queries behave the same there, a reserved word in a
 * table name, a bad index, a `@Relation` that does not resolve, all fail here
 * and nowhere else.
 *
 * `groups` in particular is close to reserved in SQL, which is why every query
 * over it quotes the name.
 */
@RunWith(AndroidJUnit4::class)
class SpliitDatabaseTest {
    private lateinit var database: SpliitDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SpliitDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = database.close()

    private fun group(id: String) = GroupEntity(
        id = id,
        name = "Trip",
        information = null,
        currencySymbol = "€",
        currencyCode = "EUR",
        createdAt = 0,
        participantCount = 2,
        hasParticipants = true,
    )

    private fun expense(id: String, groupId: String, position: Int) = ExpenseEntity(
        id = id,
        groupId = groupId,
        title = id,
        amount = 1234,
        originalAmount = null,
        originalCurrency = null,
        conversionRate = null,
        expenseDate = "2026-09-11",
        createdAt = 0,
        categoryId = null,
        paidById = "p1",
        splitMode = "EVENLY",
        recurrenceRule = "NONE",
        isReimbursement = false,
        notes = null,
        documentCount = 0,
        position = position,
    )

    @Test
    fun replacesAGroupAndEveryoneInIt() = runBlocking {
        database.groups().replace(
            group("g1"),
            listOf(
                ParticipantEntity("g1", "p1", "Ada", 0),
                ParticipantEntity("g1", "p2", "Ben", 1),
            ),
        )

        // Ben has left the group upstream. An upsert would keep him.
        database.groups().replace(
            group("g1"),
            listOf(ParticipantEntity("g1", "p1", "Ada", 0)),
        )

        assertEquals(listOf("Ada"), database.groups().participants("g1").map { it.name })
    }

    @Test
    fun keepsTheGroupsOwnParticipantOrder() = runBlocking {
        database.groups().replace(
            group("g1"),
            listOf(
                ParticipantEntity("g1", "p2", "Ben", 1),
                ParticipantEntity("g1", "p1", "Ada", 0),
            ),
        )

        // Inserted out of order; read back in the group's order, which is what
        // "which one is you" and every avatar colour is keyed on.
        assertEquals(listOf("Ada", "Ben"), database.groups().participants("g1").map { it.name })
    }

    @Test
    fun readsAnExpenseWithTheRowsSayingWhoItWasFor() = runBlocking {
        database.expenses().replacePage(
            groupId = "g1",
            expenses = listOf(expense("e1", "g1", 0)),
            paidFor = listOf(
                PaidForEntity("e1", "p1", 100, 0),
                PaidForEntity("e1", "p2", 200, 1),
            ),
            complete = true,
        )

        val row = database.expenses().observePage("g1", 10).first().single()
        assertEquals("e1", row.expense.id)
        // Shares are not amounts and their meaning depends on the split mode;
        // whatever the server sent has to come back unchanged.
        assertEquals(listOf(100L, 200L), row.paidFor.sortedBy { it.position }.map { it.shares })
    }

    @Test
    fun replacingAPageTakesTheOldRowsWithIt() = runBlocking {
        database.expenses().replacePage(
            groupId = "g1",
            expenses = listOf(expense("e1", "g1", 0), expense("e2", "g1", 1)),
            paidFor = listOf(PaidForEntity("e1", "p1", 1, 0), PaidForEntity("e2", "p1", 1, 0)),
            complete = true,
        )

        // e1 was deleted on another device.
        database.expenses().replacePage(
            groupId = "g1",
            expenses = listOf(expense("e2", "g1", 0)),
            paidFor = listOf(PaidForEntity("e2", "p1", 1, 0)),
            complete = true,
        )

        val rows = database.expenses().observePage("g1", 10).first()
        assertEquals(listOf("e2"), rows.map { it.expense.id })
        // And its paidFor rows are not left behind to attach themselves to the
        // next expense that happens to reuse the id.
        assertEquals(1, rows.single().paidFor.size)
    }

    @Test
    fun leavesRowsOlderThanThePageWhereTheyAre() = runBlocking {
        val older = expense("old", "g1", 1).copy(expenseDate = "2026-09-01", createdAt = 1)
        val newer = expense("new", "g1", 0).copy(expenseDate = "2026-09-11", createdAt = 2)
        database.expenses().replacePage("g1", listOf(newer, older), emptyList(), complete = true)

        // A shorter page, with more beyond it: "old" is outside the window
        // rather than deleted, so it stays and moves down behind the page.
        database.expenses().replacePage("g1", listOf(newer), emptyList(), complete = false)

        val rows = database.expenses().observePage("g1", 10).first()
        assertEquals(listOf("new", "old"), rows.map { it.expense.id })
        assertEquals(listOf(0, 1), rows.map { it.expense.position })
    }

    @Test
    fun dropsARowThePageReachedPastButDidNotBringBack() = runBlocking {
        val a = expense("a", "g1", 0).copy(expenseDate = "2026-09-11", createdAt = 3)
        val b = expense("b", "g1", 1).copy(expenseDate = "2026-09-05", createdAt = 2)
        val c = expense("c", "g1", 2).copy(expenseDate = "2026-09-01", createdAt = 1)
        database.expenses().replacePage("g1", listOf(a, b, c), emptyList(), complete = true)

        // The page still reaches back to "c", so "b" is missing rather than
        // merely out of view: it was deleted on another device.
        database.expenses().replacePage("g1", listOf(a, c), emptyList(), complete = false)

        assertEquals(
            listOf("a", "c"),
            database.expenses().observePage("g1", 10).first().map { it.expense.id },
        )
    }

    @Test
    fun keepsOneGroupsExpensesOutOfAnother() = runBlocking {
        database.expenses().replacePage("g1", listOf(expense("e1", "g1", 0)), emptyList(), complete = true)
        database.expenses().replacePage("g2", listOf(expense("e2", "g2", 0)), emptyList(), complete = true)

        assertEquals(1, database.expenses().count("g1"))
        assertEquals(
            listOf("e2"),
            database.expenses().observePage("g2", 10).first().map { it.expense.id },
        )
    }

    @Test
    fun readsTheFeedInTheOrderTheServerGaveIt() = runBlocking {
        database.expenses().replacePage(
            groupId = "g1",
            expenses = listOf(expense("third", "g1", 2), expense("first", "g1", 0)),
            paidFor = emptyList(),
            complete = true,
        )

        // "Newest first" is a rule the server owns; the cache keeps its order
        // rather than re-deriving one from the dates.
        assertEquals(
            listOf("first", "third"),
            database.expenses().observePage("g1", 10).first().map { it.expense.id },
        )
    }

    @Test
    fun keepsTheSuggestedPaymentsInOrder() = runBlocking {
        database.balances().replace(
            groupId = "g1",
            balances = listOf(BalanceEntity("g1", "p1", 0, 0, 9000)),
            reimbursements = listOf(
                ReimbursementEntity("g1", 0, "p2", "p1", 6000),
                ReimbursementEntity("g1", 1, "p3", "p1", 3000),
            ),
        )

        // They are a chain: each assumes the ones above it have been made.
        assertEquals(
            listOf("p2", "p3"),
            database.balances().observeReimbursements("g1").first().map { it.fromParticipantId },
        )
    }

    @Test
    fun forgettingAGroupLeavesNothingBehind() = runBlocking {
        database.groups().replace(group("g1"), listOf(ParticipantEntity("g1", "p1", "Ada", 0)))
        database.expenses().replacePage(
            "g1",
            listOf(expense("e1", "g1", 0)),
            listOf(PaidForEntity("e1", "p1", 1, 0)),
            complete = true,
        )
        database.activities().replace(
            "g1",
            listOf(ActivityEntity("a1", "g1", 0, "CREATE_EXPENSE", "p1", "e1", "Dinner", 0)),
        )

        database.balances().replace(
            "g1",
            listOf(BalanceEntity("g1", "p1", 0, 0, 1234)),
            listOf(ReimbursementEntity("g1", 0, "p1", "p2", 1234)),
        )

        // One call, one transaction. Seven statements spread across the
        // callers was seven things to keep in step with the schema.
        database.cache().forget("g1")

        assertNull(database.groups().observe("g1").first())
        assertTrue(database.groups().participants("g1").isEmpty())
        assertEquals(0, database.expenses().count("g1"))
        assertTrue(database.activities().observe("g1", 10).first().isEmpty())
        assertTrue(database.balances().observeBalances("g1").first().isEmpty())
        assertTrue(database.balances().observeReimbursements("g1").first().isEmpty())
    }

    @Test
    fun forgettingOneGroupLeavesTheOthersAlone() = runBlocking {
        database.groups().replace(group("g1"), listOf(ParticipantEntity("g1", "p1", "Ada", 0)))
        database.groups().replace(group("g2"), listOf(ParticipantEntity("g2", "p9", "Zoe", 0)))
        database.expenses().replacePage("g1", listOf(expense("e1", "g1", 0)), emptyList(), complete = true)
        database.expenses().replacePage("g2", listOf(expense("e2", "g2", 0)), emptyList(), complete = true)

        database.cache().forget("g1")

        assertEquals("Trip", database.groups().observe("g2").first()?.name)
        assertEquals(listOf("Zoe"), database.groups().participants("g2").map { it.name })
        assertEquals(1, database.expenses().count("g2"))
    }

    @Test
    fun aPageIsCappedAtTheLimitAsked() = runBlocking {
        database.expenses().replacePage(
            groupId = "g1",
            expenses = (0 until 5).map { expense("e$it", "g1", it) },
            paidFor = emptyList(),
            complete = true,
        )

        assertEquals(2, database.expenses().observePage("g1", 2).first().size)
    }
}
