package io.github.fmaruejol.ardoise.ui.server

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.fmaruejol.ardoise.core.instance.BaseUrl
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class ServerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: ServerUiState,
        onBack: () -> Unit = {},
        onSave: () -> Unit = {},
        onTest: () -> Unit = {},
        onChoiceChange: (InstanceChoice) -> Unit = {},
        onUrlChange: (String) -> Unit = {},
        onPermissionPromptDismiss: () -> Unit = {},
        onPermissionPromptRequest: () -> Unit = {},
        onOpenAppSettings: () -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                ServerScreen(
                    state = state,
                    onBack = onBack,
                    onSave = onSave,
                    onTest = onTest,
                    onChoiceChange = onChoiceChange,
                    onUrlChange = onUrlChange,
                    onPermissionPromptDismiss = onPermissionPromptDismiss,
                    onPermissionPromptRequest = onPermissionPromptRequest,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
        }
    }

    @Test
    fun `offers the project's instance and hides the address field`() {
        setContent(ServerUiState())

        compose.onNodeWithText("spliit.app").assertIsDisplayed()
        compose.onNodeWithText("The public instance").assertIsDisplayed()
        compose.onNodeWithText("Base URL").assertDoesNotExist()
    }

    @Test
    fun `asks for an address once the user chooses their own server`() {
        setContent(ServerUiState(choice = InstanceChoice.SelfHosted))

        compose.onNodeWithText("Base URL").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reports the choice when an option is tapped`() {
        var chosen: InstanceChoice? = null
        setContent(ServerUiState(), onChoiceChange = { chosen = it })

        compose.onNodeWithText("Self-hosted").performClick()

        assertEquals(InstanceChoice.SelfHosted, chosen)
    }

    @Test
    fun `saves from the app bar`() {
        var saved = false
        setContent(ServerUiState(), onSave = { saved = true })

        compose.onNodeWithText("Save").performClick()

        assertEquals(true, saved)
    }

    @Test
    fun `explains why an address was rejected`() {
        setContent(
            ServerUiState(
                choice = InstanceChoice.SelfHosted,
                customUrl = "nonsense",
                urlError = BaseUrl.Reason.Malformed,
            ),
        )

        compose.onNodeWithText("That does not look like a web address.").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `says when an instance answered`() {
        setContent(
            ServerUiState(
                choice = InstanceChoice.SelfHosted,
                customUrl = "https://spliit.example.com/",
                checkOutcome = CheckOutcome.Reachable,
            ),
        )

        compose.onNodeWithText("Reachable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Test again").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `says when an instance did not answer, and still allows saving`() {
        setContent(
            ServerUiState(
                choice = InstanceChoice.SelfHosted,
                customUrl = "https://spliit.example.com/",
                checkOutcome = CheckOutcome.Failed(SpliitError.Network(RuntimeException("x"))),
            ),
        )

        compose
            .onNodeWithText("Could not reach that server. Check the address and your connection.")
            .performScrollTo()
            .assertIsDisplayed()
        // An address can be right and merely unreachable right now.
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `warns that groups stay with the server they belong to`() {
        setContent(
            ServerUiState(
                choice = InstanceChoice.SelfHosted,
                customUrl = "https://spliit.example.com/",
                savedBaseUrl = "https://spliit.app/",
                savedGroupCount = 3,
            ),
        )

        compose
            .onNodeWithText(
                "Groups belong to the server that holds them. Switching keeps the " +
                    "3 groups saved for spliit.app, and they come back when you switch back.",
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `prompts for network access when it is missing`() {
        setContent(ServerUiState(networkAccessBlocked = true))

        compose.onNodeWithText("Network access is blocked").assertIsDisplayed()
    }

    @Test
    fun `offers Android settings as a way out of the prompt`() {
        var opened = false
        setContent(
            ServerUiState(networkAccessBlocked = true),
            onOpenAppSettings = { opened = true },
        )

        // An app cannot ask for this permission back, so settings is the only
        // useful action.
        compose.onNodeWithText("Open app settings").performClick()

        assertEquals(true, opened)
    }

    @Test
    fun `blocks saving while network access is missing`() {
        setContent(
            ServerUiState(networkAccessBlocked = true, permissionPromptDismissed = true),
        )

        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `still explains itself once the prompt is dismissed`() {
        setContent(
            ServerUiState(networkAccessBlocked = true, permissionPromptDismissed = true),
        )

        compose.onNodeWithText("Ardoise cannot reach any server until network access is allowed.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `shows no prompt when network access is allowed`() {
        setContent(ServerUiState())

        compose.onNodeWithText("Network access is blocked").assertDoesNotExist()
    }
}
