package io.github.fmaruejol.ardoise.ui.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: HomeUiState = HomeUiState(),
        onCreateGroup: () -> Unit = {},
        onJoinGroup: () -> Unit = {},
        onChangeServer: () -> Unit = {},
        onSettings: () -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                HomeScreen(
                    state = state,
                    onCreateGroup = onCreateGroup,
                    onJoinGroup = onJoinGroup,
                    onChangeServer = onChangeServer,
                    onSettings = onSettings,
                )
            }
        }
    }

    @Test
    fun `offers both ways to get a first group`() {
        setContent()

        compose.onNodeWithText("Create a group").assertIsDisplayed()
        compose.onNodeWithText("Scan or paste an invite").assertIsDisplayed()
    }

    @Test
    fun `names the server without asking anything about it`() {
        setContent(HomeUiState(baseUrl = "https://spliit.maruejol.fr/"))

        // There are no accounts, so nothing is asked for here. The server is a
        // footnote, not a gate.
        compose.onNodeWithText("Spliit server · spliit.maruejol.fr").assertIsDisplayed()
        compose.onNodeWithText("Change").assertIsDisplayed()
    }

    @Test
    fun `leads to the server screen`() {
        var changed = false
        setContent(onChangeServer = { changed = true })

        compose.onNodeWithText("Change").performClick()

        assertEquals(true, changed)
    }

    @Test
    fun `leads to joining a group`() {
        var joined = false
        setContent(onJoinGroup = { joined = true })

        compose.onNodeWithText("Scan or paste an invite").performClick()

        assertEquals(true, joined)
    }

    @Test
    fun `offers settings before there is a group to hang them off`() {
        setContent()

        // The group list's app bar is the only other way in, and a new
        // install has not
        // reached it, which is where the language is chosen.
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun `leads to the settings screen`() {
        var opened = false
        setContent(onSettings = { opened = true })

        compose.onNodeWithContentDescription("Settings").performClick()

        assertEquals(true, opened)
    }
}
