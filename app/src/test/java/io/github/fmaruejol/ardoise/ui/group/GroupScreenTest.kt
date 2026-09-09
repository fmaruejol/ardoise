package io.github.fmaruejol.ardoise.ui.group

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.PaidForWithParticipant
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.data.PendingExpense
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class GroupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val ana = Participant("p1", "Ana")
    private val ben = Participant("p2", "Ben")

    private val dinner = ExpenseSummary(
        id = "e1",
        title = "Dinner at Cervejaria",
        amount = 9600,
        originalAmount = null,
        originalCurrency = null,
        expenseDate = LocalDate.parse("2026-09-11"),
        createdAt = Instant.parse("2026-09-11T18:00:00Z"),
        category = Category(1, "Food and Drink", "Dining Out"),
        paidBy = ana,
        paidFor = listOf(PaidForWithParticipant(ana, 1), PaidForWithParticipant(ben, 1)),
        splitMode = SplitMode.EVENLY,
        recurrenceRule = RecurrenceRule.NONE,
        isReimbursement = false,
        documentCount = 0,
    )

    private val loaded = GroupUiState(
        isLoading = false,
        groupName = "Lisbon trip",
        currency = GroupCurrency.of("EUR", "€"),
        days = listOf(ExpenseDay(dinner.expenseDate, listOf(dinner))),
        expenseCount = 1,
        totalSpent = 9600,
    )

    private val queued = PendingExpense(
        id = "q1",
        groupId = "g1",
        input = ExpenseInput(
            title = "Taxi to airport",
            amount = 2200,
            expenseDate = LocalDate.parse("2026-09-11"),
            paidById = "p1",
            paidFor = listOf(PaidFor("p1", 1), PaidFor("p2", 1)),
            splitMode = SplitMode.EVENLY,
        ),
        queuedAt = Instant.parse("2026-09-11T19:00:00Z"),
        attempts = 0,
        lastError = null,
    )

    private fun setContent(
        state: GroupUiState,
        onExpenseClick: (String) -> Unit = {},
        onAddExpense: () -> Unit = {},
        onGroupSettings: () -> Unit = {},
        onPickYouOpen: () -> Unit = {},
        onPendingClick: (String) -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                GroupScreen(
                    state = state,
                    onBack = {},
                    onExpenseClick = onExpenseClick,
                    onAddExpense = onAddExpense,
                    onGroupSettings = onGroupSettings,
                    onSearchOpen = {},
                    onSearchClose = {},
                    onQueryChange = {},
                    onPickYouOpen = onPickYouOpen,
                    onPendingClick = onPendingClick,
                    onPickYouDismiss = {},
                    onYouChange = {},
                    onCategoryFilter = {},
                    onPayerFilter = {},
                    onDateFilter = {},
                    onLoadMore = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun `heads each day and says who paid`() {
        setContent(loaded)

        compose.onNodeWithText("Friday, September 11").assertIsDisplayed()
        compose.onNodeWithText("Dinner at Cervejaria").assertIsDisplayed()
        compose.onNodeWithText("Ana paid · split 2 ways").assertIsDisplayed()
        // Once on the row and once as the total, which is the whole of what
        // this group has spent.
        compose.onAllNodesWithText("€96.00").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = "fr-rFR-w411dp-h891dp")
    fun `writes the day heading the way the reader's language does`() {
        setContent(loaded)

        // English is "Friday, September 11" and French is "vendredi 11
        // septembre", a different order and no comma.
        compose.onNodeWithText("vendredi 11 septembre").assertIsDisplayed()
    }

    @Test
    fun `shows nothing about your balance until you say who you are`() {
        setContent(anonymous)

        // A change needs a participant to be a change *for*; a number here
        // would be somebody else's.
        compose.onNodeWithText("Your share").assertDoesNotExist()
        compose.onNodeWithText("+€48.00").assertDoesNotExist()
    }

    @Test
    fun `marks an expense that left you up`() {
        setContent(
            loaded.copy(
                activeParticipantId = "p1",
                yourShare = 4800,
                yourBalanceChanges = mapOf("e1" to 4800L),
            ),
        )

        // Ana paid the 96 and owes 48 of it, so the row reads +48, not her
        // 48 share, which would have read the same but meant the opposite.
        compose.onNodeWithText("+€48.00").assertIsDisplayed()
    }

    @Test
    fun `marks an expense that left you down`() {
        setContent(
            loaded.copy(
                activeParticipantId = "p2",
                yourShare = 4800,
                yourBalanceChanges = mapOf("e1" to -4800L),
            ),
        )

        // A real minus sign, not a hyphen and not the locale's brackets.
        compose.onNodeWithText("−€48.00").assertIsDisplayed()
    }

    @Test
    fun `shows a flat zero when an expense is none of yours`() {
        setContent(
            loaded.copy(
                activeParticipantId = "p2",
                // Something else in the group cost you money; this expense
                // did not, which is the point.
                yourShare = 350,
                yourBalanceChanges = mapOf("e1" to 0L),
            ),
        )

        compose.onNodeWithText("€0.00").assertIsDisplayed()
    }

    @Test
    fun `opens an expense when its row is tapped`() {
        var opened: String? = null
        setContent(loaded, onExpenseClick = { opened = it })

        compose.onNodeWithText("Dinner at Cervejaria").performClick()

        assertEquals("e1", opened)
    }

    @Test
    fun `teaches the first expense and the invite`() {
        setContent(GroupUiState(isLoading = false, groupName = "Lisbon trip"))

        compose.onNodeWithText("No expenses yet").assertIsDisplayed()
        compose.onNodeWithText("Add expense").assertIsDisplayed()
        // An expense split with nobody is not much use, so the empty state
        // offers the invite too.
        compose.onNodeWithText("Invite the others first").assertIsDisplayed()
    }

    @Test
    fun `offers a new expense from the feed`() {
        var added = false
        setContent(loaded, onAddExpense = { added = true })

        compose.onNodeWithContentDescription("Add expense").performClick()

        assertEquals(true, added)
    }

    @Test
    fun `says when a search matches nothing`() {
        setContent(
            loaded.copy(days = emptyList(), expenseCount = 0, isSearching = true, query = "berlin"),
        )

        compose.onNodeWithText("No expense here matches “berlin”.").assertIsDisplayed()
    }

    // --- before anyone has said who they are -------------------------------

    /** The group has arrived and nobody has said which participant they are. */
    private val anonymous = loaded.copy(participants = listOf(ana, ben))

    /** The same, once someone has. */
    private val identified = anonymous.copy(
        activeParticipantId = "p1",
        yourShare = 4800,
        yourBalanceChanges = mapOf(dinner.id to 4800L),
    )

    @Test
    fun `asks who you are when nobody has said`() {
        setContent(anonymous)

        compose.onNodeWithText("Tell us who you are").assertIsDisplayed()
        compose.onNodeWithText(
            "Pick your name in this group to see your share and your balance.",
        ).assertIsDisplayed()
    }

    @Test
    fun `choosing a name is one tap from the feed`() {
        var asked = false
        setContent(anonymous, onPickYouOpen = { asked = true })

        compose.onNodeWithText("Choose my name").performClick()

        assertEquals(true, asked)
    }

    @Test
    fun `drops the share half of the totals rather than dashing it`() {
        setContent(anonymous)

        compose.onNodeWithText("Total spent").assertIsDisplayed()
        // The prompt above already says why it is missing; a column of
        // nothing beside the total would only ask the question again.
        compose.onNodeWithText("Your share").assertDoesNotExist()
    }

    @Test
    fun `says nothing about identity once it is known`() {
        setContent(identified)

        compose.onNodeWithText("Tell us who you are").assertDoesNotExist()
        compose.onNodeWithText("Your share").assertIsDisplayed()
    }

    @Test
    fun `does not ask before the group has loaded`() {
        setContent(anonymous.copy(participants = emptyList()))

        // Every group looks unidentified for the moment before it arrives,
        // and a prompt that flashes on each open teaches people to ignore it.
        compose.onNodeWithText("Tell us who you are").assertDoesNotExist()
    }

    @Test
    fun `asks on an empty group too`() {
        setContent(anonymous.copy(days = emptyList(), expenseCount = 0, totalSpent = 0))

        // A group with no expenses is exactly when the answer is about to
        // matter: the next thing anyone does is add one.
        compose.onNodeWithText("Tell us who you are").assertIsDisplayed()
        compose.onNodeWithText("No expenses yet").assertIsDisplayed()
    }

    @Test
    fun `keeps the prompt out of search results`() {
        setContent(anonymous.copy(isSearching = true, query = "dinner"))

        // The question is about the group, not about what was searched for.
        compose.onNodeWithText("Tell us who you are").assertDoesNotExist()
    }

    // --- expenses that have not been sent yet -----------------------------

    @Test
    fun `heads the feed with what is still waiting to upload`() {
        setContent(loaded.copy(pending = listOf(queued)))

        compose.onNodeWithText("Pending upload").assertIsDisplayed()
        compose.onNodeWithText("Waiting to upload").assertIsDisplayed()
        compose.onNodeWithText("Taxi to airport").assertIsDisplayed()
        // Its own amount, once, the totals above it are the server's and do
        // not count money the group has not been told about.
        compose.onAllNodesWithText("€22.00").assertCountEquals(1)
    }

    @Test
    fun `counts them beside the label`() {
        setContent(loaded.copy(pending = listOf(queued, queued.copy(id = "q2"))))

        compose.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun `opens a waiting expense when its row is tapped`() {
        var opened: String? = null
        setContent(loaded.copy(pending = listOf(queued)), onPendingClick = { opened = it })

        compose.onNodeWithText("Taxi to airport").performClick()

        assertEquals("q1", opened)
    }

    @Test
    fun `does not call a group empty while an expense is waiting in it`() {
        setContent(
            loaded.copy(days = emptyList(), expenseCount = 0, totalSpent = 0, pending = listOf(queued)),
        )

        // "No expenses yet" over one the user has just typed would read as
        // having lost it.
        compose.onNodeWithText("Taxi to airport").assertIsDisplayed()
        compose.onAllNodesWithText("No expenses yet").assertCountEquals(0)
    }

    // --- the offline banner ----------------------------------------------

    @Test
    fun `says it is offline on the platform's word alone`() {
        // Nothing has failed: the screen was opened before the phone lost its
        // network, so no read has been made since to fail.
        setContent(loaded.copy(isOffline = true, error = null))

        compose.onNodeWithText("Offline. Showing the last synced copy").assertIsDisplayed()
    }

    @Test
    fun `says nothing while there is a network and nothing has failed`() {
        setContent(loaded)

        compose.onAllNodesWithText("Offline. Showing the last synced copy").assertCountEquals(0)
    }
}
