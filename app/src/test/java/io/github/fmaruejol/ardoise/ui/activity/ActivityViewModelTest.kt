package io.github.fmaruejol.ardoise.ui.activity

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.Activity
import io.github.fmaruejol.ardoise.core.model.ActivityPage
import io.github.fmaruejol.ardoise.core.model.ActivityType
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.ActivityRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val groups = GroupRepository(api, preferences, cache, outbox)
    private val activities = ActivityRepository(cache)

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api.groupResult = SpliitResult.Success(group("g1", listOf(ana, ben)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val connectivity = FakeConnectivity()

    private fun viewModel() = ActivityViewModel("g1", activities, groups, connectivity)

    private fun page(vararg entries: Activity, hasMore: Boolean = false) {
        api.listActivitiesResult =
            SpliitResult.Success(ActivityPage(entries.toList(), hasMore))
    }

    /** A page as long as the limit. */
    private fun fullPage(): List<Activity> =
        (1..ActivityRepository.DEFAULT_PAGE_SIZE).map {
            activity("a$it", "2026-09-12T09:00:00Z")
        }

    private fun activity(
        id: String,
        at: String,
        by: String? = "p2",
        type: ActivityType = ActivityType.CREATE_EXPENSE,
        data: String? = "Dinner",
    ) = Activity(
        id = id,
        groupId = "g1",
        time = Instant.parse(at),
        activityType = type,
        participantId = by,
        expenseId = "e1",
        data = data,
    )

    @Test
    fun `cuts days by the device's clock, not the server's`() = runTest(dispatcher) {
        page(activity("a1", "2026-09-12T09:00:00Z"), activity("a2", "2026-09-11T09:00:00Z"))
        val viewModel = viewModel()
        advanceUntilIdle()

        // "Today" is read against the user's own clock, so the split has to be
        // in their zone.
        val zone = ZoneId.systemDefault()
        assertEquals(
            listOf(
                Instant.parse("2026-09-12T09:00:00Z").atZone(zone).toLocalDate(),
                Instant.parse("2026-09-11T09:00:00Z").atZone(zone).toLocalDate(),
            ),
            viewModel.state.value.days.map { it.date },
        )
        assertEquals(2, viewModel.state.value.days.size)
    }

    @Test
    fun `newest day first`() = runTest(dispatcher) {
        page(activity("a1", "2026-09-11T09:00:00Z"), activity("a2", "2026-09-12T09:00:00Z"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val days = viewModel.state.value.days.map { it.date }
        assertEquals(days.sortedDescending(), days)
    }

    @Test
    fun `resolves the participant to a name`() = runTest(dispatcher) {
        page(activity("a1", "2026-09-12T09:00:00Z", by = "p2"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val row = viewModel.state.value.days.single().rows.single()
        assertEquals("Ben", row.who)
        assertFalse(row.byYou)
    }

    @Test
    fun `knows when it was you`() = runTest(dispatcher) {
        preferences.setActiveParticipantId("g1", "p2")
        page(activity("a1", "2026-09-12T09:00:00Z", by = "p2"))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.days.single().rows.single().byYou)
    }

    @Test
    fun `an anonymous entry is not attributed to nobody in particular`() = runTest(dispatcher) {
        page(activity("a1", "2026-09-12T09:00:00Z", by = null))
        val viewModel = viewModel()
        advanceUntilIdle()

        val row = viewModel.state.value.days.single().rows.single()
        // Blank, so the screen can say "Someone" rather than leave a gap where
        // a name belongs.
        assertEquals("", row.who)
        assertFalse(row.byYou)
    }

    @Test
    fun `a blank payload is no title at all`() = runTest(dispatcher) {
        page(activity("a1", "2026-09-12T09:00:00Z", data = "  "))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.days.single().rows.single().what)
    }

    @Test
    fun `an unknown activity type still shows`() = runTest(dispatcher) {
        page(activity("a1", "2026-09-12T09:00:00Z", type = ActivityType.UNKNOWN))
        val viewModel = viewModel()
        advanceUntilIdle()

        // The log is display only; a type this client has never heard of is
        // still something that happened.
        assertEquals(1, viewModel.state.value.days.single().rows.size)
    }

    @Test
    fun `asks for a longer page rather than for the next one`() = runTest(dispatcher) {
        page(*fullPage().toTypedArray())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onLoadMore()
        advanceUntilIdle()

        // The same shape as the expenses feed.
        val last = api.callsTo("listActivities").last().arguments
        assertEquals(0, last[1])
        assertEquals(ActivityRepository.DEFAULT_PAGE_SIZE * 2, last[2])
    }

    @Test
    fun `everything on screen comes out of the cache, not just the first page`() =
        runTest(dispatcher) {
            page(*fullPage().toTypedArray())
            val viewModel = viewModel()
            advanceUntilIdle()

            api.listActivitiesResult = SpliitResult.Success(
                ActivityPage(
                    fullPage() + activity("older", "2026-09-10T09:00:00Z"),
                    hasMore = false,
                ),
            )
            viewModel.onLoadMore()
            advanceUntilIdle()

            val ids = viewModel.state.value.days.flatMap { it.rows }.map { it.id }
            assertEquals(ActivityRepository.DEFAULT_PAGE_SIZE + 1, ids.size)
            assertEquals("older", ids.last())
            assertFalse(viewModel.state.value.hasMore)
        }

    @Test
    fun `there is nothing more to ask for once the page is short`() =
        runTest(dispatcher) {
            page(activity("a1", "2026-09-12T09:00:00Z"))
            val viewModel = viewModel()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.hasMore)

            viewModel.onLoadMore()
            advanceUntilIdle()

            // One request, not two: a page shorter than the limit is the whole
            // log, and the button is not offered.
            assertEquals(1, api.callsTo("listActivities").size)
        }

    @Test
    fun `an empty log is not an error`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `a fresh page replaces the log rather than layering on it`() =
        runTest(dispatcher) {
            page(*fullPage().toTypedArray())
            val viewModel = viewModel()
            advanceUntilIdle()

            // Something changed the group, so the log comes back shorter. Rows
            // that are no longer in it must not be left behind under the new ones.
            api.listActivitiesResult = SpliitResult.Success(
                ActivityPage(listOf(activity("a3", "2026-09-13T09:00:00Z")), false),
            )
            viewModel.onRefresh()
            advanceUntilIdle()

            assertEquals(
                listOf("a3"),
                viewModel.state.value.days.flatMap { it.rows }.map { it.id },
            )
        }

    @Test
    fun `several entries on one day stay on one day`() = runTest(dispatcher) {
        page(
            activity("a1", "2026-09-12T09:00:00Z"),
            activity("a2", "2026-09-12T11:00:00Z"),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.days.size)
        assertEquals(2, viewModel.state.value.days.single().rows.size)
    }
}
