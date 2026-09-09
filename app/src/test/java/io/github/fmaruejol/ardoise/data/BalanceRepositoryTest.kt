package io.github.fmaruejol.ardoise.data

import android.app.Application
import app.cash.turbine.test
import io.github.fmaruejol.ardoise.core.model.Balance
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.Reimbursement
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BalanceRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val repository = BalanceRepository(api, preferences, cache)

    @Test
    fun `serves the balances it already has before asking the server`() = runTest(dispatcher) {
        api.listBalancesResult = SpliitResult.Success(
            GroupBalances(listOf(Balance("p1", 0, 0, 6000)), emptyList()),
        )
        repository.balances("g1").test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        // Offline, the balances screen still has numbers on it.
        api.listBalancesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        repository.balances("g1").test(timeout = TURBINE_TIMEOUT) {
            val cached = (awaitItem() as SpliitResult.Success).value
            assertEquals(6000L, cached.balances.single().total)
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `keeps the suggested payments in the order they were given`() = runTest(dispatcher) {
        api.listBalancesResult = SpliitResult.Success(
            GroupBalances(
                balances = emptyList(),
                reimbursements = listOf(
                    Reimbursement("p2", "p1", 6000),
                    Reimbursement("p3", "p1", 3000),
                ),
            ),
        )

        repository.balances("g1").test(timeout = TURBINE_TIMEOUT) {
            val fresh = (awaitItem() as SpliitResult.Success).value
            // They are a chain, each assumes the ones above it have been
            // made, so their order is part of the answer, not a detail.
            assertEquals(listOf("p2", "p3"), fresh.reimbursements.map { it.fromParticipantId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not serve one group's balances for another`() = runTest(dispatcher) {
        api.listBalancesResult = SpliitResult.Success(
            GroupBalances(listOf(Balance("p1", 0, 0, 6000)), emptyList()),
        )
        repository.balances("g1").test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        api.listBalancesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        repository.balances("other").test(timeout = TURBINE_TIMEOUT) {
            // Another group's rows are not this group's cache, so there is
            // nothing to serve and the failure is the answer.
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- the cross-group summary -------------------------------------------

    @Test
    fun `asks for nothing when the user knows no groups`() = runTest(dispatcher) {
        assertEquals(SpliitResult.Success(emptyList<Any>()), repository.userBalances().first())
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `leaves out groups where the user has not said who they are`() = runTest(dispatcher) {
        preferences.rememberGroup("identified")
        preferences.rememberGroup("anonymous")
        preferences.setActiveParticipantId("identified", "p1")

        repository.userBalances().first()

        // Guessing a participant would show someone else's balance as the
        // user's own, so an unidentified group is simply left out.
        assertEquals(
            listOf(listOf("identified" to "p1")),
            api.callsTo("balancesForUser").single().arguments,
        )
    }

    @Test
    fun `asks for nothing when no group has a participant chosen`() = runTest(dispatcher) {
        preferences.rememberGroup("anonymous")

        assertEquals(SpliitResult.Success(emptyList<Any>()), repository.userBalances().first())
        assertTrue(api.callsTo("balancesForUser").isEmpty())
    }

    @Test
    fun `re-emits once the user says who they are`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.balancesForUserResult = SpliitResult.Success(listOf(userBalance("g1")))

        repository.userBalances().test(timeout = TURBINE_TIMEOUT) {
            assertEquals(SpliitResult.Success(emptyList<Any>()), awaitItem())

            preferences.setActiveParticipantId("g1", "p1")

            assertEquals(1, (awaitItem() as SpliitResult.Success).value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits when a new group is remembered`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        preferences.setActiveParticipantId("g1", "p1")

        repository.userBalances().test(timeout = TURBINE_TIMEOUT) {
            awaitItem()

            // Identify first, so remembering the group produces one emission.
            preferences.setActiveParticipantId("g2", "p9")
            preferences.rememberGroup("g2")

            awaitItem()
            assertEquals(
                listOf("g2" to "p9", "g1" to "p1"),
                api.callsTo("balancesForUser").last().arguments[0],
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits when somebody says who they are`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")

        repository.userBalances().test(timeout = TURBINE_TIMEOUT) {
            // Nobody is identified, so there is nothing to ask about.
            assertEquals(SpliitResult.Success(emptyList<Any>()), awaitItem())

            preferences.setActiveParticipantId("g1", "p1")

            // The cross-group summary is the one read that is *not* cached: it
            // spans every group at once, so there is no single group's rows to
            // serve it from, and nothing on screen depends on it offline.
            awaitItem()
            assertEquals(1, api.callsTo("balancesForUser").size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `asks again when an expense has moved a balance`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        preferences.setActiveParticipantId("g1", "p1")
        api.balancesForUserResult = SpliitResult.Success(listOf(userBalance("g1")))
        api.listBalancesResult = SpliitResult.Success(
            GroupBalances(listOf(Balance("p1", 0, 0, 6000)), emptyList()),
        )

        repository.userBalances().test(timeout = TURBINE_TIMEOUT) {
            awaitItem()
            assertEquals(1, api.callsTo("balancesForUser").size)

            // What an expense change does: the server recomputes the group's
            // balances and the cache takes them.
            cache.refreshBalances("g1")

            awaitItem()
            assertEquals(2, api.callsTo("balancesForUser").size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun userBalance(groupId: String) = UserGroupBalance(
        groupId = groupId,
        groupName = groupId,
        currencySymbol = "€",
        currencyCode = "EUR",
        participantId = "p1",
        participantName = "Ada",
        amount = 1500,
    )
}
