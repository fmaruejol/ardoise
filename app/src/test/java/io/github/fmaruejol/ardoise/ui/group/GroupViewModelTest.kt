package io.github.fmaruejol.ardoise.ui.group

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.ExpensePage
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.PaidForWithParticipant
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.CategoryRepository
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
class GroupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val groups = GroupRepository(api, preferences, cache, outbox)
    private val expenses = ExpenseRepository(api, preferences, cache, outbox)

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")
    private val food = Category(1, "Food and Drink", "Dining Out")
    private val transport = Category(2, "Transportation", "Taxi")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api.groupResult = SpliitResult.Success(group("g1", listOf(ana, ben)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val connectivity = FakeConnectivity()

    private fun viewModel() =
        GroupViewModel("g1", groups, expenses, CategoryRepository(cache), connectivity)

    private fun list(vararg expenses: ExpenseSummary) {
        api.listExpensesResult =
            SpliitResult.Success(ExpensePage(expenses.toList(), hasMore = false))
    }

    @Test
    fun `groups the feed by day, newest day first`() = runTest(dispatcher) {
        list(
            expense("e1", "Surf", 14_000, "2026-09-12"),
            expense("e2", "Pastries", 780, "2026-09-12"),
            expense("e3", "Dinner", 9600, "2026-09-11"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        val days = viewModel.state.value.days
        assertEquals(listOf(LocalDate.parse("2026-09-12"), LocalDate.parse("2026-09-11")), days.map { it.date })
        assertEquals(listOf("Surf", "Pastries"), days.first().expenses.map { it.title })
    }

    @Test
    fun `totals what the group spent`() = runTest(dispatcher) {
        list(
            expense("e1", "Surf", 14_000, "2026-09-12"),
            expense("e2", "Dinner", 9600, "2026-09-11"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(23_600L, viewModel.state.value.totalSpent)
    }

    @Test
    fun `a reimbursement is settling up, not spending`() = runTest(dispatcher) {
        list(
            expense("e1", "Dinner", 9600, "2026-09-11"),
            expense("e2", "Ben pays Ana", 4800, "2026-09-12", isReimbursement = true),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        // "Total spent" answers "how much has this trip cost"; money moving
        // between two people in the group has not cost anyone anything.
        assertEquals(9600L, viewModel.state.value.totalSpent)
    }

    @Test
    fun `paying for someone else leaves you up by their part`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p1")
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        // Ana paid 96.00 and owes 48.00 of it, so she is up 48.00, which is
        // not the same as her share, and is what the row shows.
        assertEquals(4800L, viewModel.state.value.yourBalanceChanges["e1"])
        // The totals card still asks what the trip cost you, which is a share.
        assertEquals(4800L, viewModel.state.value.yourShare)
    }

    @Test
    fun `somebody else paying leaves you down by your share`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(-4800L, viewModel.state.value.yourBalanceChanges["e1"])
        // Down 48.00, but the trip still cost you 48.00.
        assertEquals(4800L, viewModel.state.value.yourShare)
    }

    @Test
    fun `has nothing to show until you say who you are`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        // A number here would be somebody else's.
        assertNull(viewModel.state.value.yourShare)
        assertTrue(viewModel.state.value.yourBalanceChanges.isEmpty())
    }

    @Test
    fun `picks it up as soon as you say who you are`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        preferences.setActiveParticipantId("g1", "p2")
        advanceUntilIdle()

        assertEquals(-4800L, viewModel.state.value.yourBalanceChanges["e1"])
        assertEquals(4800L, viewModel.state.value.yourShare)
    }

    @Test
    fun `an expense you are not part of moves you neither way`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        list(expense("e1", "Ana's taxi", 1850, "2026-09-11", paidFor = listOf(ana)))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.yourBalanceChanges["e1"])
    }

    @Test
    fun `paying for only yourself is a wash`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p1")
        list(expense("e1", "Ana's coffee", 350, "2026-09-11", paidFor = listOf(ana)))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(0L, viewModel.state.value.yourBalanceChanges["e1"])
        // It still cost you 3.50.
        assertEquals(350L, viewModel.state.value.yourShare)
    }

    @Test
    fun `an empty group is not an error`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `asks the server to filter rather than filtering a page`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchOpen()
        viewModel.onQueryChange("dinner")
        advanceUntilIdle()

        // Only the first page is loaded, so filtering it locally would hide
        // matches that are simply further down.
        assertEquals("dinner", api.callsTo("listExpenses").last().arguments[3])
    }

    @Test
    fun `pulling on a search runs the search again`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSearchOpen()
        viewModel.onQueryChange("dinner")
        advanceUntilIdle()
        val before = api.callsTo("listExpenses").size

        list(expense("e1", "Dinner", 9600, "2026-09-11"), expense("e2", "Dinner two", 500, "2026-09-12"))
        viewModel.onRefresh()
        advanceUntilIdle()

        // A search is not cached, so the read is the request.
        assertEquals("dinner", api.callsTo("listExpenses").last().arguments[3])
        assertEquals(before + 1, api.callsTo("listExpenses").size)
        assertEquals(2, viewModel.state.value.days.sumOf { it.expenses.size })
    }

    @Test
    fun `says when a search matches nothing rather than looking empty`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        api.listExpensesResult =
            SpliitResult.Success(ExpensePage(emptyList(), hasMore = false))
        viewModel.onSearchOpen()
        viewModel.onQueryChange("berlin")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasNoMatches)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `asks for more when there is more`() = runTest(dispatcher) {
        api.listExpensesResult = SpliitResult.Success(
            ExpensePage(
                listOf(expense("e1", "Dinner", 9600, "2026-09-11")),
                hasMore = true,
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        val before = api.callsTo("listExpenses").last().arguments[2] as Int

        viewModel.onLoadMore()
        advanceUntilIdle()

        assertTrue(api.callsTo("listExpenses").last().arguments[2] as Int > before)
    }

    @Test
    fun `narrows the feed to one category`() = runTest(dispatcher) {
        list(
            expense("e1", "Dinner", 9600, "2026-09-11"),
            expense("e2", "Taxi", 1850, "2026-09-11", category = transport),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCategoryFilter(transport.id)
        advanceUntilIdle()

        assertEquals(listOf("Taxi"), viewModel.state.value.days.flatMap { it.expenses }.map { it.title })
        assertEquals(1, viewModel.state.value.expenseCount)
    }

    @Test
    fun `narrows the feed to one payer`() = runTest(dispatcher) {
        list(
            expense("e1", "Dinner", 9600, "2026-09-11"),
            expense("e2", "Taxi", 1850, "2026-09-11", paidBy = ben),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPayerFilter("p2")
        advanceUntilIdle()

        assertEquals(listOf("Taxi"), viewModel.state.value.days.flatMap { it.expenses }.map { it.title })
    }

    @Test
    fun `totals what the filter matched, not what the group spent`() = runTest(dispatcher) {
        list(
            expense("e1", "Dinner", 9600, "2026-09-11"),
            expense("e2", "Taxi", 1850, "2026-09-11", category = transport),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCategoryFilter(transport.id)
        advanceUntilIdle()

        // The line above the results reads "1 matching expense · €18.50", so
        // the total has to be of the matches.
        assertEquals(1850L, viewModel.state.value.totalSpent)
    }

    @Test
    fun `a filtered feed is fetched whole, not one page at a time`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()
        val firstPage = api.callsTo("listExpenses").last().arguments[2] as Int

        viewModel.onCategoryFilter(1)
        advanceUntilIdle()

        // Filtering happens here rather than on the server, so a count of
        // "3 matching expenses" would be a count of one page otherwise.
        assertTrue(api.callsTo("listExpenses").last().arguments[2] as Int > firstPage)
        assertFalse(viewModel.state.value.hasMore)
    }

    @Test
    fun `offers only the categories this group actually uses`() = runTest(dispatcher) {
        list(
            expense("e1", "Dinner", 9600, "2026-09-11"),
            expense("e2", "Taxi", 1850, "2026-09-11", category = transport),
            expense("e3", "Lunch", 2200, "2026-09-10"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        // Commonest first: Food twice, Transport once.
        assertEquals(
            listOf("Dining Out", "Taxi"),
            viewModel.state.value.categories.map { it.name },
        )
    }

    @Test
    fun `a date filter keeps only what falls inside it`() = runTest(dispatcher) {
        val today = LocalDate.now()
        list(
            expense("e1", "Recent", 100, today.toString()),
            expense("e2", "Old", 100, today.minusDays(90).toString()),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDateFilter(DateFilter.Last30Days)
        advanceUntilIdle()

        assertEquals(listOf("Recent"), viewModel.state.value.days.flatMap { it.expenses }.map { it.title })
    }

    @Test
    fun `last 30 days counts today as one of them`() {
        val today = LocalDate.parse("2026-09-30")

        assertTrue(DateFilter.Last30Days.matches(today, today))
        assertTrue(DateFilter.Last30Days.matches(today.minusDays(29), today))
        // Thirty days, not thirty-one.
        assertFalse(DateFilter.Last30Days.matches(today.minusDays(30), today))
    }

    @Test
    fun `leaving search throws the filters away with it`() = runTest(dispatcher) {
        list(
            expense("e1", "Dinner", 9600, "2026-09-11"),
            expense("e2", "Taxi", 1850, "2026-09-11", category = transport),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchOpen()
        viewModel.onCategoryFilter(transport.id)
        advanceUntilIdle()
        viewModel.onSearchClose()
        advanceUntilIdle()

        // The chips live in the search bar; closing it and finding the feed
        // still narrowed with nothing on screen to say so would be a trap.
        assertFalse(viewModel.state.value.isFiltered)
        assertEquals(2, viewModel.state.value.expenseCount)
    }

    @Test
    fun `an empty group is empty, a filter matching nothing is not`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCategoryFilter(transport.id)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasNoMatches)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `asks who you are once the group is loaded and nobody has said`() =
        runTest(dispatcher) {
            list(expense("e1", "Dinner", 9600, "2026-09-11"))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.needsIdentity)
        }

    @Test
    fun `does not ask before the group has arrived`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(null)
        val viewModel = viewModel()
        advanceUntilIdle()

        // Nobody is identified here either, but there is nobody to offer.
        assertFalse(viewModel.state.value.needsIdentity)
    }

    @Test
    fun `stops asking once you have said`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onYouChange(1)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.needsIdentity)
        assertEquals("p2", viewModel.state.value.activeParticipantId)
        // And the numbers it was blocking arrive with it.
        assertEquals(4800L, viewModel.state.value.yourShare)
    }

    @Test
    fun `the choice is saved on the spot, not on a button`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onYouChange(0)
        advanceUntilIdle()

        // It cannot fail and there is nothing to roll back, so tying it to a
        // Save would only be a way to lose it.
        assertEquals("p1", preferences.activeParticipantId("g1").first())
        assertTrue(api.callsTo("updateGroup").isEmpty())
    }

    @Test
    fun `a name that is not there changes nothing`() = runTest(dispatcher) {
        list(expense("e1", "Dinner", 9600, "2026-09-11"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPickYouOpen()
        viewModel.onYouChange(99)
        advanceUntilIdle()

        assertNull(viewModel.state.value.activeParticipantId)
        // The dialog stays open rather than closing on a choice that was not
        // made.
        assertTrue(viewModel.state.value.pickingYou)
    }

    private fun expense(
        id: String,
        title: String,
        amount: Long,
        date: String,
        isReimbursement: Boolean = false,
        paidFor: List<Participant> = listOf(ana, ben),
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
        paidFor = paidFor.map { PaidForWithParticipant(it, 1) },
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = isReimbursement,
        documentCount = 0,
    )

    // --- the platform's own answer ----------------------------------------

    @Test
    fun `follows the platform in and out of a network`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isOffline)

        connectivity.setOffline(true)
        advanceUntilIdle()
        // Nothing has failed and no read has been made since the network went,
        // so this is the only thing that can put the banner up.
        assertTrue(viewModel.state.value.isOffline)

        connectivity.setOffline(false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isOffline)
    }
}
