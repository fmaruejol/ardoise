package io.github.fmaruejol.ardoise.ui.grouplist

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.BalanceRepository
import io.github.fmaruejol.ardoise.data.FakeConnectivity
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.groupSummary
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GroupListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val repository = GroupRepository(api, preferences, cache, outbox)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val connectivity = FakeConnectivity()

    private val balances = BalanceRepository(api, preferences, cache)

    private fun viewModel() = GroupListViewModel(repository, balances, connectivity)

    private fun position(groupId: String, amount: Long) = UserGroupBalance(
        groupId = groupId,
        groupName = groupId,
        currencySymbol = "€",
        currencyCode = "EUR",
        participantId = "p1",
        participantName = "Ana",
        amount = amount,
    )

    // --- the list ----------------------------------------------------------

    @Test
    fun `starts loading and settles on an empty list`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `shows the groups this device knows`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1", "Trip")))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("Trip"), viewModel.state.value.groups.map { it.name })
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `a failed refresh does not blank the list`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1", "Trip")))
        val viewModel = viewModel()
        advanceUntilIdle()

        api.listGroupsResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        viewModel.onRefresh()
        advanceUntilIdle()

        // Losing what was on screen would be a worse answer than a stale one
        // plus an explanation.
        assertEquals(1, viewModel.state.value.groups.size)
        assertTrue(viewModel.state.value.error is SpliitError.Network)
    }

    @Test
    fun `the pull indicator stays up until the refresh answers`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1", "Trip")))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isRefreshing)

        val held = api.hold("listGroups")
        viewModel.onRefresh()
        advanceUntilIdle()

        // The cached rows are already back; the request is not.
        assertTrue(viewModel.state.value.isRefreshing)

        held.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `an empty list is not an error state`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Nothing was asked of the server, so there is nothing to have failed.
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.isEmpty)
    }

    // --- searching ---------------------------------------------------------

    @Test
    fun `filters the list by name`() = runTest(dispatcher) {
        listAll("Lisbon trip", "Flat 12B", "Ski weekend")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchOpen()
        viewModel.onQueryChange("fl")

        assertEquals(listOf("Flat 12B"), viewModel.state.value.visibleGroups.map { it.name })
        assertFalse(viewModel.state.value.hasNoMatches)
    }

    @Test
    fun `says so when nothing matches, rather than looking empty`() = runTest(dispatcher) {
        listAll("Lisbon trip")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchOpen()
        viewModel.onQueryChange("berlin")

        // isEmpty means "you have no groups", which would be a lie here and
        // would offer to create one instead of clearing the search.
        assertTrue(viewModel.state.value.hasNoMatches)
        assertFalse(viewModel.state.value.isEmpty)
    }

    @Test
    fun `closing the search puts every group back`() = runTest(dispatcher) {
        listAll("Lisbon trip", "Flat 12B")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSearchOpen()
        viewModel.onQueryChange("flat")

        viewModel.onSearchClose()

        assertFalse(viewModel.state.value.isSearching)
        assertEquals(2, viewModel.state.value.visibleGroups.size)
    }

    private suspend fun listAll(vararg names: String) {
        names.forEachIndexed { index, _ -> preferences.rememberGroup("g$index") }
        api.listGroupsResult = SpliitResult.Success(
            names.mapIndexed { index, name -> groupSummary("g$index", name) },
        )
    }

    // --- where the user stands in each group -------------------------------

    @Test
    fun `carries each group's own position onto its card`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        preferences.setActiveParticipantId("g1", "p1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))
        api.balancesForUserResult = SpliitResult.Success(listOf(position("g1", 8420)))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(mapOf("g1" to 8420L), viewModel.state.value.positions)
    }

    @Test
    fun `a group nobody has claimed has no position at all`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))

        val viewModel = viewModel()
        advanceUntilIdle()

        // Not zero: zero means settled.
        assertEquals(emptyMap<String, Long>(), viewModel.state.value.positions)
        assertTrue(api.callsTo("balancesForUser").isEmpty())
    }

    @Test
    fun `a failed balance read leaves the list alone`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        preferences.setActiveParticipantId("g1", "p1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))
        api.balancesForUserResult = SpliitResult.Failure(SpliitError.Network(RuntimeException()))

        val viewModel = viewModel()
        advanceUntilIdle()

        // The line is an extra on a card that reads perfectly without it, and
        // it is not cached, so offline is a missing line, not an error.
        assertEquals(emptyMap<String, Long>(), viewModel.state.value.positions)
        assertEquals(1, viewModel.state.value.groups.size)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `answering who you are fills the line in`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))
        api.balancesForUserResult = SpliitResult.Success(listOf(position("g1", -1200)))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(emptyMap<String, Long>(), viewModel.state.value.positions)

        preferences.setActiveParticipantId("g1", "p1")
        advanceUntilIdle()

        // The read follows the preference, so coming back from a group where
        // the question was just answered fills the card in.
        assertEquals(mapOf("g1" to -1200L), viewModel.state.value.positions)
    }
}
