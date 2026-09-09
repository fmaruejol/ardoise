package io.github.fmaruejol.ardoise.ui.join

import android.app.Application
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
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
class JoinGroupViewModelTest {
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

    private fun viewModel() = JoinGroupViewModel(repository)

    @Test
    fun `joins a group from a pasted link`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("abc123"))
        val viewModel = viewModel()

        viewModel.onLinkChange("https://spliit.app/groups/abc123/expenses")
        viewModel.onJoin()
        advanceUntilIdle()

        assertEquals("abc123", viewModel.state.value.joinedGroupId)
        assertEquals(listOf("abc123"), preferences.knownGroupIds.first())
    }

    @Test
    fun `rejects text that is not a group link without asking the server`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onLinkChange("my holiday group")
        viewModel.onJoin()
        advanceUntilIdle()

        assertEquals(JoinError.NotALink, viewModel.state.value.error)
        assertTrue(api.callsTo("getGroup").isEmpty())
        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
    }

    @Test
    fun `does not keep a link this server knows nothing about`() = runTest(dispatcher) {
        // The common mistake: a spliit.app link pasted into a self-hosted app.
        api.groupResult = SpliitResult.Success(null)
        val viewModel = viewModel()

        viewModel.onLinkChange("https://spliit.app/groups/abc123")
        viewModel.onJoin()
        advanceUntilIdle()

        // Keeping it would put an id in the list that never resolves, which
        // looks like the app swallowed the link.
        assertEquals(JoinError.NotFound, viewModel.state.value.error)
        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
    }

    @Test
    fun `reports a lookup that failed and stays on the screen`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()

        viewModel.onLinkChange("abc123")
        viewModel.onJoin()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error is JoinError.Failed)
        assertFalse(viewModel.state.value.isJoining)
        assertNull(viewModel.state.value.joinedGroupId)
        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
    }

    @Test
    fun `clears the error as soon as the link is edited`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onLinkChange("nonsense text")
        viewModel.onJoin()
        advanceUntilIdle()

        viewModel.onLinkChange("abc123")

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `ignores a second tap while the first is still checking`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("abc123"))
        val viewModel = viewModel()

        viewModel.onLinkChange("abc123")
        viewModel.onJoin()
        viewModel.onJoin()
        advanceUntilIdle()

        assertEquals(1, api.callsTo("getGroup").size)
    }

    // --- scanning a QR code ------------------------------------------------

    @Test
    fun `joins straight from a scanned QR code`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("abc123"))
        val viewModel = viewModel()

        // What Spliit puts in a group QR code is the group URL.
        viewModel.onScanned("https://spliit.app/groups/abc123")
        advanceUntilIdle()

        // Scanning is an explicit "add this group"; a second tap would be
        // asking the user to confirm what they just pointed a camera at.
        assertEquals("abc123", viewModel.state.value.joinedGroupId)
        assertEquals(listOf("abc123"), preferences.knownGroupIds.first())
    }

    @Test
    fun `shows what was scanned when the code is not a Spliit link`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onScanned("https://example.com/wifi-password")
        advanceUntilIdle()

        // Seeing the text beats being told only that it did not work.
        assertEquals("https://example.com/wifi-password", viewModel.state.value.link)
        assertEquals(JoinError.NotALink, viewModel.state.value.error)
    }

    @Test
    fun `ignores a scan that lands while a join is already running`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("abc123"))
        val viewModel = viewModel()

        viewModel.onLinkChange("abc123")
        viewModel.onJoin()
        viewModel.onScanned("https://spliit.app/groups/other999")
        advanceUntilIdle()

        assertEquals(1, api.callsTo("getGroup").size)
        assertEquals(listOf("abc123"), preferences.knownGroupIds.first())
    }

    @Test
    fun `does not navigate twice for one join`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("abc123"))
        val viewModel = viewModel()
        viewModel.onScanned("abc123")
        advanceUntilIdle()

        viewModel.onNavigationHandled()

        assertNull(viewModel.state.value.joinedGroupId)
    }

    // --- the scanner with the whole screen ---------------------------------

    @Test
    fun `opens and closes the full screen scanner`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onFullScreenOpen()
        assertTrue(viewModel.state.value.scanningFullScreen)

        viewModel.onFullScreenClose()
        // Closing leaves the typed link exactly as it was, which is why this
        // is a state of the screen rather than a place to navigate to.
        assertFalse(viewModel.state.value.scanningFullScreen)
    }

    @Test
    fun `reading a code closes the scanner`() = runTest(dispatcher) {
        api.groupResult = SpliitResult.Success(group("abc123"))
        val viewModel = viewModel()
        viewModel.onFullScreenOpen()

        viewModel.onScanned("https://spliit.app/groups/abc123")
        advanceUntilIdle()

        // Leaving the camera up over a join already under way would invite a
        // second scan of the same code.
        assertFalse(viewModel.state.value.scanningFullScreen)
        assertEquals("abc123", viewModel.state.value.joinedGroupId)
    }

    @Test
    fun `keeps what was scanned when the code is not a link`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onFullScreenOpen()

        viewModel.onScanned("just some text")
        advanceUntilIdle()

        // The scanner closes either way: the reason is on the screen behind
        // it, next to the field now holding what was actually read.
        assertFalse(viewModel.state.value.scanningFullScreen)
        assertEquals("just some text", viewModel.state.value.link)
    }
}
