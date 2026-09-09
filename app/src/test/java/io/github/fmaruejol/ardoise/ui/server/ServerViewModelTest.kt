package io.github.fmaruejol.ardoise.ui.server

import android.app.Application
import io.github.fmaruejol.ardoise.api.SpliitApiFactory
import io.github.fmaruejol.ardoise.core.instance.BaseUrl
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.NetworkAccess
import io.github.fmaruejol.ardoise.data.ServerRepository
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
import kotlinx.coroutines.yield
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
class ServerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)

    /** Every address is checked against the same fake, whatever it is. */
    private val apiFactory = SpliitApiFactory { api }
    private val repository = ServerRepository(preferences, apiFactory)
    private val groups = GroupRepository(api, preferences, cache, outbox)

    /** Whether Android lets the app on the network. */
    private var networkAllowed = true

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which does not exist on the JVM.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(access: NetworkAccess = NetworkAccess { networkAllowed }) =
        ServerViewModel(repository, groups, access)

    // --- defaults ----------------------------------------------------------

    @Test
    fun `starts on the project's own instance`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(InstanceChoice.Cloud, viewModel.state.value.choice)
    }

    @Test
    fun `reopens on the self-hosted address already configured`() = runTest(dispatcher) {
        preferences.setBaseUrl("https://spliit.example.com")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(InstanceChoice.SelfHosted, viewModel.state.value.choice)
        assertEquals("https://spliit.example.com/", viewModel.state.value.customUrl)
    }

    @Test
    fun `keeps the stored address even when the access check is slow`() = runTest(dispatcher) {
        // Both run from init.
        preferences.setBaseUrl("https://spliit.example.com")
        val slowAccess = NetworkAccess {
            yield()
            yield()
            networkAllowed
        }

        val viewModel = viewModel(slowAccess)
        advanceUntilIdle()

        assertEquals(InstanceChoice.SelfHosted, viewModel.state.value.choice)
        assertEquals("https://spliit.example.com/", viewModel.state.value.customUrl)
    }

    // --- saving ------------------------------------------------------------

    @Test
    fun `saves the cloud address`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(BaseUrl.CLOUD, preferences.baseUrl.first())
        assertTrue(viewModel.state.value.isSaved)
    }

    @Test
    fun `saves the cloud address without a round trip`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        // Requiring the network here would strand an offline user on this
        // screen, and the project's own address is not in doubt.
        assertTrue(api.callsTo("listCategories").isEmpty())
    }

    @Test
    fun `normalises a typed address on the way in`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("spliit.example.com")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals("https://spliit.example.com/", preferences.baseUrl.first())
    }

    @Test
    fun `refuses to save an address that is not a url`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("not a url at all")
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(BaseUrl.Reason.Malformed, viewModel.state.value.urlError)
        assertFalse(viewModel.state.value.isSaved)
    }

    @Test
    fun `refuses to save an empty address`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(BaseUrl.Reason.Empty, viewModel.state.value.urlError)
    }

    @Test
    fun `saves an address that did not answer`() = runTest(dispatcher) {
        // An instance can be perfectly correct and merely unreachable right
        // now, a home server the user is away from, a VPN that is not up.
        api.listCategoriesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://spliit.example.com")
        viewModel.onTest()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.checkOutcome is CheckOutcome.Failed)
        assertEquals("https://spliit.example.com/", preferences.baseUrl.first())
    }

    @Test
    fun `does not navigate twice for one save`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onNavigationHandled()

        assertFalse(viewModel.state.value.isSaved)
    }

    // --- testing an address ------------------------------------------------

    @Test
    fun `reports a reachable instance`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://spliit.example.com")
        viewModel.onTest()
        advanceUntilIdle()

        assertEquals(CheckOutcome.Reachable, viewModel.state.value.checkOutcome)
        // Testing alone changes nothing: saving is a separate act.
        assertEquals(BaseUrl.CLOUD, preferences.baseUrl.first())
    }

    @Test
    fun `reports something that answers but is not Spliit`() = runTest(dispatcher) {
        api.listCategoriesResult =
            SpliitResult.Failure(SpliitError.Malformed(RuntimeException("html")))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://example.com")
        viewModel.onTest()
        advanceUntilIdle()

        val outcome = viewModel.state.value.checkOutcome
        assertTrue((outcome as CheckOutcome.Failed).error is SpliitError.Malformed)
    }

    @Test
    fun `ignores a second test while the first is still running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://spliit.example.com")
        viewModel.onTest()
        viewModel.onTest()
        advanceUntilIdle()

        assertEquals(1, api.callsTo("listCategories").size)
    }

    @Test
    fun `clears the verdict as soon as the address is edited`() = runTest(dispatcher) {
        api.listCategoriesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://wrong.example.com")
        viewModel.onTest()
        advanceUntilIdle()

        viewModel.onUrlChange("https://right.example.com")

        // The verdict described text the user has already started replacing.
        assertNull(viewModel.state.value.checkOutcome)
        assertNull(viewModel.state.value.urlError)
    }

    @Test
    fun `clears the verdict when switching back to the cloud`() = runTest(dispatcher) {
        api.listCategoriesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("offline")))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://wrong.example.com")
        viewModel.onTest()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.Cloud)

        assertNull(viewModel.state.value.checkOutcome)
    }

    // --- groups belong to their server -------------------------------------

    @Test
    fun `says how many groups stay behind on the address being left`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        preferences.rememberGroup("g2")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://spliit.example.com")

        assertEquals(2, viewModel.state.value.savedGroupCount)
        assertTrue(viewModel.state.value.showSwitchNote)
    }

    @Test
    fun `says nothing about switching when the address is not changing`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        val viewModel = viewModel()
        advanceUntilIdle()

        // Still on the cloud, which is where those groups already are.
        assertFalse(viewModel.state.value.showSwitchNote)
    }

    @Test
    fun `parks the groups of the old server rather than losing them`() = runTest(dispatcher) {
        preferences.rememberGroup("g1")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://spliit.example.com")
        viewModel.onSave()
        advanceUntilIdle()

        // A group id only means anything to the server holding the group, so
        // the new server starts empty and the old list waits where it was.
        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
        assertEquals(listOf("g1"), preferences.knownGroupIds(BaseUrl.CLOUD).first())
    }

    // --- network access ----------------------------------------------------

    @Test
    fun `prompts and blocks when the app is not allowed on the network`() = runTest(dispatcher) {
        networkAllowed = false

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.networkAccessBlocked)
        assertTrue(viewModel.state.value.showPermissionPrompt)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `neither prompts nor blocks when network access is allowed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.networkAccessBlocked)
        assertFalse(viewModel.state.value.showPermissionPrompt)
    }

    @Test
    fun `will not save anything while network access is missing`() = runTest(dispatcher) {
        networkAllowed = false
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        // Even the cloud leads to an app that can do nothing.
        assertFalse(viewModel.state.value.isSaved)
    }

    @Test
    fun `dismissing the prompt hides it but keeps saving blocked`() = runTest(dispatcher) {
        networkAllowed = false
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPermissionPromptDismissed()

        assertFalse(viewModel.state.value.showPermissionPrompt)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `a dismissal does not silence the prompt forever`() = runTest(dispatcher) {
        networkAllowed = false
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPermissionPromptDismissed()

        viewModel.onPermissionPromptRequested()

        assertTrue(viewModel.state.value.showPermissionPrompt)
    }

    @Test
    fun `unblocks after a trip to Android settings`() = runTest(dispatcher) {
        networkAllowed = false
        val viewModel = viewModel()
        advanceUntilIdle()

        networkAllowed = true
        viewModel.onScreenResumed()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.networkAccessBlocked)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `blocks when access is revoked while the app is running`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // DNS is blocked too, so a revoked permission fails exactly like being
        // offline. The failure alone cannot tell them apart.
        networkAllowed = false
        api.listCategoriesResult =
            SpliitResult.Failure(SpliitError.Network(RuntimeException("unknown host")))
        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://spliit.example.com")
        viewModel.onTest()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.networkAccessBlocked)
        assertNull(viewModel.state.value.checkOutcome)
    }

    @Test
    fun `does not blame the permission for a server that answered badly`() = runTest(dispatcher) {
        api.listCategoriesResult = SpliitResult.Failure(SpliitError.Http(500, "boom"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onChoiceChange(InstanceChoice.SelfHosted)
        viewModel.onUrlChange("https://spliit.example.com")
        viewModel.onTest()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.networkAccessBlocked)
        assertTrue(viewModel.state.value.checkOutcome is CheckOutcome.Failed)
    }
}
