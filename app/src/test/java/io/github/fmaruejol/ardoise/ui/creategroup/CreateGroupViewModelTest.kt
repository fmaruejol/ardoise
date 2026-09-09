package io.github.fmaruejol.ardoise.ui.creategroup

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.github.fmaruejol.ardoise.api.GroupInput
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.group
import io.github.fmaruejol.ardoise.data.testCache
import io.github.fmaruejol.ardoise.data.testOutbox
import io.github.fmaruejol.ardoise.ui.throughProcessDeath
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
class CreateGroupViewModelTest {
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

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        CreateGroupViewModel(repository, savedState)

    private fun fill(viewModel: CreateGroupViewModel, vararg participants: String) {
        viewModel.onNameChange("Lisbon trip")
        participants.forEachIndexed { index, name ->
            if (index > 0) viewModel.onAddParticipant()
            viewModel.onParticipantChange(index, name)
        }
    }

    @Test
    fun `creates the group and remembers its id`() = runTest(dispatcher) {
        api.createGroupResult = SpliitResult.Success("abc123")
        val viewModel = viewModel()

        fill(viewModel, "Ana", "Ben")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("abc123", viewModel.state.value.createdGroupId)
        // The id is the only way back into the group; a create that forgot it
        // would strand the user outside their own group.
        assertEquals(listOf("abc123"), preferences.knownGroupIds.first())
    }

    @Test
    fun `sends the name, currency and participants`() = runTest(dispatcher) {
        val viewModel = viewModel()

        fill(viewModel, "Ana", "Ben")
        viewModel.onCurrencyChange("JPY")
        viewModel.onSave()
        advanceUntilIdle()

        val input = api.callsTo("createGroup").single().arguments.single() as GroupInput
        assertEquals("Lisbon trip", input.name)
        assertEquals("JPY", input.currencyCode)
        assertEquals(listOf("Ana", "Ben"), input.participants.map { it.name })
    }

    @Test
    fun `sends the group information when there is any`() = runTest(dispatcher) {
        val viewModel = viewModel()

        fill(viewModel, "Ana")
        viewModel.onInformationChange("  Rua da Bica 14  ")
        viewModel.onSave()
        advanceUntilIdle()

        val input = api.callsTo("createGroup").single().arguments.single() as GroupInput
        assertEquals("Rua da Bica 14", input.information)
    }

    @Test
    fun `sends no information rather than an empty one`() = runTest(dispatcher) {
        val viewModel = viewModel()

        fill(viewModel, "Ana")
        viewModel.onSave()
        advanceUntilIdle()

        val input = api.callsTo("createGroup").single().arguments.single() as GroupInput
        assertNull(input.information)
    }

    // --- which one is you --------------------------------------------------

    @Test
    fun `marks the chosen participant, not merely the first`() = runTest(dispatcher) {
        api.createGroupResult = SpliitResult.Success("abc123")
        api.groupResult = SpliitResult.Success(
            group("abc123", listOf(Participant("p1", "Ana"), Participant("p2", "Ben"))),
        )
        val viewModel = viewModel()

        fill(viewModel, "Ana", "Ben")
        viewModel.onYouChange(1)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("p2", preferences.activeParticipantId("abc123").first())
    }

    @Test
    fun `the choice follows a row removed above it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fill(viewModel, "Ana", "Ben", "Chloé")
        viewModel.onYouChange(2)

        viewModel.onRemoveParticipant(0)

        // The selection is an index, so it has to move with the list or it
        // would silently come to mean somebody else.
        assertEquals("Chloé", viewModel.state.value.youName)
    }

    @Test
    fun `removing the chosen row falls back to the first`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fill(viewModel, "Ana", "Ben")
        viewModel.onYouChange(1)

        viewModel.onRemoveParticipant(1)

        assertEquals("Ana", viewModel.state.value.youName)
    }

    @Test
    fun `refuses to create a group whose chosen participant has no name`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            fill(viewModel, "Ana", "")
            viewModel.onYouChange(1)

            viewModel.onSave()
            advanceUntilIdle()

            // Blank rows are dropped, so the choice would otherwise point at
            // nobody once the group existed.
            assertTrue(CreateGroupError.You in viewModel.state.value.errors)
            assertTrue(api.callsTo("createGroup").isEmpty())
        }

    @Test
    fun `marks the first participant as the user`() = runTest(dispatcher) {
        api.createGroupResult = SpliitResult.Success("abc123")
        api.groupResult = SpliitResult.Success(
            group("abc123", listOf(Participant("p1", "Ana"), Participant("p2", "Ben"))),
        )
        val viewModel = viewModel()

        fill(viewModel, "Ana", "Ben")
        viewModel.onSave()
        advanceUntilIdle()

        // This is the only moment it is knowable, and it is what makes
        // "you owe / you are owed" mean anything later.
        assertEquals("p1", preferences.activeParticipantId("abc123").first())
    }

    @Test
    fun `creates the group even when the participant cannot be matched back`() =
        runTest(dispatcher) {
            api.createGroupResult = SpliitResult.Success("abc123")
            api.groupResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
            val viewModel = viewModel()

            fill(viewModel, "Ana")
            viewModel.onSave()
            advanceUntilIdle()

            // The group exists; failing here costs a tap inside it, not the group.
            assertEquals("abc123", viewModel.state.value.createdGroupId)
            assertNull(preferences.activeParticipantId("abc123").first())
        }

    // --- what the server would reject, said in words instead ---------------

    @Test
    fun `refuses a name the server would reject`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onNameChange("x")
        viewModel.onParticipantChange(0, "Ana")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(CreateGroupError.Name in viewModel.state.value.errors)
        assertTrue(api.callsTo("createGroup").isEmpty())
    }

    @Test
    fun `refuses a group with no one in it`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onNameChange("Lisbon trip")
        viewModel.onSave()
        advanceUntilIdle()

        // The first row is where a name would go, so that is the one marked.
        assertTrue(CreateGroupError.ParticipantName(0) in viewModel.state.value.errors)
        assertTrue(api.callsTo("createGroup").isEmpty())
    }

    @Test
    fun `refuses two participants with the same name`() = runTest(dispatcher) {
        val viewModel = viewModel()

        fill(viewModel, "Ana", "ana")
        viewModel.onSave()
        advanceUntilIdle()

        // The second of the pair: the one the user just typed.
        assertTrue(CreateGroupError.DuplicateParticipant(1) in viewModel.state.value.errors)
        assertTrue(api.callsTo("createGroup").isEmpty())
    }

    @Test
    fun `ignores blank rows rather than refusing them`() = runTest(dispatcher) {
        val viewModel = viewModel()

        fill(viewModel, "Ana", "")
        viewModel.onSave()
        advanceUntilIdle()

        // An empty row is a row not filled in yet, not an error to report.
        val input = api.callsTo("createGroup").single().arguments.single() as GroupInput
        assertEquals(listOf("Ana"), input.participants.map { it.name })
    }

    @Test
    fun `keeps the first participant when a row is removed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fill(viewModel, "Ana", "Ben")

        viewModel.onRemoveParticipant(0)

        // The first row is the user, and a group needs at least one person.
        assertEquals(listOf("Ana", "Ben"), viewModel.state.value.participants)
    }

    @Test
    fun `reports a create that failed`() = runTest(dispatcher) {
        api.createGroupResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()

        fill(viewModel, "Ana")
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.errors.any { it is CreateGroupError.Failed })
        assertNull(viewModel.state.value.createdGroupId)
    }

    @Test
    fun `ignores a second save while the first is still running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fill(viewModel, "Ana")

        viewModel.onSave()
        viewModel.onSave()
        advanceUntilIdle()

        // Mutations are not idempotent: a second create makes a second group.
        assertEquals(1, api.callsTo("createGroup").size)
    }

    // --- the currency the form opens on ------------------------------------

    @Test
    fun `opens on the device's currency before anything is chosen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Robolectric runs en-US, so this is USD rather than the euros the
        // form used to be pinned to.
        assertEquals(GroupCurrency.defaultCodeFor(), viewModel.state.value.currencyCode)
    }

    @Test
    fun `opens on the currency chosen in settings`() = runTest(dispatcher) {
        preferences.setDefaultCurrencyCode("JPY")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("JPY", viewModel.state.value.currencyCode)
    }

    @Test
    fun `saves the group in the currency the form was opened on`() = runTest(dispatcher) {
        preferences.setDefaultCurrencyCode("SEK")
        val viewModel = viewModel()
        advanceUntilIdle()
        fill(viewModel, "Ana")

        viewModel.onSave()
        advanceUntilIdle()

        val input = api.callsTo("createGroup").single().arguments[0] as GroupInput
        assertEquals("SEK", input.currencyCode)
    }

    @Test
    fun `a currency the user picked is not overwritten by the stored one`() =
        runTest(dispatcher) {
            preferences.setDefaultCurrencyCode("JPY")

            val viewModel = viewModel()
            // Before the stored answer has had a chance to arrive.
            viewModel.onCurrencyChange("GBP")
            advanceUntilIdle()

            // The preference is what the form opens on, not what it is pinned
            // to, and an answer still in flight must not undo a choice.
            assertEquals("GBP", viewModel.state.value.currencyCode)
        }

    @Test
    fun `choosing a currency here does not change the default`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCurrencyChange("GBP")
        advanceUntilIdle()

        // One group in pounds says nothing about the next one, and the
        // setting is the user's to change in settings.
        assertEquals(
            GroupCurrency.defaultCodeFor(),
            preferences.defaultCurrencyCode.first(),
        )
    }

    // --- which row is at fault ---------------------------------------------

    @Test
    fun `marks the row the bad name is actually in`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onNameChange("Lisbon trip")
        viewModel.onParticipantChange(0, "Ana")
        viewModel.onAddParticipant()
        // A blank row in the middle: legal, and dropped before saving.
        viewModel.onAddParticipant()
        viewModel.onParticipantChange(2, "x")

        viewModel.onSave()
        advanceUntilIdle()

        // Index 2 as typed, not index 1 as the filtered list would number it.
        assertTrue(CreateGroupError.ParticipantName(2) in viewModel.state.value.errors)
    }

    @Test
    fun `marks the duplicate rather than the original`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onNameChange("Lisbon trip")
        fill(viewModel, "Ana", "Ben", "ana")

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(CreateGroupError.DuplicateParticipant(2) in viewModel.state.value.errors)
    }

    @Test
    fun `marks every bad row and the group name together`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onNameChange("x")
        viewModel.onParticipantChange(0, "Ana")
        viewModel.onAddParticipant()
        viewModel.onParticipantChange(1, "y")
        viewModel.onAddParticipant()
        viewModel.onParticipantChange(2, "ana")

        viewModel.onSave()
        advanceUntilIdle()

        // The name, the too-short row, the duplicate, and "which one is you",
        // which cannot resolve while a name it might point at is invalid.
        assertEquals(
            setOf(
                CreateGroupError.Name,
                CreateGroupError.ParticipantName(1),
                CreateGroupError.DuplicateParticipant(2),
            ),
            viewModel.state.value.errors - CreateGroupError.You,
        )
    }

    @Test
    fun `renaming a row clears that row and leaves the rest`() = runTest(dispatcher) {
        val viewModel = viewModel()
        // `fill` sets a valid group name, so shorten it afterwards.
        fill(viewModel, "Ana", "y")
        viewModel.onNameChange("x")
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onParticipantChange(1, "Ben")

        assertTrue(CreateGroupError.ParticipantName(1) !in viewModel.state.value.errors)
        assertTrue(CreateGroupError.Name in viewModel.state.value.errors)
    }

    @Test
    fun `removing a row drops the marks that pointed at rows`() = runTest(dispatcher) {
        val viewModel = viewModel()
        // `fill` sets a valid group name, so shorten it afterwards.
        fill(viewModel, "Ana", "y")
        viewModel.onNameChange("x")
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onRemoveParticipant(1)

        // Every row under the removed one has moved, so a mark by index would
        // now be pointing at somebody else.
        assertTrue(viewModel.state.value.errors.none { it.participantIndex != null })
        assertTrue(CreateGroupError.Name in viewModel.state.value.errors)
    }

    @Test
    fun `points at the topmost row that is wrong`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fill(viewModel, "Ana", "y", "z")

        viewModel.onSave()
        advanceUntilIdle()

        // Two bad rows: the form goes back to the first of them, not to
        // whichever the set happened to hold first.
        assertEquals(CreateGroupError.ParticipantName(1), viewModel.state.value.firstError)
    }

    @Test
    fun `the group name outranks the rows below it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        fill(viewModel, "Ana", "y")
        viewModel.onNameChange("x")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(CreateGroupError.Name, viewModel.state.value.firstError)
    }

    @Test
    fun `counts the rows the user added, so the screen can focus one`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onAddParticipant()
            viewModel.onAddParticipant()
            advanceUntilIdle()

            // A count, not an index: two blank rows look exactly like one, so
            // only this says an add happened at all.
            assertEquals(2, viewModel.state.value.participantsAdded)
        }

    @Test
    fun `a half-typed group comes back`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = viewModel(saved)
        fill(before, "Ana", "Ben")
        before.onInformationChange("Flat 3, back before Sunday")
        before.onYouChange(1)
        advanceUntilIdle()

        val after = viewModel(saved.throughProcessDeath())
        advanceUntilIdle()

        val state = after.state.value
        assertEquals("Lisbon trip", state.name)
        assertEquals(listOf("Ana", "Ben"), state.participants)
        assertEquals("Flat 3, back before Sunday", state.information)
        assertEquals(1, state.youIndex)
    }

    @Test
    fun `a chosen currency survives, and the stored default does not overwrite it`() =
        runTest(dispatcher) {
            preferences.setDefaultCurrencyCode("USD")
            val saved = SavedStateHandle()
            val before = viewModel(saved)
            before.onCurrencyChange("JPY")
            advanceUntilIdle()

            val after = viewModel(saved.throughProcessDeath())
            advanceUntilIdle()

            assertEquals("JPY", after.state.value.currencyCode)
        }

    @Test
    fun `nothing typed leaves the form as it opens`() = runTest(dispatcher) {
        preferences.setDefaultCurrencyCode("USD")
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        val after = viewModel(saved.throughProcessDeath())
        advanceUntilIdle()

        assertEquals("", after.state.value.name)
        assertEquals(listOf(""), after.state.value.participants)
        assertEquals("USD", after.state.value.currencyCode)
    }

    @Test
    fun `a refused save is not restored as a complaint about a fresh form`() =
        runTest(dispatcher) {
            val saved = SavedStateHandle()
            val before = viewModel(saved)
            before.onSave()
            advanceUntilIdle()
            assertTrue(before.state.value.errors.isNotEmpty())

            val after = viewModel(saved.throughProcessDeath())
            advanceUntilIdle()

            assertEquals(emptySet<CreateGroupError>(), after.state.value.errors)
            assertEquals(0, after.state.value.refusedSaves)
        }
}
