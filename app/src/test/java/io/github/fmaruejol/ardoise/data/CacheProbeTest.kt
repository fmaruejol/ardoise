package io.github.fmaruejol.ardoise.data

import android.app.Application
import app.cash.turbine.test
import io.github.fmaruejol.ardoise.data.local.CategoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** That Room's invalidation reaches a collector under the test dispatcher. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CacheProbeTest {
    private val dispatcher = StandardTestDispatcher()
    private val database = testDatabase(dispatcher)

    @Test
    fun `a write wakes a collector`() = runTest(dispatcher) {
        database.categories().observe().test(timeout = TURBINE_TIMEOUT) {
            assertEquals(emptyList<CategoryEntity>(), awaitItem())

            database.categories().upsert(listOf(CategoryEntity(1, "Food", "Dining")))
            advanceUntilIdle()

            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a write from another dao wakes a collector over its own table`() =
        runTest(dispatcher) {
            database.expenses().observePage("g1", 10).test(timeout = TURBINE_TIMEOUT) {
                assertEquals(0, awaitItem().size)

                database.groups().upsertParticipants(emptyList())
                advanceUntilIdle()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
