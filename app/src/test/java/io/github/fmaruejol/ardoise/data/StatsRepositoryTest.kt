package io.github.fmaruejol.ardoise.data

import android.app.Application
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.stats.CategoryTotal
import io.github.fmaruejol.ardoise.core.stats.GroupStats
import io.github.fmaruejol.ardoise.core.stats.ParticipantTotal
import io.github.fmaruejol.ardoise.core.stats.StatsBreakdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val cache = testCache(api, dispatcher)
    private val repository = StatsRepository(cache)

    private val stats = GroupStats(
        total = 9600,
        breakdown = StatsBreakdown(
            firstDate = LocalDate.of(2026, 9, 10),
            lastDate = LocalDate.of(2026, 9, 14),
            byCategory = listOf(
                CategoryTotal(Category(2, "Home", "Rent"), 6000),
                CategoryTotal(Category(1, "Food and Drink", "Dining Out"), 3600),
            ),
            byParticipant = listOf(ParticipantTotal("p2", "Ben", 9600), ParticipantTotal("p1", "Ana", 0)),
        ),
    )

    @Test
    fun `the cache gives back what the server said, in the server's order`() = runTest(dispatcher) {
        api.groupStatsResult = SpliitResult.Success(stats)
        repository.stats("g1").first { it is SpliitResult.Success }

        assertEquals(stats, cache.stats("g1").first())
    }

    @Test
    fun `an older instance's total alone stays alone`() = runTest(dispatcher) {
        api.groupStatsResult = SpliitResult.Success(GroupStats(total = 9600, breakdown = null))
        repository.stats("g1").first { it is SpliitResult.Success }

        assertNull(cache.stats("g1").first()?.breakdown)
    }

    @Test
    fun `an expense change refreshes the stats only once something has read them`() =
        runTest(dispatcher) {
            cache.refreshAfterExpenseChange("g1", ExpenseRepository.DEFAULT_PAGE_SIZE)
            advanceUntilIdle()
            assertEquals(0, api.callsTo("groupStats").size)

            api.groupStatsResult = SpliitResult.Success(stats)
            repository.stats("g1").first { it is SpliitResult.Success }
            cache.refreshAfterExpenseChange("g1", ExpenseRepository.DEFAULT_PAGE_SIZE)
            advanceUntilIdle()

            assertEquals(2, api.callsTo("groupStats").size)
        }

    @Test
    fun `forgetting a group takes its stats too`() = runTest(dispatcher) {
        api.groupStatsResult = SpliitResult.Success(stats)
        repository.stats("g1").first { it is SpliitResult.Success }

        cache.forget("g1")

        assertNull(cache.stats("g1").first())
    }
}
