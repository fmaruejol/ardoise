package io.github.fmaruejol.ardoise.data

import android.app.Application
import app.cash.turbine.test
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.ui.Retry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** When a read has finished refreshing: the one thing its emissions cannot say. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshScopeTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val repository = GroupRepository(api, preferences, cache, outbox)
    private val retry = Retry()

    private suspend fun knows(vararg ids: String) = ids.forEach { id ->
        preferences.rememberGroup(id)
        api.listGroupsResult = SpliitResult.Success(ids.map { groupSummary(it, "Trip") })
    }

    /** Cancelled by the caller: it never ends, and `runTest` waits for children. */
    private fun TestScope.reading() =
        launch { retry.restarting { repository.groups() }.collect { } }
            .also { advanceUntilIdle() }

    @Test
    fun `a refresh that changes nothing says nothing`() = runTest(dispatcher) {
        knows("g1")
        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            assertTrue(awaitItem() is SpliitResult.Success)
            advanceUntilIdle()
            // The refresh writes the same rows back, so what it produces
            // equals the cached value and is dropped.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the indicator stays up until the refresh answers`() = runTest(dispatcher) {
        knows("g1")
        val read = reading()

        retry.refreshing.test(timeout = TURBINE_TIMEOUT) {
            assertFalse(awaitItem())

            val held = api.hold("listGroups")
            retry.again()
            assertTrue(awaitItem())

            // The cached rows are already out; the request is not back.
            advanceUntilIdle()
            expectNoEvents()

            held.complete(Unit)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        read.cancel()
    }

    @Test
    fun `a failed refresh brings it down too`() = runTest(dispatcher) {
        knows("g1")
        val read = reading()

        retry.refreshing.test(timeout = TURBINE_TIMEOUT) {
            assertFalse(awaitItem())
            api.listGroupsResult =
                SpliitResult.Failure(SpliitError.Network(RuntimeException("off")))
            retry.again()
            assertTrue(awaitItem())
            // Nothing was written, so the cache may never speak again.
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        read.cancel()
    }

    @Test
    fun `a pull that asks the server nothing comes down`() = runTest(dispatcher) {
        // No group is known, so the read never reaches the cache at all.
        val read = reading()

        retry.refreshing.test(timeout = TURBINE_TIMEOUT) {
            assertFalse(awaitItem())
            retry.again()
            assertTrue(awaitItem())
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(api.callsTo("listGroups").isEmpty())
        read.cancel()
    }

    @Test
    fun `the first load pulls no indicator over itself`() = runTest(dispatcher) {
        knows("g1")

        retry.refreshing.test(timeout = TURBINE_TIMEOUT) {
            val read = reading()
            // `isLoading` is already saying this.
            assertFalse(awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
            read.cancel()
        }
        assertEquals(1, api.callsTo("listGroups").size)
    }

    @Test
    fun `the gesture and the banner's button are the same thing`() = runTest(dispatcher) {
        knows("g1")
        val read = reading()

        retry.refreshing.test(timeout = TURBINE_TIMEOUT) {
            assertFalse(awaitItem())
            retry.again()
            assertTrue(awaitItem())
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, api.callsTo("listGroups").size)
        read.cancel()
    }

    @Test
    fun `a refresh nobody asked for moves nothing`() = runTest(dispatcher) {
        knows("g1")
        val read = reading()

        retry.refreshing.test(timeout = TURBINE_TIMEOUT) {
            assertFalse(awaitItem())
            // What every mutation's own refresh is: collected with no scope in
            // context, so a save behind an open screen never spins it.
            cache.refreshGroups(listOf("g1"))
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        read.cancel()
    }
}
