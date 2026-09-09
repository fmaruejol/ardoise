package io.github.fmaruejol.ardoise.data

import android.app.Application
import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.core.model.Category
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** The queue of expenses typed offline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseOutboxTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)

    private fun input(title: String = "Taxi", amount: Long = 2200) = ExpenseInput(
        title = title,
        amount = amount,
        expenseDate = LocalDate.parse("2026-09-11"),
        paidById = "p1",
        paidFor = listOf(PaidFor("p1", 1), PaidFor("p2", 1)),
        splitMode = SplitMode.EVENLY,
    )

    private fun offline() = SpliitError.Network(RuntimeException("offline"))

    @Test
    fun `keeps an expense exactly as it was typed`() = runTest(dispatcher) {
        outbox.enqueue(
            "g1",
            input().copy(
                notes = "Split with Ben",
                categoryId = 4,
                isReimbursement = true,
                recurrenceRule = RecurrenceRule.WEEKLY,
                splitMode = SplitMode.BY_SHARES,
                paidFor = listOf(PaidFor("p1", 200), PaidFor("p2", 100)),
            ),
        )
        advanceUntilIdle()

        val queued = outbox.pending("g1").first().single().input
        assertEquals("Split with Ben", queued.notes)
        assertEquals(4, queued.categoryId)
        assertEquals(true, queued.isReimbursement)
        assertEquals(RecurrenceRule.WEEKLY, queued.recurrenceRule)
        // Shares are not amounts and their meaning depends on the split mode.
        assertEquals(SplitMode.BY_SHARES, queued.splitMode)
        assertEquals(listOf(200L, 100L), queued.paidFor.map { it.shares })
    }

    @Test
    fun `sends what is waiting and empties the queue`() = runTest(dispatcher) {
        outbox.enqueue("g1", input())
        advanceUntilIdle()

        assertEquals(1, outbox.flush())
        advanceUntilIdle()

        assertEquals(1, api.callsTo("createExpense").size)
        assertTrue(outbox.pending("g1").first().isEmpty())
    }

    @Test
    fun `sends them in the order they were typed`() = runTest(dispatcher) {
        outbox.enqueue("g1", input(title = "First"))
        advanceUntilIdle()
        outbox.enqueue("g1", input(title = "Second"))
        advanceUntilIdle()

        outbox.flush()
        advanceUntilIdle()

        assertEquals(
            listOf("First", "Second"),
            api.callsTo("createExpense").map { (it.arguments[1] as ExpenseInput).title },
        )
    }

    @Test
    fun `stops at the first unreachable send`() = runTest(dispatcher) {
        outbox.enqueue("g1", input(title = "First"))
        advanceUntilIdle()
        outbox.enqueue("g1", input(title = "Second"))
        advanceUntilIdle()
        api.createExpenseResult = SpliitResult.Failure(offline())

        assertEquals(0, outbox.flush())
        advanceUntilIdle()

        // The second would fail the same way, and trying it would only put an
        // attempt on an expense that never left.
        assertEquals(1, api.callsTo("createExpense").size)
        assertEquals(2, outbox.pending("g1").first().size)
    }

    @Test
    fun `an expense the server refuses does not block the ones behind it`() =
        runTest(dispatcher) {
            outbox.enqueue("g1", input(title = "Refused"))
            advanceUntilIdle()
            outbox.enqueue("g1", input(title = "Fine"))
            advanceUntilIdle()

            api.createExpenseResult = SpliitResult.Failure(
                SpliitError.Procedure("BAD_REQUEST", "no"),
            )
            outbox.flush()
            advanceUntilIdle()

            // Both were tried: one expense the server will not take says
            // nothing about the next.
            assertEquals(2, api.callsTo("createExpense").size)
            // And the rejected one is kept, for the user to fix or throw away.
            assertEquals(2, outbox.pending("g1").first().size)
        }

    @Test
    fun `records the attempt so a retry knows it has been out`() = runTest(dispatcher) {
        outbox.enqueue("g1", input())
        advanceUntilIdle()
        assertEquals(0, outbox.pending("g1").first().single().attempts)

        api.createExpenseResult = SpliitResult.Failure(offline())
        outbox.flush()
        advanceUntilIdle()

        // Zero attempts is what makes a first send unambiguously safe; above
        // zero, a send may have landed and lost its answer.
        assertEquals(1, outbox.pending("g1").first().single().attempts)
    }

    @Test
    fun `does not send an expense twice when the first answer was lost`() =
        runTest(dispatcher) {
            outbox.enqueue("g1", input())
            advanceUntilIdle()

            // The send reached the server and the response did not come back.
            api.createExpenseResult = SpliitResult.Failure(offline())
            outbox.flush()
            advanceUntilIdle()

            // It is on the server after all, created since it was queued.
            api.createExpenseResult = SpliitResult.Success("e1")
            api.listExpensesResult = SpliitResult.Success(
                ExpensePage(listOf(serverExpense("Taxi", 2200)), hasMore = false),
            )

            assertEquals(1, outbox.flush())
            advanceUntilIdle()

            // Sent once, not twice.
            assertEquals(1, api.callsTo("createExpense").size)
            assertTrue(outbox.pending("g1").first().isEmpty())
        }

    @Test
    fun `an expense on the server from before does not count as sent`() =
        runTest(dispatcher) {
            api.listExpensesResult = SpliitResult.Success(
                ExpensePage(
                    // Same everything, but created long before this was typed:
                    // last week's taxi, not this one.
                    listOf(serverExpense("Taxi", 2200, createdAt = "2026-09-01T09:00:00Z")),
                    hasMore = false,
                ),
            )
            outbox.enqueue("g1", input())
            advanceUntilIdle()
            api.createExpenseResult = SpliitResult.Failure(offline())
            outbox.flush()
            advanceUntilIdle()

            api.createExpenseResult = SpliitResult.Success("e1")
            outbox.flush()
            advanceUntilIdle()

            // It was sent the second time: an identical expense from before
            // this one was queued is a different expense, and dropping it
            // would silently lose the one the user typed.
            assertEquals(2, api.callsTo("createExpense").size)
            assertTrue(outbox.pending("g1").first().isEmpty())
        }

    @Test
    fun `a first send is never second-guessed`() = runTest(dispatcher) {
        // Something identical is already on the server, and this has never
        // been sent, so it is a second one, not the same one.
        api.listExpensesResult = SpliitResult.Success(
            ExpensePage(listOf(serverExpense("Taxi", 2200)), hasMore = false),
        )
        outbox.enqueue("g1", input())
        advanceUntilIdle()

        outbox.flush()
        advanceUntilIdle()

        assertEquals(1, api.callsTo("createExpense").size)
        // And nothing was looked up first: with no attempt behind it there is
        // nothing ambiguous to check.
        assertEquals("createExpense", api.calls.first().procedure)
    }

    @Test
    fun `attributes a queued expense to whoever the user says they are`() =
        runTest(dispatcher) {
            preferences.setActiveParticipantId("g1", "p2")
            outbox.enqueue("g1", input())
            advanceUntilIdle()

            outbox.flush()
            advanceUntilIdle()

            assertEquals("p2", api.callsTo("createExpense").single().arguments[2])
        }

    @Test
    fun `throwing one away sends nothing`() = runTest(dispatcher) {
        val id = outbox.enqueue("g1", input())
        advanceUntilIdle()

        outbox.discard(id)
        advanceUntilIdle()

        assertEquals(0, outbox.flush())
        assertTrue(api.callsTo("createExpense").isEmpty())
        assertNull(outbox.find(id))
    }

    @Test
    fun `forgetting a group throws away what was queued for it`() = runTest(dispatcher) {
        outbox.enqueue("g1", input())
        outbox.enqueue("g2", input())
        advanceUntilIdle()

        outbox.discardAllFor("g1")
        advanceUntilIdle()

        // Without the group id there is no way back into it, so an expense
        // waiting for it could never be sent.
        assertTrue(outbox.pending("g1").first().isEmpty())
        assertEquals(1, outbox.pending("g2").first().size)
    }

    @Test
    fun `an empty queue does nothing at all`() = runTest(dispatcher) {
        assertEquals(0, outbox.flush())

        assertTrue(api.calls.isEmpty())
    }

    private fun serverExpense(
        title: String,
        amount: Long,
        createdAt: String = Instant.now().plusSeconds(5).toString(),
    ) = ExpenseSummary(
        id = "e1",
        title = title,
        amount = amount,
        originalAmount = null,
        originalCurrency = null,
        expenseDate = LocalDate.parse("2026-09-11"),
        createdAt = Instant.parse(createdAt),
        category = Category(0, "Uncategorized", "General"),
        paidBy = Participant("p1", "Ada"),
        paidFor = listOf(PaidForWithParticipant(Participant("p1", "Ada"), 1)),
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = false,
        documentCount = 0,
    )
}
