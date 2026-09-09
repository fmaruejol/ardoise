package io.github.fmaruejol.ardoise.ui.settings

import android.app.Application
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.data.AppLanguage
import io.github.fmaruejol.ardoise.data.AppTheme
import io.github.fmaruejol.ardoise.data.ExpenseOutbox
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
import kotlinx.coroutines.flow.first
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val api = FakeSpliitApi()
    private val preferences = FakeGroupPreferences()
    private val cache = testCache(api, dispatcher)
    private val outbox: ExpenseOutbox = testOutbox(api, cache, preferences, dispatcher)
    private val groups = GroupRepository(api, preferences, cache, outbox)
    private val servers = ServerRepository(preferences, { _ -> api })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val languages = LanguageRepository(preferences)

    private val themes = ThemeRepository(preferences)

    private fun viewModel() = SettingsViewModel(servers, groups, languages, themes)

    @Test
    fun `starts on the device's currency, like the create-group form`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(
                GroupCurrency.defaultCodeFor(),
                viewModel.state.value.defaultCurrencyCode,
            )
        }

    @Test
    fun `saves a chosen currency the moment it is chosen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDefaultCurrencyChange("JPY")
        advanceUntilIdle()

        // No Save button to lose it behind: it cannot fail and there is
        // nothing to roll back.
        assertEquals("JPY", preferences.defaultCurrencyCode.first())
        assertEquals("JPY", viewModel.state.value.defaultCurrencyCode)
    }

    @Test
    fun `follows the server the app is pointed at`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        preferences.setBaseUrl("https://spliit.example.com")
        advanceUntilIdle()

        // Observed rather than read once, so coming back from the server
        // screen shows the new address without a reload.
        assertEquals("https://spliit.example.com/", viewModel.state.value.baseUrl)
    }

    @Test
    fun `starts on the device's own language`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(AppLanguage.System, viewModel.state.value.language)
    }

    @Test
    fun `saves a chosen language the moment it is chosen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onLanguageChange(AppLanguage.French)
        advanceUntilIdle()

        // No Save button, for the same reason as the currency: it cannot fail
        // and there is nothing to roll back.
        assertEquals("fr", preferences.languageTag.first())
        assertEquals(AppLanguage.French, viewModel.state.value.language)
    }

    @Test
    fun `going back to the device's language forgets the tag`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onLanguageChange(AppLanguage.French)
        advanceUntilIdle()
        viewModel.onLanguageChange(AppLanguage.System)
        advanceUntilIdle()

        // Absent means "follow the device", so it is removed rather than
        // stored as a language of its own.
        assertNull(preferences.languageTag.first())
        assertEquals(AppLanguage.System, viewModel.state.value.language)
    }

    @Test
    fun `starts on the device's own light or dark setting`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(AppTheme.System, viewModel.state.value.theme)
    }

    @Test
    fun `saves a chosen theme the moment it is chosen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onThemeChange(AppTheme.Light)
        advanceUntilIdle()

        assertEquals("Light", preferences.themeName.first())
        assertEquals(AppTheme.Light, viewModel.state.value.theme)
    }

    @Test
    fun `going back to the device's setting forgets the name`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onThemeChange(AppTheme.Dark)
        advanceUntilIdle()
        viewModel.onThemeChange(AppTheme.System)
        advanceUntilIdle()

        // Absent means "follow the device", as with the language.
        assertNull(preferences.themeName.first())
        assertEquals(AppTheme.System, viewModel.state.value.theme)
    }
}
