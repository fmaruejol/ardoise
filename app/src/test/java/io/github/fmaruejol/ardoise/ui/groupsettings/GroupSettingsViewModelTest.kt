package io.github.fmaruejol.ardoise.ui.groupsettings

import android.app.Application
import io.github.fmaruejol.ardoise.api.GroupInput
import io.github.fmaruejol.ardoise.api.SpliitApiFactory
import io.github.fmaruejol.ardoise.core.model.GroupDetails
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.ServerRepository
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
import org.junit.Assert.assertNotNull
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
class GroupSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val repository = GroupRepository(api, preferences, cache, outbox)
    private val serverRepository = ServerRepository(preferences, SpliitApiFactory { api })

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        api.groupDetailsResult = SpliitResult.Success(
            GroupDetails(
                group = group("abc123", listOf(ana, ben)).copy(
                    name = "Lisbon trip",
                    information = "Kitty is €20 each.",
                ),
                participantIdsWithExpenses = emptyList(),
            ),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GroupSettingsViewModel("abc123", repository, serverRepository)

    @Test
    fun `shows the group as the server has it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Lisbon trip", state.name)
        assertEquals("Kitty is €20 each.", state.information)
        assertEquals(listOf("Ana", "Ben"), state.participants.map { it.name })
    }

    @Test
    fun `builds the invite link from the group and the server`() = runTest(dispatcher) {
        preferences.setBaseUrl("https://spliit.example.com")
        val viewModel = viewModel()
        advanceUntilIdle()

        // This is what the QR encodes, and what the join screen scans.
        assertEquals("https://spliit.example.com/groups/abc123", viewModel.state.value.inviteUrl)
    }

    // --- which one is you --------------------------------------------------

    @Test
    fun `nobody is you until you say so`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value.activeParticipantName)
    }

    @Test
    fun `records who you are without telling the server`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPickYouOpen()
        viewModel.onYouChange(1)
        advanceUntilIdle()

        assertEquals("p2", preferences.activeParticipantId("abc123").first())
        assertEquals("Ben", viewModel.state.value.activeParticipantName)
        // Spliit has no accounts, so there is nobody to tell: this must not
        // reach the server at all.
        assertTrue(api.callsTo("updateGroup").isEmpty())
    }

    @Test
    fun `saves the choice straight away rather than on Save`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onYouChange(0)
        advanceUntilIdle()

        // It never leaves the device, so there is nothing that could fail and
        // nothing to roll back, waiting for Save would only be a way to lose
        // it.
        assertEquals("p1", preferences.activeParticipantId("abc123").first())
    }

    @Test
    fun `ignores a pick of someone the server has never seen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onAddParticipant()

        viewModel.onYouChange(2)
        advanceUntilIdle()

        // A participant added but not yet saved has no id to record.
        assertNull(preferences.activeParticipantId("abc123").first())
    }

    // --- editing the group -------------------------------------------------

    @Test
    fun `saves the name, information and participants`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNameChange("Lisbon 2026")
        viewModel.onInformationChange("Rua da Bica 14")
        viewModel.onAddParticipant()
        viewModel.onParticipantNameChange(2, "Chloé")
        viewModel.onSave()
        advanceUntilIdle()

        val input = api.callsTo("updateGroup").single().arguments[1] as GroupInput
        assertEquals("Lisbon 2026", input.name)
        assertEquals("Rua da Bica 14", input.information)
        assertEquals(listOf("Ana", "Ben", "Chloé"), input.participants.map { it.name })
        // Existing participants keep their ids; a new one has none yet.
        assertEquals(listOf("p1", "p2", null), input.participants.map { it.id })
    }

    @Test
    fun `carries the currency through untouched`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNameChange("Lisbon 2026")
        viewModel.onSave()
        advanceUntilIdle()

        // groups.update takes the whole group, and the ISO code is what
        // decides the scale of every amount in it.
        val input = api.callsTo("updateGroup").single().arguments[1] as GroupInput
        assertEquals("EUR", input.currencyCode)
        assertEquals("€", input.currencySymbol)
    }

    @Test
    fun `refuses to remove someone who appears on an expense`() = runTest(dispatcher) {
        api.groupDetailsResult = SpliitResult.Success(
            GroupDetails(
                group = group("abc123", listOf(ana, ben)),
                participantIdsWithExpenses = listOf("p2"),
            ),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRemoveParticipant(1)

        // Their share of those expenses has to go somewhere first, so the
        // server refuses too, better to say why than to relay a BAD_REQUEST.
        assertEquals("Ben", viewModel.state.value.blockedRemoval)
        assertEquals(listOf("Ana", "Ben"), viewModel.state.value.participants.map { it.name })
    }

    @Test
    fun `removes a participant who has no expenses`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRemoveParticipant(1)

        assertNull(viewModel.state.value.blockedRemoval)
        assertEquals(listOf("Ana"), viewModel.state.value.participants.map { it.name })
    }

    @Test
    fun `refuses a name the server would reject`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNameChange("x")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(GroupSettingsError.Name in viewModel.state.value.saveErrors)
        assertTrue(api.callsTo("updateGroup").isEmpty())
    }

    @Test
    fun `reports a save that failed`() = runTest(dispatcher) {
        api.updateGroupResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saveErrors.any { it is GroupSettingsError.Failed })
    }

    // --- the group going away ----------------------------------------------

    @Test
    fun `asks before removing the group from this device`() = runTest(dispatcher) {
        preferences.rememberGroup("abc123")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRemoveGroupClick()

        assertTrue(viewModel.state.value.confirmingRemoval)
        assertEquals(listOf("abc123"), preferences.knownGroupIds.first())
    }

    @Test
    fun `removes the group once confirmed`() = runTest(dispatcher) {
        preferences.rememberGroup("abc123")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRemoveGroupClick()
        viewModel.onRemoveGroupConfirm()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
        assertTrue(viewModel.state.value.isRemoved)
    }

    @Test
    fun `says so when the group is gone from the server`() = runTest(dispatcher) {
        api.groupDetailsResult = SpliitResult.Failure(
            SpliitError.NotFound,
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        // Being deleted is a state to render, not an error to apologise for.
        assertTrue(viewModel.state.value.notFound)
        assertNull(viewModel.state.value.loadError)
    }

    // --- which row is at fault ---------------------------------------------

    @Test
    fun `marks the participant whose name the server would reject`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Ana at 0, Ben at 1; shorten the second one.
        viewModel.onParticipantNameChange(1, "x")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(GroupSettingsError.ParticipantName(1) in viewModel.state.value.saveErrors)
        assertTrue(api.callsTo("updateGroup").isEmpty())
    }

    @Test
    fun `marks the duplicate rather than the original`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onParticipantNameChange(1, "ana")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(GroupSettingsError.DuplicateParticipant(1) in viewModel.state.value.saveErrors)
    }

    // --- a failed load, and the way back from it ----------------------------

    @Test
    fun `a failed load can be retried, and fills the form in`() = runTest(dispatcher) {
        api.groupDetailsResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.loadError)
        assertEquals("", viewModel.state.value.name)

        api.groupDetailsResult = SpliitResult.Success(
            GroupDetails(
                group = group("abc123", listOf(ana, ben)).copy(name = "Lisbon trip"),
                participantIdsWithExpenses = emptyList(),
            ),
        )
        viewModel.onRetry()
        advanceUntilIdle()

        // `groups.getDetails` is a one-shot read and deliberately uncached, so
        // without a retry a single dropped request stranded the user until
        // they left the group and came back.
        val state = viewModel.state.value
        assertNull(state.loadError)
        // The seeding guard is "not yet loaded", not "still loading": a retry
        // arrives with isLoading already false, and keying on that left the
        // form blank behind a screen that said it had succeeded.
        assertEquals("Lisbon trip", state.name)
        assertEquals(listOf("Ana", "Ben"), state.participants.map { it.name })
    }

    @Test
    fun `a re-emission does not overwrite what is being typed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNameChange("Porto trip")
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals("Porto trip", viewModel.state.value.name)
    }
}
