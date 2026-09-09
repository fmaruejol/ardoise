package io.github.fmaruejol.ardoise.ui.totals

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.ExpensePage
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.PaidForWithParticipant
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
class TotalsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val groups = GroupRepository(api, preferences, cache, outbox)
    private val expenses = ExpenseRepository(api, preferences, cache, outbox)
    private val connectivity = FakeConnectivity()

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")
    private val cara = Participant("p3", "Cara")
    private val food = Category(1, "Food and Drink", "Dining Out")
    private val transport = Category(2, "Transportation", "Taxi")
    private val offline = SpliitError.Network(IOException("no network"))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api.groupResult = SpliitResult.Success(group("g1", listOf(ana, ben, cara)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TotalsViewModel("g1", groups, expenses, connectivity)

    private fun list(vararg expenses: ExpenseSummary) {
        api.listExpensesResult =
            SpliitResult.Success(ExpensePage(expenses.toList(), hasMore = false))
    }

    @Test
    fun `adds the group up`() = runTest(dispatcher) {
        list(
            expense("e1", "Surf", 14_000, "2026-09-12"),
            expense("e2", "Dinner", 9600, "2026-09-11"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(23_600L, state.stats?.total)
        assertEquals(3, state.stats?.participantCount)
        assertNull(state.error)
    }

    @Test
    fun `a reimbursement is settling up, not spending`() = runTest(dispatcher) {
        list(
            expense("e1", "Dinner", 9600, "2026-09-11"),
            expense("e2", "Ben pays Ana", 4800, "2026-09-12", isReimbursement = true),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(9600L, viewModel.state.value.stats?.total)
        assertEquals(listOf(9600L), viewModel.state.value.categories.map { it.total })
    }

    @Test
    fun `the widest category bar is full, the rest are drawn against it`() = runTest(dispatcher) {
        list(
            expense("e1", "Surf", 15_000, "2026-09-12", category = food),
            expense("e2", "Taxi", 5000, "2026-09-11", category = transport),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val bars = viewModel.state.value.categories
        assertEquals(listOf("Dining Out", "Taxi"), bars.map { it.label })
        assertEquals(1f, bars.first().weight, 0.0001f)
        assertEquals(1f / 3f, bars.last().weight, 0.0001f)
    }

    @Test
    fun `a group that has spent nothing has no bar of width zero over zero`() =
        runTest(dispatcher) {
            list(expense("e1", "Ben pays Ana", 4800, "2026-09-12", isReimbursement = true))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.categories.all { it.weight == 0f })
            assertTrue(viewModel.state.value.isEmpty)
        }

    @Test
    fun `a payer keeps the position the group gave them`() = runTest(dispatcher) {
        list(
            expense("e1", "Surf", 20_000, "2026-09-12", paidBy = cara),
            expense("e2", "Dinner", 9600, "2026-09-11", paidBy = ana),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val payers = viewModel.state.value.payers
        assertEquals(listOf("Cara", "Ana", "Ben"), payers.map { it.name })
        assertEquals(listOf(2, 0, 1), payers.map { it.index })
        assertEquals(listOf(20_000L, 9600L, 0L), payers.map { it.paid })
    }

    @Test
    fun `marks whoever this device says it is`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        preferences.setActiveParticipantId("g1", ben.id)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(ben.id), viewModel.state.value.payers.filter { it.isYou }.map { it.participantId })
    }

    @Test
    fun `a failed read stops loading and says why`() = runTest(dispatcher) {
        api.listExpensesResult = SpliitResult.Failure(offline)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(offline, viewModel.state.value.error)
    }

    @Test
    fun `retrying asks again and clears the error`() = runTest(dispatcher) {
        api.listExpensesResult = SpliitResult.Failure(offline)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(offline, viewModel.state.value.error)

        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        viewModel.onRetry()
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(9600L, viewModel.state.value.stats?.total)
    }

    @Test
    fun `follows the platform in and out of a network`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isOffline)

        connectivity.setOffline(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isOffline)

        connectivity.setOffline(false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isOffline)
    }

    @Test
    fun `asks for the whole group, since a missing expense is a wrong total`() =
        runTest(dispatcher) {
            list(expense("e1", "Dinner", 9600, "2026-09-11"))
            viewModel()
            advanceUntilIdle()

            val limits = api.callsTo("listExpenses").map { it.arguments[2] }
            assertTrue(limits.all { it == TotalsViewModel.ALL_EXPENSES })
        }

    private fun expense(
        id: String,
        title: String,
        amount: Long,
        date: String,
        isReimbursement: Boolean = false,
        category: Category = food,
        paidBy: Participant = ana,
    ) = ExpenseSummary(
        id = id,
        title = title,
        amount = amount,
        originalAmount = null,
        originalCurrency = null,
        expenseDate = LocalDate.parse(date),
        createdAt = Instant.parse("${date}T12:00:00Z"),
        category = category,
        paidBy = paidBy,
        paidFor = listOf(ana, ben, cara).map { PaidForWithParticipant(it, 1) },
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = isReimbursement,
        documentCount = 0,
    )
}
