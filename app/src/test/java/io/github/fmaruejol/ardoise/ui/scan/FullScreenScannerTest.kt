package io.github.fmaruejol.ardoise.ui.scan

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

/** The full-screen scanner, everything except the camera. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FullScreenScannerTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        onScan: (String) -> Unit = {},
        onClose: () -> Unit = {},
        onUseLink: () -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                FullScreenScanner(
                    onScan = onScan,
                    onClose = onClose,
                    onUseLink = onUseLink,
                )
            }
        }
    }

    @Test
    fun `says what to aim at`() {
        setContent()

        compose.onNodeWithText("Scan QR code").assertIsDisplayed()
        compose.onNodeWithText("Hold the whole QR code inside the frame").assertIsDisplayed()
    }

    @Test
    fun `closes from the bar`() {
        var closed = false
        setContent(onClose = { closed = true })

        compose.onNodeWithContentDescription("Close").performClick()

        assertEquals(true, closed)
    }

    @Test
    fun `offers the link field as the way out`() {
        var used = false
        setContent(onUseLink = { used = true })

        // The screen it is standing in front of is the one with the field on
        // it, so the way back has to be on this screen and not only in the bar.
        compose.onNodeWithText("Use a link instead").performClick()

        assertEquals(true, used)
    }

    @Test
    fun `offers no light on a device that has none`() {
        setContent()

        // Absent rather than disabled: a control that cannot do anything is a
        // question the user has to answer.
        compose.onNodeWithContentDescription("Turn the light on").assertDoesNotExist()
        compose.onNodeWithContentDescription("Turn the light off").assertDoesNotExist()
    }
}
