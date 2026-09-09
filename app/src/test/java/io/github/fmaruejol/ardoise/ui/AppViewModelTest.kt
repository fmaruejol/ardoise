package io.github.fmaruejol.ardoise.ui

import android.app.Application
import io.github.fmaruejol.ardoise.api.SpliitApiFactory
import io.github.fmaruejol.ardoise.data.AppLanguage
import io.github.fmaruejol.ardoise.data.AppTheme
import io.github.fmaruejol.ardoise.data.Connectivity
import io.github.fmaruejol.ardoise.data.ExpenseRepository
import io.github.fmaruejol.ardoise.data.FakeConnectivity
import io.github.fmaruejol.ardoise.data.FakeGroupPreferences
import io.github.fmaruejol.ardoise.data.FakeSpliitApi
import io.github.fmaruejol.ardoise.data.GroupRepository
import io.github.fmaruejol.ardoise.data.LanguageRepository
import io.github.fmaruejol.ardoise.data.ServerRepository
import io.github.fmaruejol.ardoise.data.ThemeRepository
import io.github.fmaruejol.ardoise.data.testCache
import io.github.fmaruejol.ardoise.data.testOutbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox = testOutbox(api, cache, preferences, dispatcher)
    private val serverRepository = ServerRepository(preferences, SpliitApiFactory { api })
    private val groupRepository = GroupRepository(api, preferences, cache, outbox)
    private val expenses = ExpenseRepository(api, preferences, cache, outbox)

    /** Never fires: this test is about where the app opens, not the queue. */
    private val connectivity = FakeConnectivity()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val languages = LanguageRepository(preferences)

    private val themes = ThemeRepository(preferences)

    private fun viewModel() =
        AppViewModel(serverRepository, groupRepository, languages, themes, expenses, connectivity)

    @Test
    fun `opens on the first-run screen for a new install`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(Routes.HOME, viewModel.startDestination.value)
    }

    @Test
    fun `goes straight to the groups once a server has been chosen`() = runTest(dispatcher) {
        preferences.setBaseUrl("https://spliit.example.com")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(Routes.GROUPS, viewModel.startDestination.value)
    }

    @Test
    fun `treats the cloud as a real choice, not as the default`() = runTest(dispatcher) {
        // The stored address cannot tell these apart: the default *is* the
        // cloud, so only the act of choosing counts.
        preferences.setBaseUrl("https://spliit.app/")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(Routes.GROUPS, viewModel.startDestination.value)
    }

    @Test
    fun `goes to the groups for someone who has one but never touched the server`() =
        runTest(dispatcher) {
            // Joining a group is arriving. Showing the first-run screen again
            // would hide the group they already have.
            preferences.rememberGroup("abc123")

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(Routes.GROUPS, viewModel.startDestination.value)
        }

    @Test
    fun `does not guess before it knows`() = runTest(dispatcher) {
        val viewModel = viewModel()

        // Nothing is drawn until this is non-null, so a wrong guess here would
        // flash a screen at everyone on every launch.
        assertNull(viewModel.startDestination.value)
    }

    // --- the language the app draws in -------------------------------------

    @Test
    fun `follows the device until somebody chooses otherwise`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Nothing stored means the phone's own language, which Android then
        // resolves to French or falls back to English.
        assertEquals(AppLanguage.System, viewModel.language.value)
    }

    @Test
    fun `takes a chosen language while the app is open`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        preferences.setLanguageTag("fr")
        advanceUntilIdle()

        // Observed, not read once: choosing a language changes what is on
        // screen now rather than at the next launch.
        assertEquals(AppLanguage.French, viewModel.language.value)
    }

    @Test
    fun `a language the app does not ship falls back to the device`() = runTest(dispatcher) {
        preferences.setLanguageTag("de")

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(AppLanguage.System, viewModel.language.value)
    }

    // --- the theme it draws in ---------------------------------------------

    @Test
    fun `follows the device until somebody chooses a theme`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(AppTheme.System, viewModel.theme.value)
    }

    @Test
    fun `takes a chosen theme while the app is open`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        preferences.setThemeName("Light")
        advanceUntilIdle()

        assertEquals(AppTheme.Light, viewModel.theme.value)
    }
}
