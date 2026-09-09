package io.github.fmaruejol.ardoise.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fmaruejol.ardoise.data.AppLanguage
import io.github.fmaruejol.ardoise.data.AppTheme
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: SettingsUiState,
        onDefaultCurrencyChange: (String) -> Unit = {},
        onLanguageChange: (AppLanguage) -> Unit = {},
        onThemeChange: (AppTheme) -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                SettingsScreen(
                    state = state,
                    onBack = {},
                    onServer = {},
                    onAbout = {},
                    onDefaultCurrencyChange = onDefaultCurrencyChange,
                    onLanguageChange = onLanguageChange,
                    onThemeChange = onThemeChange,
                )
            }
        }
    }

    @Test
    fun `says which currency a new group will start in`() {
        setContent(SettingsUiState(defaultCurrencyCode = "EUR"))

        compose.onNodeWithText("Currency for new groups").assertIsDisplayed()
        // The code and the symbol together: the code is what the server
        // stores and the symbol is what the reader recognises.
        compose.onNodeWithText("EUR (€)").assertIsDisplayed()
    }

    @Test
    fun `shows the stored choice rather than the default`() {
        setContent(SettingsUiState(defaultCurrencyCode = "JPY"))

        compose.onNodeWithText("JPY (¥)").assertIsDisplayed()
    }

    @Test
    fun `changes it from the list behind the row`() {
        var chosen: String? = null
        setContent(
            SettingsUiState(defaultCurrencyCode = "EUR"),
            onDefaultCurrencyChange = { chosen = it },
        )

        compose.onNodeWithText("Currency for new groups").performClick()
        compose.onNodeWithText("GBP (£)").performClick()

        assertEquals("GBP", chosen)
    }

    @Test
    fun `shows the server it is pointed at`() {
        setContent(SettingsUiState(baseUrl = "https://spliit.example.com/"))

        compose.onNodeWithText("spliit.example.com").assertIsDisplayed()
    }

    // --- the language switcher ---------------------------------------------

    @Test
    fun `says the app is following the device`() {
        setContent(SettingsUiState(language = AppLanguage.System))

        compose.onNode(hasText("Language") and hasText("System default")).assertIsDisplayed()
    }

    @Test
    fun `names a chosen language in that language`() {
        setContent(SettingsUiState(language = AppLanguage.French))

        // The autonym, not "French": somebody who has just picked the wrong
        // one has to be able to find their way back out.
        compose.onNodeWithText("Français").assertIsDisplayed()
    }

    @Test
    fun `changes the language from the list behind the row`() {
        var chosen: AppLanguage? = null
        setContent(
            SettingsUiState(language = AppLanguage.System),
            onLanguageChange = { chosen = it },
        )

        compose.onNode(hasText("Language") and hasText("System default")).performClick()
        compose.onNodeWithText("Français").performClick()

        assertEquals(AppLanguage.French, chosen)
    }

    // --- the theme switcher -------------------------------------------------

    @Test
    fun `says the theme is following the device`() {
        setContent(SettingsUiState(theme = AppTheme.System))

        compose.onNode(hasText("Theme") and hasText("System default")).assertIsDisplayed()
    }

    @Test
    fun `names a chosen theme`() {
        setContent(SettingsUiState(theme = AppTheme.Light))

        compose.onNodeWithText("Light").assertIsDisplayed()
    }

    @Test
    fun `changes the theme from the list behind the row`() {
        var chosen: AppTheme? = null
        setContent(SettingsUiState(theme = AppTheme.System), onThemeChange = { chosen = it })

        compose.onNode(hasText("Theme") and hasText("System default")).performClick()
        compose.onNodeWithText("Dark").performClick()

        assertEquals(AppTheme.Dark, chosen)
    }
}
