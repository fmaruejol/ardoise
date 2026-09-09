package io.github.fmaruejol.ardoise.ui.expense

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.FakeConnectivity
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.group
import io.github.fmaruejol.ardoise.data.testCache
import io.github.fmaruejol.ardoise.data.testOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val groups = GroupRepository(api, preferences, cache, outbox)
    private val expenses = ExpenseRepository(api, preferences, cache, outbox)

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")
    private val cleo = Participant("p3", "Cleo")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api.groupResult = SpliitResult.Success(group("g1", listOf(ana, ben, cleo)))
        api.getExpenseResult = SpliitResult.Success(dinner())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val connectivity = FakeConnectivity()

    private fun viewModel() = ExpenseDetailViewModel("g1", "e1", groups, expenses, connectivity)

    @Test
    fun `reads the expense as it stands`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Dinner", state.title)
        assertEquals(9000L, state.amount)
        assertEquals("Ana", state.payerName)
        assertEquals(LocalDate.parse("2026-09-11"), state.date)
    }

    @Test
    fun `apportions the expense the way the balances do`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // 90.00 evenly between three. A detail screen that divided it its own
        // way would quietly contradict every other number in the app.
        assertEquals(
            listOf(3000L, 3000L, 3000L),
            viewModel.state.value.shares.map { it.amount },
        )
        assertEquals(9000L, viewModel.state.value.shares.sumOf { it.amount })
    }

    @Test
    fun `leaves out anyone the expense is not for`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(
            dinner().copy(paidFor = listOf(PaidFor("p1", 1), PaidFor("p2", 1))),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Ana", "Ben"), viewModel.state.value.shares.map { it.name })
    }

    @Test
    fun `lists them in the group's order, not the database's`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(
            dinner().copy(
                paidFor = listOf(PaidFor("p3", 1), PaidFor("p1", 1), PaidFor("p2", 1)),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        // paidFor comes back without an ORDER BY, so the rows would otherwise
        // shuffle between loads.
        assertEquals(listOf("Ana", "Ben", "Cleo"), viewModel.state.value.shares.map { it.name })
    }

    @Test
    fun `marks which one is you`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(false, true, false), viewModel.state.value.shares.map { it.isYou })
        // Ana paid, so it was not you.
        assertFalse(viewModel.state.value.isPaidByYou)
    }

    @Test
    fun `says when you were the one who paid`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p1")
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isPaidByYou)
    }

    @Test
    fun `asks before deleting`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteClick()

        // It goes for everyone in the group and moves every balance with it.
        assertTrue(viewModel.state.value.confirmingDelete)
        assertTrue(api.callsTo("deleteExpense").isEmpty())
    }

    @Test
    fun `deletes once confirmed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteClick()
        viewModel.onDeleteConfirm()
        advanceUntilIdle()

        assertEquals(listOf("g1", "e1", null), api.callsTo("deleteExpense").single().arguments)
        assertTrue(viewModel.state.value.isDeleted)
    }

    @Test
    fun `keeps the expense when the delete is cancelled`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteClick()
        viewModel.onDeleteDismiss()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.confirmingDelete)
        assertTrue(api.callsTo("deleteExpense").isEmpty())
    }

    @Test
    fun `reports a delete that failed and stays put`() = runTest(dispatcher) {
        api.deleteExpenseResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteClick()
        viewModel.onDeleteConfirm()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isDeleted)
        assertTrue(viewModel.state.value.deleteError is SpliitError.Network)
    }

    @Test
    fun `says so when the expense is already gone`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Failure(
            SpliitError.NotFound,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notFound)
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun `keeps a cached expense on screen when the refresh fails`() = runTest(dispatcher) {
        // Read it once so the cache has it in full.
        viewModel()
        advanceUntilIdle()

        api.getExpenseResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("off")))
        val offline = viewModel()
        advanceUntilIdle()

        val state = offline.state.value
        // A read is the cache and then a refresh, so both arrive.
        assertTrue(state.isLoaded)
        assertEquals("Dinner", state.title)
        assertEquals(9000L, state.amount)
        assertNotNull(state.loadError)
        assertFalse(state.notFound)
    }

    @Test
    fun `raises the banner when the platform says there is no network`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isOffline)

        connectivity.setOffline(true)
        advanceUntilIdle()

        // Nothing failed, no read has been made since.
        assertTrue(viewModel.state.value.isOffline)
        assertTrue(viewModel.state.value.isLoaded)
    }

    private fun payment(
        payer: String = "p1",
        payee: String = "p2",
        paidFor: List<PaidFor> = listOf(PaidFor(payee, 1)),
    ) = dinner().copy(
        title = "Reimbursement",
        amount = 4920,
        paidById = payer,
        paidFor = paidFor,
        isReimbursement = true,
    )

    private fun dinner() = Expense(
        id = "e1",
        groupId = "g1",
        title = "Dinner",
        amount = 9000,
        originalAmount = null,
        originalCurrency = null,
        conversionRate = null,
        expenseDate = LocalDate.parse("2026-09-11"),
        createdAt = Instant.parse("2026-09-11T18:00:00Z"),
        category = null,
        paidById = "p1",
        paidFor = listOf(PaidFor("p1", 1), PaidFor("p2", 1), PaidFor("p3", 1)),
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = false,
        notes = null,
        documents = emptyList(),
    )

    // --- an expense that repeats -------------------------------------------

    @Test
    fun `carries the rule and predicts the next copy`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(
            dinner().copy(
                expenseDate = LocalDate.now().minusDays(2),
                recurrenceRule = RecurrenceRule.WEEKLY,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(RecurrenceRule.WEEKLY, viewModel.state.value.recurrenceRule)
        // The same arithmetic the server uses, which is the only reason a date
        // may be shown at all.
        assertEquals(LocalDate.now().plusDays(5), viewModel.state.value.nextCopy)
    }

    @Test
    fun `does not name a copy the server has already made`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(
            dinner().copy(
                expenseDate = LocalDate.now().minusMonths(3),
                recurrenceRule = RecurrenceRule.WEEKLY,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        // A date in the past is not the next copy, and where the series has
        // actually got to is on a chain the server keeps and does not return.
        assertEquals(RecurrenceRule.WEEKLY, viewModel.state.value.recurrenceRule)
        assertNull(viewModel.state.value.nextCopy)
    }

    @Test
    fun `an expense that does not repeat has no next copy`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(RecurrenceRule.NONE, viewModel.state.value.recurrenceRule)
        assertNull(viewModel.state.value.nextCopy)
    }

    // --- a reimbursement ---------------------------------------------------

    @Test
    fun `knows a payment from an expense`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(payment())
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isReimbursement)
    }

    @Test
    fun `names both ends of the payment`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(payment(payer = "p1", payee = "p2"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val transfer = viewModel.state.value.transfer!!
        assertEquals("Ana", transfer.payerName)
        assertEquals("Ben", transfer.payeeName)
        // Their positions in the group, so the avatars are the same colours
        // here as everywhere else.
        assertEquals(0, transfer.payerIndex)
        assertEquals(1, transfer.payeeIndex)
    }

    @Test
    fun `says which end is you`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        api.getExpenseResult = SpliitResult.Success(payment(payer = "p1", payee = "p2"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val transfer = viewModel.state.value.transfer!!
        assertFalse(transfer.payerIsYou)
        assertTrue(transfer.payeeIsYou)
    }

    @Test
    fun `an ordinary expense is not a transfer`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value.transfer)
        assertFalse(viewModel.state.value.isReimbursement)
    }

    @Test
    fun `a reimbursement across several people falls back to the split card`() =
        runTest(dispatcher) {
            api.getExpenseResult = SpliitResult.Success(
                payment(paidFor = listOf(PaidFor("p2", 1), PaidFor("p3", 1))),
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            // Still a reimbursement, so it stays out of "total spent", but two
            // recipients is not a transfer anyone can draw as two sides.
            assertTrue(viewModel.state.value.isReimbursement)
            assertNull(viewModel.state.value.transfer)
            assertEquals(2, viewModel.state.value.shares.size)
        }

    @Test
    fun `paying yourself is not a transfer`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(payment(payer = "p1", payee = "p1"))
        val viewModel = viewModel()
        advanceUntilIdle()

        // It would read "Ana pays Ana", with a minus and a plus against the
        // same name.
        assertNull(viewModel.state.value.transfer)
    }
}
