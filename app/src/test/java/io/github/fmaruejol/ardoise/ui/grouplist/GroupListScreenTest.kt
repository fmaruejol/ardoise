package io.github.fmaruejol.ardoise.ui.grouplist

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.data.groupSummary
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class GroupListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: GroupListUiState,
        onGroupClick: (String) -> Unit = {},
        onCreateGroup: () -> Unit = {},
        onJoinGroup: () -> Unit = {},
        onSettings: () -> Unit = {},
        onRefresh: () -> Unit = {},
        onSearchOpen: () -> Unit = {},
        onSearchClose: () -> Unit = {},
        onQueryChange: (String) -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                GroupListScreen(
                    state = state,
                    onGroupClick = onGroupClick,
                    onCreateGroup = onCreateGroup,
                    onJoinGroup = onJoinGroup,
                    onSettings = onSettings,
                    onRefresh = onRefresh,
                    onSearchOpen = onSearchOpen,
                    onSearchClose = onSearchClose,
                    onQueryChange = onQueryChange,
                )
            }
        }
    }

    private val trip = groupSummary("g1", "Trip to Lisbon")

    @Test
    fun `teaches both ways in when there are no groups yet`() {
        setContent(GroupListUiState(isLoading = false))

        compose.onNodeWithText("No groups yet").assertIsDisplayed()
        // A group is only reachable by link, so the empty state has to offer
        // creating one *and* joining one.
        compose.onNodeWithText("Create a group").assertIsDisplayed()
        compose.onNodeWithText("Join with a link").assertIsDisplayed()
    }

    @Test
    fun `offers to join from the empty state`() {
        var joined = false
        setContent(GroupListUiState(isLoading = false), onJoinGroup = { joined = true })

        compose.onNodeWithText("Join with a link").performClick()

        assertEquals(true, joined)
    }

    @Test
    fun `lists the groups with how many people are in them`() {
        setContent(GroupListUiState(isLoading = false, groups = listOf(trip)))

        compose.onNodeWithText("Trip to Lisbon").assertIsDisplayed()
        compose.onNodeWithText("2 people").assertIsDisplayed()
        compose.onNodeWithText("1 group on this device").assertIsDisplayed()
    }

    @Test
    fun `opens a group when its card is tapped`() {
        var opened: String? = null
        setContent(
            GroupListUiState(isLoading = false, groups = listOf(trip)),
            onGroupClick = { opened = it },
        )

        compose.onNodeWithText("Trip to Lisbon").performClick()

        assertEquals("g1", opened)
    }

    @Test
    fun `keeps showing the groups when a refresh fails`() {
        setContent(
            GroupListUiState(
                isLoading = false,
                groups = listOf(trip),
                error = SpliitError.Network(RuntimeException("offline")),
            ),
        )

        compose.onNodeWithText("Trip to Lisbon").assertIsDisplayed()
        // The same banner every other reading screen uses, and it says what a
        // network failure actually means for the rows below it: they came out
        // of the cache.
        compose.onNodeWithText("Offline. Showing the last synced copy").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun `offers a new group from the list`() {
        var created = false
        setContent(
            GroupListUiState(isLoading = false, groups = listOf(trip)),
            onCreateGroup = { created = true },
        )

        compose.onNodeWithText("New group").performClick()

        assertEquals(true, created)
    }

    @Test
    fun `offers to join from the list as well as from the empty state`() {
        var joined = false
        setContent(
            GroupListUiState(isLoading = false, groups = listOf(trip)),
            onJoinGroup = { joined = true },
        )

        // Until this button existed, a second group could only arrive by
        // opening a link: the empty state was the one screen that offered it.
        compose.onNodeWithText("Join a group").performClick()

        assertEquals(true, joined)
    }

    @Test
    fun `offers no search until the list is long enough to need one`() {
        setContent(GroupListUiState(isLoading = false, groups = listOf(trip)))

        compose.onNodeWithContentDescription("Search groups").assertDoesNotExist()
    }

    @Test
    fun `offers search once there are many groups`() {
        val many = (1..6).map { groupSummary("g$it", "Group $it") }
        var opened = false
        setContent(
            GroupListUiState(isLoading = false, groups = many),
            onSearchOpen = { opened = true },
        )

        compose.onNodeWithContentDescription("Search groups").performClick()

        assertEquals(true, opened)
    }

    @Test
    fun `says when a search matches nothing`() {
        setContent(
            GroupListUiState(
                isLoading = false,
                groups = listOf(trip),
                isSearching = true,
                query = "berlin",
            ),
        )

        compose.onNodeWithText("No group here matches “berlin”.").performScrollTo()
            .assertIsDisplayed()
    }

    // --- where the user stands ---------------------------------------------

    @Test
    fun `says what the group owes you`() {
        setContent(
            GroupListUiState(
                isLoading = false,
                groups = listOf(trip),
                positions = mapOf("g1" to 8420L),
            ),
        )

        compose.onNodeWithText("You are owed").assertIsDisplayed()
        compose.onNodeWithText("€84.20").assertIsDisplayed()
    }

    @Test
    fun `and what you owe it`() {
        setContent(
            GroupListUiState(
                isLoading = false,
                groups = listOf(trip),
                positions = mapOf("g1" to -1200L),
            ),
        )

        // The sign is in the words, not on the number.
        compose.onNodeWithText("You owe").assertIsDisplayed()
        compose.onNodeWithText("€12.00").assertIsDisplayed()
    }

    @Test
    fun `says settled when it is square`() {
        setContent(
            GroupListUiState(
                isLoading = false,
                groups = listOf(trip),
                positions = mapOf("g1" to 0L),
            ),
        )

        compose.onNodeWithText("Settled up").assertIsDisplayed()
    }

    @Test
    fun `says nothing at all when there is no answer`() {
        setContent(GroupListUiState(isLoading = false, groups = listOf(trip)))

        // A group nobody has claimed, or a read that failed: absent rather
        // than settled, because zero already means something.
        compose.onNodeWithText("Settled up").assertDoesNotExist()
        compose.onNodeWithText("You are owed").assertDoesNotExist()
        compose.onNodeWithText("You owe").assertDoesNotExist()
    }
}
