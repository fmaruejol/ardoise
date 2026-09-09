package io.github.fmaruejol.ardoise.data

import android.app.Application
import app.cash.turbine.test
import io.github.fmaruejol.ardoise.api.GroupInput
import io.github.fmaruejol.ardoise.api.ParticipantInput
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
class GroupRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val repository = GroupRepository(api, preferences, cache, outbox)

    private val groupInput = GroupInput(
        name = "Trip",
        currencySymbol = "€",
        currencyCode = "EUR",
        participants = listOf(ParticipantInput(name = "Ada")),
    )

    // --- reads -------------------------------------------------------------

    @Test
    fun `asks for nothing when the user knows no groups`() = runTest(dispatcher) {
        val result = repository.groups().first()

        assertEquals(SpliitResult.Success(emptyList<Any>()), result)
        // An empty list is the answer; a round trip to confirm it is waste.
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `returns groups in the order they were last opened, not the server's`() =
        runTest(dispatcher) {
            preferences.rememberGroup("older")
            preferences.rememberGroup("newer")
            api.listGroupsResult = SpliitResult.Success(
                listOf(groupSummary("older"), groupSummary("newer")),
            )

            repository.groups().test(timeout = TURBINE_TIMEOUT) {
                // Nothing is cached, so the fetched list is the first thing
                // said.
                val groups = (awaitItem() as SpliitResult.Success).value
                assertEquals(listOf("newer", "older"), groups.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `serves the groups it already has before asking the server`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))
        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        // A cold read with no network: the list is still there, which is the
        // whole point of the cache.
        api.listGroupsResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            assertEquals(listOf("g1"), (awaitItem() as SpliitResult.Success).value.map { it.id })
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `drops a group the server no longer has`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))
        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        // Deleted by somebody else. Stale is one thing; gone is another, and
        // the cache must not keep showing it.
        api.listGroupsResult = SpliitResult.Success(emptyList())
        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            // The cached group, and then its absence once the server has been
            // asked.
            assertEquals(listOf("g1"), (awaitItem() as SpliitResult.Success).value.map { it.id })
            assertEquals(emptyList<Any>(), (awaitItem() as SpliitResult.Success).value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits when a group is remembered`() = runTest(dispatcher) {
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))

        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            assertEquals(SpliitResult.Success(emptyList<Any>()), awaitItem())

            repository.remember("g1")

            val groups = (awaitItem() as SpliitResult.Success).value
            assertEquals(listOf("g1"), groups.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits when a group is forgotten`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Success(listOf(groupSummary("g1")))

        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            assertEquals(1, (awaitItem() as SpliitResult.Success).value.size)

            repository.forget("g1")

            assertEquals(SpliitResult.Success(emptyList<Any>()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `asks the server again on every read`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")

        repeat(2) {
            repository.groups().test(timeout = TURBINE_TIMEOUT) {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

        // There is no refresh method any more: a read *is* a refresh, and the
        // "Try again" button subscribes again. Nothing announces staleness.
        assertEquals(2, api.callsTo("listGroups").size)
    }

    @Test
    fun `passes a failure through rather than throwing`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.listGroupsResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))

        repository.groups().test(timeout = TURBINE_TIMEOUT) {
            // Nothing has ever been fetched, so there is nothing to show under
            // a banner and the failure is the whole answer.
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reports an unknown group id as no group rather than an error`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(null)

        assertEquals(SpliitResult.Success(null), repository.group("gone").first())
    }

    @Test
    fun `serves a group it already has before asking the server`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("g1"))
        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        api.groupResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            val cached = (awaitItem() as SpliitResult.Success).value
            assertEquals("g1", cached?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a group the server has deleted leaves the cache`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("g1"))
        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        api.groupResult = SpliitResult.Success(null)
        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            // The cached group, and then its absence.
            skipItems(1)
            assertEquals(SpliitResult.Success(null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forgetting a group takes its cached contents with it`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        api.groupResult = SpliitResult.Success(group("g1"))
        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)
            cancelAndIgnoreRemainingEvents()
        }

        repository.forget("g1")

        // Keeping it would be holding a group's contents after the user asked
        // to be rid of it.
        api.groupResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            // Nothing cached and nothing fetched: the failure, not a group.
            assertTrue(awaitItem() is SpliitResult.Failure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updating a group re-emits it without anything being told`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("g1"))

        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            skipItems(1)

            api.groupResult = SpliitResult.Success(group("g1").copy(name = "Renamed"))
            repository.update("g1", groupInput)
            advanceUntilIdle()

            // The update writes the group row; Room wakes every query over it.
            // That is what replaced the change bus.
            assertEquals("Renamed", (awaitItem() as SpliitResult.Success).value?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- mutations ---------------------------------------------------------

    @Test
    fun `remembers a group it just created`() = runTest(dispatcher) {
        api.createGroupResult = SpliitResult.Success("g9")

        val result = repository.create(groupInput)

        assertEquals(SpliitResult.Success("g9"), result)
        // The id is the only way back into the group; losing it loses access.
        assertEquals(listOf("g9"), preferences.knownGroupIds.first())
    }

    @Test
    fun `does not remember a group it failed to create`() = runTest(dispatcher) {
        api.createGroupResult =
            SpliitResult.Failure(SpliitError.Procedure("BAD_REQUEST", "min2"))

        repository.create(groupInput)

        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
    }

    @Test
    fun `attributes an update to the participant the user says they are`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        preferences.setActiveParticipantId("g1", "p2")

        repository.update("g1", groupInput)

        assertEquals(listOf("g1", groupInput, "p2"), api.callsTo("updateGroup").single().arguments)
    }

    @Test
    fun `sends no participant when the user has not said who they are`() = runTest(dispatcher) {
        repository.update("g1", groupInput)

        assertEquals(null, api.callsTo("updateGroup").single().arguments[2])
    }

    @Test
    fun `makes the group stale after a successful update`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("g1"))

        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()

            api.groupResult = SpliitResult.Success(group("g1").copy(name = "Renamed"))
            repository.update("g1", groupInput)
            advanceUntilIdle()

            // Fetched again and written back, which is what wakes the read.
            assertEquals("Renamed", (awaitItem() as SpliitResult.Success).value?.name)
            assertEquals(2, api.callsTo("getGroup").size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leaves the group alone after a failed update`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("g1"))
        api.updateGroupResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))

        repository.group("g1").test(timeout = TURBINE_TIMEOUT) {
            awaitItem()
            repository.update("g1", groupInput)
            advanceUntilIdle()
            expectNoEvents()
            assertEquals(1, api.callsTo("getGroup").size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `forgetting a group also forgets who the user was in it`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        repository.setActiveParticipant("g1", "p1")

        repository.forget("g1")

        assertEquals(null, repository.activeParticipantId("g1").first())
    }
}
