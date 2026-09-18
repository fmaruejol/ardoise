package io.github.fmaruejol.ardoise.ui.expense

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.CategoryRepository
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.FakeExchangeRates
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.group
import io.github.fmaruejol.ardoise.data.testCache
import io.github.fmaruejol.ardoise.data.testOutbox
import io.github.fmaruejol.ardoise.ui.throughProcessDeath
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
import java.util.Locale

/** The form's job is to turn what someone typed into the exact integers the server expects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseFormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val groups = GroupRepository(api, preferences, cache, outbox)
    private val expenses = ExpenseRepository(api, preferences, cache, outbox)
    private val categories = CategoryRepository(cache)

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")
    private val cleo = Participant("p3", "Cleo")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api.groupResult = SpliitResult.Success(group("g1", listOf(ana, ben, cleo)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        expenseId: String? = null,
        prefill: ReimbursementPrefill? = null,
        pendingId: String? = null,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = ExpenseFormViewModel(
        groupId = "g1",
        expenseId = expenseId,
        pendingId = pendingId,
        prefill = prefill,
        groups = groups,
        expenses = expenses,
        categories = categories,
        rates = rates,
        savedState = savedState,
    )

    private val rates = FakeExchangeRates()

    private fun prefill(
        payer: String = "p1",
        payee: String = "p2",
        amount: Long = 6510,
    ) = ReimbursementPrefill(
        payerId = payer,
        payeeId = payee,
        amount = amount,
        title = "Reimbursement",
    )

    private fun sentInput(): ExpenseInput =
        api.callsTo("createExpense").single().arguments[1] as ExpenseInput

    // --- a new expense -----------------------------------------------------

    @Test
    fun `starts with everyone in, split evenly, paid by you`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        // "Paid by" starting on this device's own participant is the reason the
        // app asks who you are.
        assertEquals("p2", state.paidById)
        assertEquals(SplitMode.EVENLY, state.splitMode)
        assertEquals(setOf("p1", "p2", "p3"), state.paidFor.keys)
        // Distinct from the payer: the split editor marks "(you)" with this,
        // and the payer is only its starting value.
        assertEquals("p2", state.activeParticipantId)
    }

    @Test
    fun `changing who paid does not change who you are`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPayerChange("p1")

        assertEquals("p1", viewModel.state.value.paidById)
        assertEquals("p2", viewModel.state.value.activeParticipantId)
    }

    @Test
    fun `sends the amount in minor units`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("12.34")
        viewModel.onSave()
        advanceUntilIdle()

        // The group is in euros, so 12.34 is 1234 minor units. In a yen group
        // the same text would be 1234 yen, the ISO code is what decides.
        assertEquals(1234L, sentInput().amount)
    }

    @Test
    fun `an evenly split sends everyone in, and only them`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")

        viewModel.onSplitEditorOpen()
        viewModel.onParticipantToggle("p3")
        viewModel.onSave()
        advanceUntilIdle()

        val input = sentInput()
        assertEquals(SplitMode.EVENLY, input.splitMode)
        assertEquals(setOf("p1", "p2"), input.paidFor.map { it.participantId }.toSet())
    }

    @Test
    fun `refuses an expense nobody is paying for`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")
        viewModel.onSplitEditorOpen()

        listOf("p1", "p2", "p3").forEach(viewModel::onParticipantToggle)
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(ExpenseFormError.NobodyPaidFor in viewModel.state.value.errors)
        assertTrue(api.callsTo("createExpense").isEmpty())
    }

    // --- the split modes, and the scale each one stores ---------------------

    @Test
    fun `by amount sends minor units that add up to the expense`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Airbnb")
        viewModel.onAmountChange("30")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)
        viewModel.onShareChange("p1", "15")
        viewModel.onShareChange("p2", "10")
        viewModel.onShareChange("p3", "5")
        viewModel.onSave()
        advanceUntilIdle()

        val input = sentInput()
        assertEquals(SplitMode.BY_AMOUNT, input.splitMode)
        assertEquals(listOf(1500L, 1000L, 500L), input.paidFor.sortedByDescending { it.shares }.map { it.shares })
        assertEquals(input.amount, input.paidFor.sumOf { it.shares })
    }

    @Test
    fun `by shares stores a share count scaled by a hundred`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Taxi")
        viewModel.onAmountChange("30")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_SHARES)
        viewModel.onShareChange("p1", "2")
        viewModel.onShareChange("p2", "1")
        viewModel.onShareChange("p3", "1")
        viewModel.onSave()
        advanceUntilIdle()

        // 2 shares is stored as 200.
        val input = sentInput()
        assertEquals(200L, input.paidFor.first { it.participantId == "p1" }.shares)
        assertEquals(100L, input.paidFor.first { it.participantId == "p2" }.shares)
    }

    @Test
    fun `by percentage stores percentages scaled by a hundred`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Hotel")
        viewModel.onAmountChange("100")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_PERCENTAGE)
        viewModel.onShareChange("p1", "50")
        viewModel.onShareChange("p2", "25")
        viewModel.onShareChange("p3", "25")
        viewModel.onSave()
        advanceUntilIdle()

        val input = sentInput()
        assertEquals(5000L, input.paidFor.first { it.participantId == "p1" }.shares)
        assertEquals(10_000L, input.paidFor.sumOf { it.shares })
    }

    @Test
    fun `accepts a fractional percentage`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Hotel")
        viewModel.onAmountChange("100")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_PERCENTAGE)
        viewModel.onShareChange("p1", "33.34")
        viewModel.onShareChange("p2", "33.33")
        viewModel.onShareChange("p3", "33.33")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(3334L, sentInput().paidFor.first { it.participantId == "p1" }.shares)
    }

    // --- the rows nobody typed in -------------------------------------------

    @Test
    fun `a typed percentage leaves the rest of the rows adding up to a hundred`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAmountChange("100")

            viewModel.onSplitEditorOpen()
            viewModel.onSplitModeChange(SplitMode.BY_PERCENTAGE)
            viewModel.onShareChange("p1", "50")

            // The point of the whole thing: nobody works out the last
            // percentage by hand.
            val state = viewModel.state.value
            assertEquals(5000L, state.paidFor["p1"])
            assertEquals(2500L, state.paidFor["p2"])
            assertEquals(2500L, state.paidFor["p3"])
            assertEquals(SplitMode.PERCENT_TOTAL, state.paidFor.values.sum())
            assertEquals("25", state.splitText["p3"])
        }

    @Test
    fun `a typed amount leaves the rest of the rows adding up to the expense`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAmountChange("30")

            viewModel.onSplitEditorOpen()
            viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)
            viewModel.onShareChange("p1", "10")
            viewModel.onShareChange("p2", "13")

            val state = viewModel.state.value
            assertEquals(700L, state.paidFor["p3"])
            assertEquals(3000L, state.paidFor.values.sum())
        }

    @Test
    fun `a row typed in is never moved again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("100")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_PERCENTAGE)
        viewModel.onShareChange("p1", "20")
        viewModel.onShareChange("p2", "20")
        viewModel.onShareChange("p3", "20")

        // Every number is now somebody's, so sixty per cent stands and the
        // form refuses it rather than overruling one of the three.
        assertEquals(6000L, viewModel.state.value.paidFor.values.sum())
    }

    @Test
    fun `a row the total no longer reaches drops out, and comes back`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("100")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_PERCENTAGE)
        viewModel.onShareChange("p1", "100")

        // Nothing left to give them, which is what typing 0 already means.
        assertEquals(setOf("p1"), viewModel.state.value.paidFor.keys)

        viewModel.onShareChange("p1", "60")

        // Still nobody's numbers, so they come back.
        assertEquals(2000L, viewModel.state.value.paidFor["p2"])
        assertEquals(2000L, viewModel.state.value.paidFor["p3"])
    }

    @Test
    fun `an expense read back keeps its own percentages`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(
            existing().copy(
                amount = 10_000,
                splitMode = SplitMode.BY_PERCENTAGE,
                paidFor = listOf(PaidFor("p1", 5000), PaidFor("p2", 3000), PaidFor("p3", 2000)),
            ),
        )
        val viewModel = viewModel(expenseId = "e1")
        advanceUntilIdle()

        viewModel.onSplitEditorOpen()
        viewModel.onShareChange("p1", "60")

        // Somebody wrote 30 and 20 on purpose; the editor does not rewrite
        // them to make its own sum work.
        val state = viewModel.state.value
        assertEquals(3000L, state.paidFor["p2"])
        assertEquals(2000L, state.paidFor["p3"])
    }

    @Test
    fun `says so when the amounts do not add up`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Airbnb")
        viewModel.onAmountChange("30")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)
        viewModel.onShareChange("p1", "10")
        viewModel.onShareChange("p2", "10")
        viewModel.onShareChange("p3", "5")
        viewModel.onSave()
        advanceUntilIdle()

        // The server rejects this too; saying it here means pointing at the
        // numbers rather than relaying a BAD_REQUEST.
        assertTrue(ExpenseFormError.SplitTotal(2500, 3000) in viewModel.state.value.errors)
        assertTrue(viewModel.state.value.editingSplit)
        assertTrue(api.callsTo("createExpense").isEmpty())
    }

    @Test
    fun `says so when the percentages do not add up`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Hotel")
        viewModel.onAmountChange("100")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_PERCENTAGE)
        viewModel.onShareChange("p1", "50")
        viewModel.onShareChange("p2", "20")
        viewModel.onShareChange("p3", "20")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(ExpenseFormError.SplitTotal(9000, 10_000) in viewModel.state.value.errors)
    }

    @Test
    fun `seeds a percentage split that already adds to a hundred`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("100")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_PERCENTAGE)

        // Three people at 33.33% each would be 9999, and the server would
        // refuse it. The leftover has to land somewhere.
        assertEquals(10_000L, viewModel.state.value.paidFor.values.sum())
    }

    @Test
    fun `seeds an amount split that adds to exactly the amount`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("10")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)

        // 1000 over three is 333 each and one minor unit left over, which has
        // to land on somebody or the form refuses its own seeded split.
        assertEquals(1000L, viewModel.state.value.paidFor.values.sum())
    }

    @Test
    fun `seeds a negative amount split that adds to exactly the amount`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        // Income, which `:core` apportions with floorDiv precisely so the
        // leftover stays on the same side of zero as the amount.
        viewModel.onAmountChange("-50")

        viewModel.onSplitEditorOpen()
        viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)

        // Dividing by hand truncated towards zero and left this at -4998, so
        // the form marked a SplitTotal error on a split it had just written
        // itself.
        assertEquals(-5000L, viewModel.state.value.paidFor.values.sum())
    }

    @Test
    fun `carries the people over when the mode changes, not the numbers`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAmountChange("30")
        viewModel.onSplitEditorOpen()
        viewModel.onParticipantToggle("p3")

        viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)

        // A share of 2 means nothing as an amount, so only membership survives.
        assertEquals(setOf("p1", "p2"), viewModel.state.value.paidFor.keys)
        assertEquals(3000L, viewModel.state.value.paidFor.values.sum())
    }

    // --- recurring ---------------------------------------------------------

    @Test
    fun `does not repeat unless asked`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(RecurrenceRule.NONE, sentInput().recurrenceRule)
    }

    @Test
    fun `sends the recurrence rule`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Rent")
        viewModel.onAmountChange("900")

        viewModel.onRecurrenceOpen()
        viewModel.onRecurrenceChange(RecurrenceRule.MONTHLY)
        viewModel.onRecurrenceClose()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(RecurrenceRule.MONTHLY, sentInput().recurrenceRule)
    }

    @Test
    fun `keeps an existing expense repeating`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(existing().copy(recurrenceRule = RecurrenceRule.WEEKLY))
        val viewModel = viewModel("e1")
        advanceUntilIdle()

        assertEquals(RecurrenceRule.WEEKLY, viewModel.state.value.recurrenceRule)

        viewModel.onSave()
        advanceUntilIdle()

        // Saving an edit must not quietly cancel the series.
        val input = api.callsTo("updateExpense").single().arguments[2] as ExpenseInput
        assertEquals(RecurrenceRule.WEEKLY, input.recurrenceRule)
    }

    @Test
    fun `stops an expense repeating`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(existing().copy(recurrenceRule = RecurrenceRule.MONTHLY))
        val viewModel = viewModel("e1")
        advanceUntilIdle()

        viewModel.onRecurrenceChange(RecurrenceRule.NONE)
        viewModel.onSave()
        advanceUntilIdle()

        val input = api.callsTo("updateExpense").single().arguments[2] as ExpenseInput
        assertEquals(RecurrenceRule.NONE, input.recurrenceRule)
    }

    // --- reimbursements ----------------------------------------------------

    @Test
    fun `an expense is not a reimbursement unless it says so`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(false, sentInput().isReimbursement)
    }

    @Test
    fun `marks a payment between two people as a reimbursement`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Ben pays Ana back")
        viewModel.onAmountChange("30")

        viewModel.onReimbursementChange(true)
        viewModel.onSave()
        advanceUntilIdle()

        // It still moves balances, which is how settling up works, but the
        // feed keeps it out of "total spent".
        assertEquals(true, sentInput().isReimbursement)
    }

    @Test
    fun `keeps an existing expense a reimbursement`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(existing().copy(isReimbursement = true))
        val viewModel = viewModel("e1")
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.isReimbursement)

        viewModel.onSave()
        advanceUntilIdle()

        val input = api.callsTo("updateExpense").single().arguments[2] as ExpenseInput
        assertEquals(true, input.isReimbursement)
    }

    // --- editing -----------------------------------------------------------

    @Test
    fun `loads an existing expense as it stands`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(existing())
        val viewModel = viewModel("e1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Dinner", state.title)
        assertEquals("96.00", state.amountText)
        assertEquals(SplitMode.BY_SHARES, state.splitMode)
        assertEquals(mapOf("p1" to 200L, "p2" to 100L), state.paidFor)
    }

    @Test
    fun `updates rather than creating a second expense`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(existing())
        val viewModel = viewModel("e1")
        advanceUntilIdle()

        viewModel.onTitleChange("Dinner at Cervejaria")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(api.callsTo("createExpense").isEmpty())
        val call = api.callsTo("updateExpense").single()
        assertEquals("e1", call.arguments[1])
        assertEquals("Dinner at Cervejaria", (call.arguments[2] as ExpenseInput).title)
    }

    @Test
    fun `deletes an expense`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(existing())
        val viewModel = viewModel("e1")
        advanceUntilIdle()

        viewModel.onDelete()
        advanceUntilIdle()

        assertEquals(listOf("g1", "e1", null), api.callsTo("deleteExpense").single().arguments)
        assertTrue(viewModel.state.value.isDeleted)
    }

    // --- the things that must not happen twice -----------------------------

    @Test
    fun `ignores a second save while the first is still running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")

        viewModel.onSave()
        viewModel.onSave()
        advanceUntilIdle()

        // Mutations are not idempotent: a second create is a second expense,
        // and a duplicated expense is a wrong balance for everyone.
        assertEquals(1, api.callsTo("createExpense").size)
    }

    @Test
    fun `a save with no network is kept rather than lost`() = runTest(dispatcher) {
        api.createExpenseResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")

        viewModel.onSave()
        advanceUntilIdle()

        // Nothing reached the server, so nothing has happened yet and the
        // expense is only waiting.
        assertTrue(viewModel.state.value.errors.isEmpty())
        assertNotNull(viewModel.state.value.savedId)
        assertTrue(viewModel.state.value.wasQueued)
    }

    @Test
    fun `a save the server refuses is reported, not queued`() = runTest(dispatcher) {
        api.createExpenseResult = SpliitResult.Failure(
            SpliitError.Procedure("BAD_REQUEST", "no"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")

        viewModel.onSave()
        advanceUntilIdle()

        // Queueing this would be promising to send something the server will
        // never accept, however many times it is tried.
        assertTrue(viewModel.state.value.errors.any { it is ExpenseFormError.Failed })
        assertEquals("Dinner", viewModel.state.value.title)
        assertNull(viewModel.state.value.savedId)
    }

    // --- an expense still waiting to be sent --------------------------------

    @Test
    fun `opens a queued expense as it was typed`() = runTest(dispatcher) {
        val pendingId = outbox.enqueue("g1", queuedInput())
        advanceUntilIdle()

        val viewModel = viewModel(pendingId = pendingId)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Taxi to airport", state.title)
        assertEquals("22.00", state.amountText)
        assertEquals("p2", state.paidById)
        // Not a new expense: it exists, it just has not been sent.
        assertEquals(false, state.isNew)
    }

    @Test
    fun `editing a queued expense replaces it rather than adding a second`() =
        runTest(dispatcher) {
            val pendingId = outbox.enqueue("g1", queuedInput())
            advanceUntilIdle()
            val viewModel = viewModel(pendingId = pendingId)
            advanceUntilIdle()

            viewModel.onAmountChange("25.00")
            viewModel.onSave()
            advanceUntilIdle()

            // Online now, so the edit goes; either way there is one of it.
            assertEquals(2500L, sentInput().amount)
            assertNull(outbox.find(pendingId))
        }

    @Test
    fun `an edit with still no network goes back in the queue`() = runTest(dispatcher) {
        val pendingId = outbox.enqueue("g1", queuedInput())
        advanceUntilIdle()
        api.createExpenseResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel(pendingId = pendingId)
        advanceUntilIdle()

        viewModel.onAmountChange("25.00")
        viewModel.onSave()
        advanceUntilIdle()

        // The old entry is gone and the edited one took its place: one queue
        // entry, with the new amount.
        assertNull(outbox.find(pendingId))
        val queued = outbox.pending("g1").first().single()
        assertEquals(2500L, queued.input.amount)
    }

    @Test
    fun `throwing away a queued expense sends nothing`() = runTest(dispatcher) {
        val pendingId = outbox.enqueue("g1", queuedInput())
        advanceUntilIdle()
        val viewModel = viewModel(pendingId = pendingId)
        advanceUntilIdle()

        viewModel.onDelete()
        advanceUntilIdle()

        // It is not on the server, so there is nothing to delete there.
        assertNull(outbox.find(pendingId))
        assertTrue(api.callsTo("deleteExpense").isEmpty())
        assertTrue(viewModel.state.value.isDeleted)
    }

    private fun queuedInput() = ExpenseInput(
        title = "Taxi to airport",
        amount = 2200,
        expenseDate = LocalDate.parse("2026-09-11"),
        paidById = "p2",
        paidFor = listOf(PaidFor("p1", 1), PaidFor("p2", 1)),
        splitMode = SplitMode.EVENLY,
    )

    private fun existing() = Expense(
        id = "e1",
        groupId = "g1",
        title = "Dinner",
        amount = 9600,
        originalAmount = null,
        originalCurrency = null,
        conversionRate = null,
        expenseDate = LocalDate.parse("2026-09-11"),
        createdAt = Instant.parse("2026-09-11T18:00:00Z"),
        category = null,
        paidById = "p1",
        paidFor = listOf(PaidFor("p1", 200), PaidFor("p2", 100)),
        splitMode = SplitMode.BY_SHARES,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = false,
        notes = null,
        documents = emptyList(),
    )

    // --- opened from "Mark as paid" ----------------------------------------

    @Test
    fun `fills the form in rather than saving behind the user`() = runTest(dispatcher) {
        val viewModel = viewModel(prefill = prefill())
        advanceUntilIdle()

        // Settling up writes nothing on its own: it opens this form, and the
        // save is the user's. `groups.expenses.create` cannot be taken back.
        assertTrue(api.callsTo("createExpense").isEmpty())
        assertEquals("Reimbursement", viewModel.state.value.title)
        assertTrue(viewModel.state.value.isReimbursement)
    }

    @Test
    fun `puts the amount in major units, for the user to read and change`() =
        runTest(dispatcher) {
            val viewModel = viewModel(prefill = prefill(amount = 6510))
            advanceUntilIdle()

            assertEquals("65.10", viewModel.state.value.amountText)
            // And it still parses back to exactly what the balances said.
            assertEquals(6510L, viewModel.state.value.amount)
        }

    @Test
    fun `puts the payment between the two people and nobody else`() = runTest(dispatcher) {
        val viewModel = viewModel(prefill = prefill(payer = "p1", payee = "p2"))
        advanceUntilIdle()

        assertEquals("p1", viewModel.state.value.paidById)
        // Split across the group it would move everyone else's balance too.
        assertEquals(setOf("p2"), viewModel.state.value.paidFor.keys)
        assertEquals(SplitMode.EVENLY, viewModel.state.value.splitMode)
    }

    @Test
    fun `saves what the prefill described`() = runTest(dispatcher) {
        val viewModel = viewModel(prefill = prefill())
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        val input = sentInput()
        assertEquals(6510L, input.amount)
        assertEquals("p1", input.paidById)
        assertEquals(listOf("p2"), input.paidFor.map { it.participantId })
        assertTrue(input.isReimbursement)
    }

    @Test
    fun `edits made to a prefilled payment are what gets saved`() = runTest(dispatcher) {
        val viewModel = viewModel(prefill = prefill())
        advanceUntilIdle()

        // The whole point of opening the form: the payment that actually
        // happened was a round 60.00.
        viewModel.onAmountChange("60.00")
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(6000L, sentInput().amount)
    }

    @Test
    fun `a prefill naming somebody who has left falls back to a blank form`() =
        runTest(dispatcher) {
            val viewModel = viewModel(prefill = prefill(payee = "gone"))
            advanceUntilIdle()

            // Saving against an id the group does not have would only come
            // back as a BAD_REQUEST.
            assertEquals("", viewModel.state.value.title)
            assertEquals(false, viewModel.state.value.isReimbursement)
            assertEquals(
                setOf("p1", "p2", "p3"),
                viewModel.state.value.paidFor.keys,
            )
        }

    @Test
    fun `a prefilled payment does not open the keyboard on the amount`() =
        runTest(dispatcher) {
            val viewModel = viewModel(prefill = prefill())
            advanceUntilIdle()

            // The number is already there and the form is here to be checked,
            // not filled in. `AmountField` reads this to skip the focus.
            assertTrue(viewModel.state.value.isPrefilled)
        }

    @Test
    fun `a blank new expense still takes the focus on the amount`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isPrefilled)
    }

    @Test
    fun `a prefill that fell back is not treated as prefilled`() = runTest(dispatcher) {
        val viewModel = viewModel(prefill = prefill(payee = "gone"))
        advanceUntilIdle()

        // The form is blank, so it is the blank form's behaviour that applies.
        assertEquals(false, viewModel.state.value.isPrefilled)
    }

    // --- everything wrong at once ------------------------------------------

    @Test
    fun `marks every empty field, not the first one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        // Stopping at the title would send the user round again for the
        // amount, which is the sort of thing a form should only do once.
        assertEquals(
            setOf(ExpenseFormError.Title, ExpenseFormError.Amount),
            viewModel.state.value.errors,
        )
        assertTrue(api.callsTo("createExpense").isEmpty())
    }

    @Test
    fun `fixing one field leaves the other marked`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onTitleChange("Dinner")

        assertEquals(setOf(ExpenseFormError.Amount), viewModel.state.value.errors)
    }

    @Test
    fun `does not complain about the split when there is no amount to split`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onTitleChange("Dinner")
            viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)

            viewModel.onSave()
            advanceUntilIdle()

            // Every share fails to add up to an amount that is not there, and
            // saying so twice explains nothing.
            assertEquals(setOf(ExpenseFormError.Amount), viewModel.state.value.errors)
        }

    @Test
    fun `opens the split editor only when the split is the whole problem`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onAmountChange("30")
            viewModel.onSplitModeChange(SplitMode.BY_AMOUNT)
            // Every row typed in, so none is left for the editor to correct.
            viewModel.onShareChange("p1", "5")
            viewModel.onShareChange("p2", "5")
            viewModel.onShareChange("p3", "5")

            viewModel.onSave()
            advanceUntilIdle()

            // No title either, so the editor would hide half the answer.
            assertTrue(ExpenseFormError.Title in viewModel.state.value.errors)
            assertFalse(viewModel.state.value.editingSplit)

            viewModel.onTitleChange("Dinner")
            viewModel.onSave()
            advanceUntilIdle()

            // Now it is the only thing left, and the numbers are in there.
            assertTrue(viewModel.state.value.editingSplit)
        }

    // --- which field a refused save sends you back to -----------------------

    @Test
    fun `points at the topmost field that is wrong`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        // The amount sits above the title on this form, and a set has no
        // order of its own, so this is what decides where the form scrolls.
        assertEquals(ExpenseFormError.Amount, viewModel.state.value.firstError)
    }

    @Test
    fun `moves on to the next field once the first is fixed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onAmountChange("30")

        assertEquals(ExpenseFormError.Title, viewModel.state.value.firstError)
    }

    @Test
    fun `counts a refused save so the same one can scroll twice`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.refusedSaves)

        // Nothing changed, so the errors are identical, and a form the user
        // has scrolled away from still has to come back.
        viewModel.onSave()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.refusedSaves)
    }

    @Test
    fun `does not send you anywhere for a save the server refused`() = runTest(dispatcher) {
        api.createExpenseResult = SpliitResult.Failure(
            SpliitError.Procedure("BAD_REQUEST", "no"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("30")

        viewModel.onSave()
        advanceUntilIdle()

        // It belongs to no field, and it is reported at the foot of the form
        // where the Save button that caused it already is.
        assertNull(viewModel.state.value.firstError)
        assertEquals(0, viewModel.state.value.refusedSaves)
    }

    // --- the user's own notation -------------------------------------------

    @Test
    fun `an expense opened in a comma locale can be saved again untouched`() =
        runTest(dispatcher) {
            val original = Locale.getDefault()
            Locale.setDefault(Locale.FRANCE)
            try {
                api.getExpenseResult = SpliitResult.Success(existing().copy(amount = 1234))
                val viewModel = viewModel("e1")
                advanceUntilIdle()

                // Seeded in French, so it reads "12,34", and the form has to
                // be able to read back what it wrote.
                assertEquals("12,34", viewModel.state.value.amountText)

                viewModel.onSave()
                advanceUntilIdle()

                val input = api.callsTo("updateExpense").single().arguments[2] as ExpenseInput
                assertEquals(1234L, input.amount)
            } finally {
                Locale.setDefault(original)
            }
        }

    @Test
    fun `reads an amount typed with a comma`() = runTest(dispatcher) {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onTitleChange("Dîner")
            viewModel.onAmountChange("30,50")

            viewModel.onSave()
            advanceUntilIdle()

            assertEquals(3050L, sentInput().amount)
        } finally {
            Locale.setDefault(original)
        }
    }

    // --- an expense paid in another currency -------------------------------

    @Test
    fun `starts in the group's own currency`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("EUR", viewModel.state.value.entryCurrency.code)
        assertEquals(false, viewModel.state.value.isConverted)
        // Nothing is asked of anybody until a currency is actually changed.
        assertTrue(rates.asked.isEmpty())
    }

    @Test
    fun `looks the rate up for the day the money was spent`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDateChange(LocalDate.parse("2026-09-11"))

        viewModel.onEntryCurrencyChange("BRL")
        advanceUntilIdle()

        // The expense's own currency is the base, which is the direction the
        // rate is stored in and the one upstream queries.
        assertEquals(
            listOf(Triple(LocalDate.parse("2026-09-11"), "BRL", "EUR")),
            rates.asked,
        )
    }

    @Test
    fun `converts what was typed into what the group is charged`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEntryCurrencyChange("BRL")
        advanceUntilIdle()

        viewModel.onAmountChange("540.00")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(54_000L, state.enteredAmount)
        // 540.00 BRL at 0.17857, which is what the split and every balance
        // downstream of it will be built from.
        assertEquals(9643L, state.amount)
    }

    @Test
    fun `saves the group amount and keeps what was actually paid`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEntryCurrencyChange("BRL")
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("540.00")
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        val input = sentInput()
        assertEquals(9643L, input.amount)
        assertEquals(54_000L, input.originalAmount)
        assertEquals("BRL", input.originalCurrency)
        // A plain decimal, because it goes out as a JSON number.
        assertEquals("0.17857", input.conversionRate)
    }

    @Test
    fun `an expense in the group's own currency carries no conversion`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("96.43")
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        val input = sentInput()
        assertEquals(9643L, input.amount)
        assertNull(input.originalAmount)
        assertNull(input.originalCurrency)
        assertNull(input.conversionRate)
    }

    @Test
    fun `going back to the group's currency drops the rate with it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEntryCurrencyChange("BRL")
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.conversionRateText.isEmpty())

        viewModel.onEntryCurrencyChange("EUR")
        advanceUntilIdle()

        // A rate left behind would be saved with an expense that has no other
        // currency in it.
        assertEquals("", viewModel.state.value.conversionRateText)
        assertEquals(false, viewModel.state.value.isConverted)
    }

    @Test
    fun `refuses to save a converted expense with no rate`() = runTest(dispatcher) {
        rates.rate = null
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEntryCurrencyChange("BRL")
        advanceUntilIdle()
        viewModel.onTitleChange("Dinner")
        viewModel.onAmountChange("540.00")
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(ExpenseFormError.Rate in viewModel.state.value.errors)
        assertTrue(api.callsTo("createExpense").isEmpty())
    }

    @Test
    fun `a rate typed in the dialog is what gets used`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEntryCurrencyChange("BRL")
        advanceUntilIdle()
        viewModel.onAmountChange("540.00")

        viewModel.onRateOpen()
        viewModel.onRateDraftChange("0.2")
        viewModel.onRateSave()
        advanceUntilIdle()

        assertEquals("0.2", viewModel.state.value.conversionRateText)
        assertEquals(10_800L, viewModel.state.value.amount)
    }

    @Test
    fun `leaving the dialog alone leaves the rate alone`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEntryCurrencyChange("BRL")
        advanceUntilIdle()
        val before = viewModel.state.value.conversionRateText

        viewModel.onRateOpen()
        viewModel.onRateDraftChange("999")
        viewModel.onRateDismiss()
        advanceUntilIdle()

        assertEquals(before, viewModel.state.value.conversionRateText)
    }

    @Test
    fun `editing one opens on what was paid, not on the converted amount`() =
        runTest(dispatcher) {
            api.getExpenseResult = SpliitResult.Success(
                existing().copy(
                    amount = 9643,
                    originalAmount = 54_000,
                    originalCurrency = "BRL",
                    conversionRate = "0.17857",
                ),
            )
            val viewModel = viewModel("e1")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("BRL", state.entryCurrency.code)
            assertEquals("540.00", state.amountText)
            assertEquals("0.17857", state.conversionRateText)
            // And the group amount is unchanged by the round trip.
            assertEquals(9643L, state.amount)
        }

    @Test
    fun `a half-typed expense comes back`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = viewModel(savedState = saved)
        advanceUntilIdle()
        before.onTitleChange("Dinner")
        before.onAmountChange("42.50")
        before.onDateChange(LocalDate.parse("2026-09-04"))
        before.onPayerChange("p2")
        before.onNotesToggle()
        before.onNotesChange("Ana had the fish")
        before.onRecurrenceChange(RecurrenceRule.WEEKLY)
        before.onSplitModeChange(SplitMode.BY_AMOUNT)
        advanceUntilIdle()
        val split = before.state.value.paidFor

        val after = viewModel(savedState = saved.throughProcessDeath())
        advanceUntilIdle()

        val state = after.state.value
        assertEquals("Dinner", state.title)
        assertEquals("42.50", state.amountText)
        assertEquals(LocalDate.parse("2026-09-04"), state.date)
        assertEquals("p2", state.paidById)
        assertEquals("Ana had the fish", state.notes)
        assertTrue(state.showNotes)
        assertEquals(RecurrenceRule.WEEKLY, state.recurrenceRule)
        assertEquals(SplitMode.BY_AMOUNT, state.splitMode)
        assertEquals(split, state.paidFor)
    }

    @Test
    fun `the currency it was typed in comes back, with its rate`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = viewModel(savedState = saved)
        advanceUntilIdle()
        before.onAmountChange("540")
        before.onEntryCurrencyChange("BRL")
        advanceUntilIdle()
        val rate = before.state.value.conversionRateText

        val after = viewModel(savedState = saved.throughProcessDeath())
        advanceUntilIdle()

        assertEquals("BRL", after.state.value.entryCurrency.code)
        assertEquals(rate, after.state.value.conversionRateText)
        assertEquals("EUR", after.state.value.currency.code)
    }

    @Test
    fun `an edit keeps what was typed over what the server says`() = runTest(dispatcher) {
        api.getExpenseResult = SpliitResult.Success(existing())
        val saved = SavedStateHandle()
        val before = viewModel(expenseId = "e1", savedState = saved)
        advanceUntilIdle()
        before.onTitleChange("Dinner and drinks")
        advanceUntilIdle()

        val after = viewModel(expenseId = "e1", savedState = saved.throughProcessDeath())
        advanceUntilIdle()

        assertEquals("Dinner and drinks", after.state.value.title)
        assertFalse(after.state.value.isNew)
    }

    @Test
    fun `a draft naming somebody no longer in the group drops them`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = viewModel(savedState = saved)
        advanceUntilIdle()
        before.onPayerChange("p3")
        advanceUntilIdle()

        groups.forget("g1")
        api.groupResult = SpliitResult.Success(group("g1", listOf(ana, ben)))
        val after = viewModel(savedState = saved.throughProcessDeath())
        advanceUntilIdle()

        assertFalse("p3" in after.state.value.paidFor.keys)
        assertFalse(after.state.value.paidById == "p3")
    }

    @Test
    fun `an untouched form is not restored over a fresh one`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(savedState = saved)
        advanceUntilIdle()

        val after = viewModel(savedState = saved.throughProcessDeath())
        advanceUntilIdle()

        assertEquals("", after.state.value.title)
        assertEquals("", after.state.value.amountText)
        assertEquals(3, after.state.value.paidFor.size)
    }
}
