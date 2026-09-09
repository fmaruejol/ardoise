package io.github.fmaruejol.ardoise.ui.join

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The join screen has no camera on it: the card is an invitation to the
 * full-screen scanner, which is where the viewfinder lives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class JoinGroupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: JoinGroupUiState) {
        compose.setContent {
            ArdoiseTheme {
                JoinGroupScreen(
                    state = state,
                    onBack = {},
                    onLinkChange = {},
                    onScan = {},
                    onJoin = {},
                    onFullScreenOpen = {},
                    onFullScreenClose = {},
                )
            }
        }
    }

    @Test
    fun `offers the link field`() {
        setContent(JoinGroupUiState())

        compose.onNodeWithText("Group link").assertIsDisplayed()
        compose.onNodeWithText("Join group").assertIsDisplayed()
    }

    @Test
    fun `swaps the whole screen for the scanner rather than navigating`() {
        setContent(JoinGroupUiState(link = "half typed", scanningFullScreen = true))

        // The scanner is here to fill in the field on the screen behind it, so
        // it is a state of that screen: the half-typed link is still there
        // when it closes, with no back stack entry carrying it.
        compose.onNodeWithText("Scan QR code").assertIsDisplayed()
        compose.onNodeWithText("Join group").assertDoesNotExist()
    }

    @Test
    fun `invites the scanner rather than opening it`() {
        setContent(JoinGroupUiState())

        // No viewfinder on this screen, so nothing asks for the camera of
        // somebody who came to paste a link.
        compose.onNodeWithText("Scan the group's QR code").assertIsDisplayed()
        compose.onNodeWithText("Open scanner").assertIsDisplayed()
        compose.onNodeWithText("The scanner needs access to your camera.", substring = true)
            .assertIsDisplayed()
    }
}
