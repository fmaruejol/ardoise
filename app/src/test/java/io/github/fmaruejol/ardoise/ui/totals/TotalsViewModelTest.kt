package io.github.fmaruejol.ardoise.ui.totals

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.stats.CategoryTotal
import io.github.fmaruejol.ardoise.core.stats.GroupStats
import io.github.fmaruejol.ardoise.core.stats.ParticipantTotal
import io.github.fmaruejol.ardoise.core.stats.StatsBreakdown
import io.github.fmaruejol.ardoise.data.FakeConnectivity
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.StatsRepository
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

    private fun viewModel() = TotalsViewModel("g1", groups, StatsRepository(cache), connectivity)

    private fun answer(
        total: Long,
        categories: List<CategoryTotal> = listOf(CategoryTotal(food, total)),
        payers: List<ParticipantTotal> = listOf(
            ParticipantTotal(ana.id, ana.name, total),
            ParticipantTotal(ben.id, ben.name, 0),
            ParticipantTotal(cara.id, cara.name, 0),
        ),
    ) {
        api.groupStatsResult = SpliitResult.Success(
            GroupStats(
                total = total,
                breakdown = StatsBreakdown(
                    firstDate = LocalDate.of(2026, 9, 11),
                    lastDate = LocalDate.of(2026, 9, 12),
                    byCategory = categories,
                    byParticipant = payers,
                ),
            ),
        )
    }

    @Test
    fun `shows what the server added up`() = runTest(dispatcher) {
        answer(23_700)
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(23_700L, state.stats?.total)
        assertEquals(2, state.averages?.days)
        assertEquals(7_900L, state.averages?.perPerson)
        assertNull(state.error)
    }

    @Test
    fun `asks the stats procedure, not for every expense`() = runTest(dispatcher) {
        answer(9600)
        viewModel()
        advanceUntilIdle()

        assertEquals(1, api.callsTo("groupStats").size)
        assertTrue(api.callsTo("listExpenses").isEmpty())
    }

    @Test
    fun `the widest category bar is full, the rest are drawn against it`() = runTest(dispatcher) {
        answer(20_000, categories = listOf(CategoryTotal(food, 15_000), CategoryTotal(transport, 5000)))
        val viewModel = viewModel()
        advanceUntilIdle()

        val bars = viewModel.state.value.categories
        assertEquals(listOf("Dining Out", "Taxi"), bars.map { it.label })
        assertEquals(1f, bars.first().weight, 0.0001f)
        assertEquals(1f / 3f, bars.last().weight, 0.0001f)
    }

    @Test
    fun `a category netting below zero gets no bar rather than a negative one`() =
        runTest(dispatcher) {
            answer(4000, categories = listOf(CategoryTotal(food, 5000), CategoryTotal(transport, -1000)))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(0f, viewModel.state.value.categories.last().weight, 0f)
        }

    @Test
    fun `a group that has spent nothing is empty`() = runTest(dispatcher) {
        answer(0, categories = emptyList())
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun `a payer keeps the position the group gave them`() = runTest(dispatcher) {
        answer(
            29_600,
            payers = listOf(
                ParticipantTotal(cara.id, cara.name, 20_000),
                ParticipantTotal(ana.id, ana.name, 9600),
                ParticipantTotal(ben.id, ben.name, 0),
            ),
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
        answer(9600)
        preferences.setActiveParticipantId("g1", ben.id)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(ben.id), viewModel.state.value.payers.filter { it.isYou }.map { it.participantId })
    }

    @Test
    fun `an older instance gives the total and says why there is nothing else`() =
        runTest(dispatcher) {
            api.groupStatsResult = SpliitResult.Success(GroupStats(total = 9000, breakdown = null))
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(9000L, state.stats?.total)
            assertEquals(3000L, state.averages?.perPerson)
            assertNull(state.averages?.days)
            assertTrue(state.hasNoBreakdown)
            assertTrue(state.categories.isEmpty())
            assertTrue(state.payers.isEmpty())
            assertFalse(state.isEmpty)
        }

    @Test
    fun `what was fetched once is shown again with no network`() = runTest(dispatcher) {
        answer(9600)
        viewModel()
        advanceUntilIdle()

        api.groupStatsResult = SpliitResult.Failure(offline)
        val again = viewModel()
        advanceUntilIdle()

        assertEquals(9600L, again.state.value.stats?.total)
        assertEquals(offline, again.state.value.error)
    }

    @Test
    fun `a failed read stops loading and says why`() = runTest(dispatcher) {
        api.groupStatsResult = SpliitResult.Failure(offline)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(offline, viewModel.state.value.error)
    }

    @Test
    fun `retrying asks again and clears the error`() = runTest(dispatcher) {
        api.groupStatsResult = SpliitResult.Failure(offline)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(offline, viewModel.state.value.error)

        answer(9600)
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
}
