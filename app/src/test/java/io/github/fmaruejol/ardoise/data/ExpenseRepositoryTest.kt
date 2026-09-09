package io.github.fmaruejol.ardoise.data

import android.app.Application
import app.cash.turbine.test
import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpenseDocument
import io.github.fmaruejol.ardoise.core.model.ExpensePage
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.PaidForWithParticipant
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val repository = ExpenseRepository(api, preferences, cache, outbox)

    private val expenseInput = ExpenseInput(
        title = "Dinner",
        amount = 4_250,
        expenseDate = LocalDate.of(2026, 9, 4),
        paidById = "p1",
        paidFor = listOf(PaidFor("p1", 100), PaidFor("p2", 100)),
        splitMode = SplitMode.EVENLY,
    )

    @Test
    fun `asks for the first page with the server's own default size`() = runTest(dispatcher) {
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()
            assertEquals(
                listOf("g1", 0, ExpenseRepository.DEFAULT_PAGE_SIZE, null),
                api.callsTo("listExpenses").single().arguments,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refetches the list once a new expense is created`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()

            api.listExpensesResult = page(expense("e1", "Dinner"), expense("e2", "Taxi"))
            repository.create("g1", expenseInput)
            advanceUntilIdle()

            // The cache is rewritten and Room wakes this read. Nothing was
            // told to reload: the write and the read share a table.
            assertEquals(listOf("e1", "e2"), awaitItem().expenseIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a change to one expense refreshes the balances and the log with it`() =
        runTest(dispatcher) {
            repository.create("g1", expenseInput)
            advanceUntilIdle()

            // The server recomputes both as a side effect of the mutation, and
            // Room cannot know that.
            assertEquals(1, api.callsTo("listBalances").size)
            assertEquals(1, api.callsTo("listActivities").size)
        }

    @Test
    fun `leaves the list alone when creating an expense fails`() = runTest(dispatcher) {
        api.createExpenseResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))

        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()
            repository.create("g1", expenseInput)
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refetches the list after a delete`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()

            api.listExpensesResult = page()
            repository.delete("g1", "e1")
            advanceUntilIdle()

            // An expense deleted elsewhere has to leave the cache: the page is
            // replaced, not merged.
            assertEquals(emptyList<String>(), awaitItem().expenseIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not refetch a different group's expenses`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()

            api.listExpensesResult = page()
            repository.create("other", expenseInput)
            advanceUntilIdle()

            // Every query is scoped to its group, so another group's rows
            // cannot wake this one.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `serves the cache before the network answers`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            assertEquals(listOf("e1"), awaitItem().expenseIds())
            cancelAndIgnoreRemainingEvents()
        }

        // A second reader gets the cached page first, and only then whatever
        // the network has to say.
        api.listExpensesResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("off")))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            assertEquals(listOf("e1"), awaitItem().expenseIds())
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed refresh leaves the cache alone`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        api.listExpensesResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("off")))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            // The last thing the server said is a better answer than nothing.
            assertEquals(listOf("e1"), awaitItem().expenseIds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a search goes to the server and is never cached`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        repository.expenses("g1", filter = "dinner").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Storing a search would overwrite the feed with a subset of itself,
        // so the feed's cache is still empty and has nothing to serve.
        api.listExpensesResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("off")))
        repository.expenses("g1").test(timeout = TURBINE_TIMEOUT) {
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun page(vararg expenses: ExpenseSummary) =
        SpliitResult.Success(ExpensePage(expenses.toList(), hasMore = false))

    private fun expense(id: String, title: String) = ExpenseSummary(
        id = id,
        title = title,
        amount = 4_250,
        originalAmount = null,
        originalCurrency = null,
        expenseDate = LocalDate.of(2026, 9, 4),
        createdAt = Instant.parse("2026-09-04T12:00:00Z"),
        category = null,
        paidBy = Participant("p1", "Ada"),
        paidFor = listOf(PaidForWithParticipant(Participant("p1", "Ada"), 1)),
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = false,
        documentCount = 0,
    )

    private fun fullExpense(id: String) = Expense(
        id = id,
        groupId = "g1",
        title = "Dinner",
        amount = 4_250,
        originalAmount = null,
        originalCurrency = null,
        conversionRate = null,
        expenseDate = LocalDate.of(2026, 9, 4),
        createdAt = Instant.parse("2026-09-04T12:00:00Z"),
        category = null,
        paidById = "p1",
        paidFor = listOf(PaidFor("p1", 1)),
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = false,
        notes = null,
        documents = emptyList(),
    )

    private fun SpliitResult<ExpensePage>.expenseIds(): List<String> =
        (this as SpliitResult.Success).value.expenses.map { it.id }

    @Test
    fun `attributes a mutation to the participant the user says they are`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        api.getExpenseResult = SpliitResult.Success(fullExpense("e1"))

        repository.create("g1", expenseInput)
        repository.update("g1", "e1", expenseInput)
        repository.delete("g1", "e1")

        assertEquals("p2", api.callsTo("createExpense").single().arguments[2])
        assertEquals("p2", api.callsTo("updateExpense").single().arguments[3])
        assertEquals("p2", api.callsTo("deleteExpense").single().arguments[2])
    }

    @Test
    fun `sends no participant when the user has not said who they are`() = runTest(dispatcher) {
        repository.create("g1", expenseInput)

        assertEquals(null, api.callsTo("createExpense").single().arguments[2])
    }

    @Test
    fun `passes the expense input through untouched`() = runTest(dispatcher) {
        repository.create("g1", expenseInput)

        // Shares are already in the stored representation for the split mode;
        // nothing in the repository may rescale them.
        assertEquals(expenseInput, api.callsTo("createExpense").single().arguments[1])
    }

    @Test
    fun `a longer page is one request, not a second one`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))

        repository.expenses("g1", limit = 60).test(timeout = TURBINE_TIMEOUT) {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // "Show older" asks for a longer page rather than for the next one.
        assertEquals(
            listOf("g1", 0, 60, null),
            api.callsTo("listExpenses").single().arguments,
        )
    }

    // --- what the feed refresh must not throw away -------------------------

    @Test
    fun `a feed refresh keeps the note the list does not carry`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        api.getExpenseResult = SpliitResult.Success(
            fullExpense("e1").copy(notes = "Split with Ben at the bar"),
        )
        // The detail read is what puts the note in the cache.
        repository.expense("g1", "e1").first()
        advanceUntilIdle()

        // Now the feed refreshes, as it does on every visit to the group.
        cache.refreshExpenses("g1", 20)
        advanceUntilIdle()

        // `groups.expenses.list` has no notes field, so replacing the row from
        // a summary used to blank it, quietly deleting the note from the
        // cache and from the screen that reads it.
        api.getExpenseResult = SpliitResult.Failure(
            SpliitError.Network(RuntimeException("offline")),
        )
        val cached = repository.expense("g1", "e1").first() as SpliitResult.Success<Expense>
        assertEquals("Split with Ben at the bar", cached.value.notes)
    }

    @Test
    fun `an edit keeps the documents the expense already had`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(
            fullExpense("e1").copy(
                documents = listOf(ExpenseDocument("d1", "https://s3.example/receipt.jpg", 800, 600)),
            ),
        )

        repository.update("g1", "e1", expenseInput)

        val sent = api.callsTo("updateExpense").single().arguments[2] as ExpenseInput
        assertEquals(listOf("d1"), sent.documents.map { it.id })
    }

    @Test
    fun `an edit that cannot read the expense back is not sent`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Failure(SpliitError.Network(IOException("no network")))

        val result = repository.update("g1", "e1", expenseInput)

        assertTrue(result is SpliitResult.Failure)
        assertTrue(api.callsTo("updateExpense").isEmpty())
    }

    @Test
    fun `editing an expense refreshes the one that was edited`() = runTest(dispatcher) {
        api.listExpensesResult = page(expense("e1", "Dinner"))
        api.getExpenseResult = SpliitResult.Success(fullExpense("e1").copy(notes = "before"))
        repository.expense("g1", "e1").first()
        advanceUntilIdle()

        api.getExpenseResult = SpliitResult.Success(fullExpense("e1").copy(notes = "after"))
        repository.update("g1", "e1", expenseInput)
        advanceUntilIdle()

        // The detail screen the user comes back to reads the cache, and the
        // feed refresh alone cannot tell it what the note now says.
        assertTrue(api.callsTo("getExpense").size >= 2)
    }

    // --- what a shorter page must not throw away ---------------------------

    private fun pageOf(vararg expenses: ExpenseSummary, hasMore: Boolean) =
        SpliitResult.Success(ExpensePage(expenses.toList(), hasMore = hasMore))

    private fun dated(id: String, day: Int) =
        expense(id, "Expense $id").copy(
            expenseDate = LocalDate.of(2026, 9, day),
            createdAt = Instant.parse("2026-09-%02dT12:00:00Z".format(day)),
        )

    @Test
    fun `an expense older than the page stays in the cache`() = runTest(dispatcher) {
        // The user scrolled back and read the older one, so it is cached.
        api.listExpensesResult = pageOf(dated("new", 9), dated("old", 1), hasMore = false)
        cache.refreshExpenses("g1", 60)
        advanceUntilIdle()

        // A shorter page, the feed reloaded at its default size, with more
        // beyond it.
        api.listExpensesResult = pageOf(dated("new", 9), hasMore = true)
        cache.refreshExpenses("g1", 1)
        advanceUntilIdle()

        // Outside the window is not the same as gone.
        api.getExpenseResult = SpliitResult.Failure(
            SpliitError.Network(RuntimeException("offline")),
        )
        assertEquals(
            listOf("new", "old"),
            cache.expenses("g1", limit = 60).first().map { it.id },
        )
    }

    @Test
    fun `an expense deleted upstream still goes`() = runTest(dispatcher) {
        api.listExpensesResult = pageOf(dated("a", 9), dated("b", 5), dated("c", 1), hasMore = false)
        cache.refreshExpenses("g1", 60)
        advanceUntilIdle()

        // "b" was deleted on another device: the page still reaches back to
        // "c", so its absence is an answer and not a window.
        api.listExpensesResult = pageOf(dated("a", 9), dated("c", 1), hasMore = true)
        cache.refreshExpenses("g1", 2)
        advanceUntilIdle()

        assertEquals(
            listOf("a", "c"),
            cache.expenses("g1", limit = 60).first().map { it.id },
        )
    }

    @Test
    fun `a complete page is the whole group, so anything missing is gone`() =
        runTest(dispatcher) {
            api.listExpensesResult = pageOf(dated("a", 9), dated("b", 1), hasMore = false)
            cache.refreshExpenses("g1", 60)
            advanceUntilIdle()

            api.listExpensesResult = pageOf(dated("a", 9), hasMore = false)
            cache.refreshExpenses("g1", 60)
            advanceUntilIdle()

            // Nothing beyond the page means nothing older to keep.
            assertEquals(listOf("a"), cache.expenses("g1", limit = 60).first().map { it.id })
        }

    // --- the refresh after a mutation has to cover what is cached ----------

    @Test
    fun `a mutation refreshes a page as wide as the cache, not the default one`() =
        runTest(dispatcher) {
            // The feed opens on thirty rows and "show older" goes further, so
            // the cache is routinely wider than the repository's default page.
            api.listExpensesResult = pageOf(*(1..12).map { dated("e$it", it) }.toTypedArray(), hasMore = false)
            cache.refreshExpenses("g1", 60)
            advanceUntilIdle()
            api.calls.clear()

            repository.delete("g1", "e5")
            advanceUntilIdle()

            // A narrower page describes a narrower window, and a refresh only
            // deletes rows inside the window it was given, so refreshing at
            // the default ten left an expense deleted from row fifteen sitting
            // in the cache and on the feed, while the balances refreshed by
            // the same call no longer counted it.
            val limit = api.callsTo("listExpenses").single().arguments[2] as Int
            assertTrue("refreshed at $limit, cache holds 12", limit >= 12)
        }

    @Test
    fun `a mutation still asks for the default page when the cache is smaller`() =
        runTest(dispatcher) {
            api.listExpensesResult = pageOf(dated("e1", 1), hasMore = false)
            cache.refreshExpenses("g1", 60)
            advanceUntilIdle()
            api.calls.clear()

            repository.delete("g1", "e1")
            advanceUntilIdle()

            // Widening is a floor, not a multiplier: a small group must not
            // start asking for more than the feed would have shown anyway.
            assertEquals(
                listOf("g1", 0, ExpenseRepository.DEFAULT_PAGE_SIZE, null),
                api.callsTo("listExpenses").single().arguments,
            )
        }

    @Test
    fun `kept rows are renumbered to sit after the page`() = runTest(dispatcher) {
        api.listExpensesResult = pageOf(dated("b", 5), dated("c", 1), hasMore = false)
        cache.refreshExpenses("g1", 60)
        advanceUntilIdle()

        // A newer expense appears upstream and the page is refetched short.
        api.listExpensesResult = pageOf(dated("a", 9), dated("b", 5), hasMore = true)
        cache.refreshExpenses("g1", 2)
        advanceUntilIdle()

        // "c" was at position 1 and the page now owns 0 and 1. Left alone it
        // would sit level with "b" and the feed's order would be a coin toss.
        assertEquals(
            listOf("a", "b", "c"),
            cache.expenses("g1", limit = 60).first().map { it.id },
        )
    }
}
