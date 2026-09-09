package io.github.fmaruejol.ardoise.ui.creategroup

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onLast
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
class CreateGroupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: CreateGroupUiState = CreateGroupUiState(),
        onAddParticipant: () -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                CreateGroupScreen(
                    state = state,
                    onClose = {},
                    onNameChange = {},
                    onCurrencyChange = {},
                    onInformationChange = {},
                    onPickYouOpen = {},
                    onPickYouDismiss = {},
                    onYouChange = {},
                    onParticipantChange = { _, _ -> },
                    onAddParticipant = onAddParticipant,
                    onRemoveParticipant = {},
                    onSave = {},
                )
            }
        }
    }

    @Test
    fun `asks for a participant when the row is tapped`() {
        var added = false
        setContent(onAddParticipant = { added = true })

        compose.onNodeWithText("Add participant").performClick()

        assertEquals(true, added)
    }

    @Test
    fun `puts the cursor in a row the moment it is added`() {
        setContent(
            CreateGroupUiState(participants = listOf("Ana", ""), participantsAdded = 1),
        )

        // Otherwise adding three people is three taps on the row and three
        // more on the fields it made.
        compose.onAllNodes(hasSetTextAction()).onLast().assertIsFocused()
    }

    @Test
    fun `takes nothing on opening the form`() {
        setContent(CreateGroupUiState(participants = listOf("")))

        // Nothing has been added yet, so the keyboard stays down: the form
        // opens on a headline and a name field the user may not want first.
        compose.onAllNodes(hasSetTextAction()).onLast().assertIsNotFocused()
    }

    @Test
    fun `offers a seat for somebody who is not there yet`() {
        setContent()

        compose.onNodeWithText("Participants").assertIsDisplayed()
        compose.onNodeWithText("Add participant").assertIsDisplayed()
    }
}
