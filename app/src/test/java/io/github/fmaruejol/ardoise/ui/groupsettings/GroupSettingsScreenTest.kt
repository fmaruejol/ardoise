package io.github.fmaruejol.ardoise.ui.groupsettings

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class GroupSettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val loaded = GroupSettingsUiState(
        isLoading = false,
        name = "Lisbon trip",
        participants = listOf(ParticipantEdit("p1", "Ana"), ParticipantEdit("p2", "Ben")),
        inviteUrl = "https://spliit.app/groups/abc123",
    )

    private fun setContent(
        state: GroupSettingsUiState,
        onPickYouOpen: () -> Unit = {},
        onYouChange: (Int) -> Unit = {},
        onPickYouDismiss: () -> Unit = {},
        onRemoveGroupClick: () -> Unit = {},
        onShare: () -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                GroupSettingsScreen(
                    state = state,
                    onBack = {},
                    onSave = {},
                    onNameChange = {},
                    onInformationChange = {},
                    onParticipantNameChange = { _, _ -> },
                    onAddParticipant = {},
                    onRemoveParticipant = {},
                    onBlockedRemovalDismiss = {},
                    onPickYouOpen = onPickYouOpen,
                    onPickYouDismiss = onPickYouDismiss,
                    onYouChange = onYouChange,
                    onRemoveGroupClick = onRemoveGroupClick,
                    onRemoveGroupConfirm = {},
                    onRemoveGroupDismiss = {},
                    onRetry = {},
                    onShare = onShare,
                )
            }
        }
    }

    @Test
    fun `puts the invite first, because the link is the only way in`() {
        setContent(loaded)

        compose.onNodeWithText("Invite to Lisbon trip").assertIsDisplayed()
        compose.onNodeWithText("Anyone with the link can edit").assertIsDisplayed()
    }

    @Test
    fun `says nobody is you until someone is`() {
        setContent(loaded)

        compose.onNodeWithText("Which one is you").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Not set. Pick yourself to see your balance")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `names who you are once you have said`() {
        setContent(loaded.copy(activeParticipantId = "p2"))

        compose
            .onNodeWithText("Ben · preselects Paid by on new expenses")
            .performScrollTo()
            .assertIsDisplayed()
        // And marks them in the list, so the two never disagree.
        compose.onNodeWithText("This is me").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `keeps the choice under its own heading`() {
        setContent(loaded.copy(activeParticipantId = "p1"))

        // Everything above this goes to the server; this does not. Saying so is
        // the point of the heading.
        compose.onNodeWithText("On this device").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `opens the picker when the row is tapped`() {
        var opened = false
        setContent(loaded, onPickYouOpen = { opened = true })

        compose.onNodeWithText("Which one is you").performScrollTo().performClick()

        assertEquals(true, opened)
    }

    @Test
    fun `lists the participants to choose from`() {
        var chosen = -1
        setContent(
            loaded.copy(pickingYou = true, activeParticipantId = "p1"),
            onYouChange = { chosen = it },
        )

        compose.onNodeWithText("Which one is you?").assertIsDisplayed()
        // "Ben" is also the text of his row behind the dialog, so pick the
        // option rather than the field.
        compose.onNode(hasText("Ben") and isSelectable()).performClick()

        assertEquals(1, chosen)
    }

    @Test
    fun `explains a participant who cannot be removed`() {
        setContent(loaded.copy(blockedRemoval = "Ben"))

        compose
            .onNodeWithText(
                "Ben appears on an expense, so removing them would change what " +
                    "everyone owes. Deal with those expenses first.",
            )
            .assertIsDisplayed()
    }

    @Test
    fun `offers to remove the group from this device`() {
        var clicked = false
        setContent(loaded, onRemoveGroupClick = { clicked = true })

        compose
            .onNodeWithText("Remove group from this device")
            .performScrollTo()
            .performClick()

        assertEquals(true, clicked)
    }

    @Test
    fun `says when the group is gone from the server`() {
        setContent(GroupSettingsUiState(isLoading = false, notFound = true))

        compose
            .onNodeWithText("This server has no group with that link any more.")
            .assertIsDisplayed()
    }

    // --- an error goes on the thing that is wrong --------------------------

    @Test
    fun `puts a name error on the participant it is about`() {
        setContent(
            loaded.copy(saveErrors = setOf(GroupSettingsError.DuplicateParticipant(1))),
        )

        // Once, not twice: it used to be a line at the foot of the form, and
        // leaving it there as well would say the same thing in two places.
        compose
            .onAllNodesWithText("Two participants cannot share a name.")
            .assertCountEquals(1)
    }

    @Test
    fun `keeps a failed save at the foot of the form`() {
        setContent(
            loaded.copy(
                saveErrors = setOf(
                    GroupSettingsError.Failed(SpliitError.Network(RuntimeException("offline"))),
                ),
            ),
        )

        // A server that refused the whole group belongs to no field, so it
        // stays where it was.
        compose
            .onNodeWithText("Could not reach that server. Check the address and your connection.")
            .assertIsDisplayed()
    }

    @Test
    fun `puts the cursor in a participant the moment it is added`() {
        setContent(
            loaded.copy(
                participants = loaded.participants + ParticipantEdit(null, ""),
                participantsAdded = 1,
            ),
        )

        // As on the create-group form: a row added is a name about to be typed.
        compose.onAllNodes(hasSetTextAction()).onLast().assertIsFocused()
    }

    @Test
    fun `takes nothing on opening the screen`() {
        setContent(loaded)

        compose.onAllNodes(hasSetTextAction()).onLast().assertIsNotFocused()
    }
}
