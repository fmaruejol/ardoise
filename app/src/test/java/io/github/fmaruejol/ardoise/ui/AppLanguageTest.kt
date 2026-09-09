package io.github.fmaruejol.ardoise.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.data.AppLanguage
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Android's own per-app language is API 33 and this app runs from 26, so the
 * choice is applied by hand, which makes it something to have tests on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class AppLanguageTest {
    @get:Rule
    val compose = createComposeRule()

    /** The process default is half of what this file changes; put it back. */
    private val original = Locale.getDefault()

    @After
    fun tearDown() = Locale.setDefault(original)

    private fun setContent(language: AppLanguage, content: @Composable () -> Unit) {
        compose.setContent {
            ArdoiseTheme {
                ProvideAppLanguage(language, content)
            }
        }
    }

    @Test
    fun `draws in the chosen language whatever the device is set to`() {
        setContent(AppLanguage.French) { Text(stringResource(R.string.settings_title)) }

        // The device is English here, the qualifiers say nothing else, so
        // this is the choice being applied rather than the phone's language.
        compose.onNodeWithText("Paramètres").assertIsDisplayed()
    }

    @Test
    fun `follows the device when nothing has been chosen`() {
        setContent(AppLanguage.System) { Text(stringResource(R.string.settings_title)) }

        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-rFR-w411dp-h891dp")
    fun `a French device reads French without anyone choosing it`() {
        setContent(AppLanguage.System) { Text(stringResource(R.string.settings_title)) }

        compose.onNodeWithText("Paramètres").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-rDE-w411dp-h891dp")
    fun `a language the app does not ship falls back to English`() {
        setContent(AppLanguage.System) { Text(stringResource(R.string.settings_title)) }

        // Android's own resolution: no `values-de`, so `values`.
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `money follows the language, not only the words`() {
        setContent(AppLanguage.French) {
            Text(GroupCurrency.of("EUR", "€").format(3000))
        }

        // `:core` formats with `Locale.getDefault()` when a screen does not
        // name one, so the process default has to move with the choice, or the
        // app would read French and write €30.00.
        compose.onNodeWithText("30,00", substring = true).assertIsDisplayed()
    }
}
