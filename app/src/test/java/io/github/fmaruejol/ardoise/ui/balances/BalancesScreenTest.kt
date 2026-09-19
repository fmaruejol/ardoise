package io.github.fmaruejol.ardoise.ui.balances

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.ui.pullDown
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class BalancesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: BalancesUiState,
        onSettleUp: () -> Unit = {},
        onPickYouOpen: () -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                BalancesScreen(
                    state = state,
                    onBack = {},
                    onSettleUp = onSettleUp,
                    onTotals = {},
                    onGroupSettings = {},
                    onRefresh = onRefresh,
                    onPickYouOpen = onPickYouOpen,
                    onPickYouDismiss = {},
                    onYouChange = {},
                )
            }
        }
    }

    private val loaded = BalancesUiState(
        isLoading = false,
        groupName = "Lisbon trip",
        currency = GroupCurrency.of("EUR", "€"),
        rows = listOf(
            BalanceRow("p1", "Ana", 8420, isYou = true, weight = 1f),
            BalanceRow("p2", "Ben", -8420, isYou = false, weight = 1f),
        ),
        yourPosition = 8420,
        counterparties = 1,
        canSettle = true,
    )

    @Test
    fun `pulling the balances down refreshes them`() {
        var pulled = 0
        setContent(loaded, onRefresh = { pulled++ })

        compose.onNodeWithText("Your position").pullDown()

        assertEquals(1, pulled)
    }

    @Test
    fun `empty balances can be pulled down too`() {
        var pulled = 0
        setContent(BalancesUiState(isLoading = false), onRefresh = { pulled++ })

        // A centred Box would swallow the gesture; nothing here scrolls.
        compose.onNodeWithText("Nothing to balance until there is an expense.").pullDown()

        assertEquals(1, pulled)
    }

    @Test
    fun `leads with your own position`() {
        setContent(loaded)

        compose.onNodeWithText("Your position").assertIsDisplayed()
        // Once in the card at the top and once on your own row below it.
        compose.onAllNodesWithText("+€84.20").assertCountEquals(2)
        compose.onNodeWithText("One person owes you").assertIsDisplayed()
    }

    @Test
    fun `says who you are in the list`() {
        setContent(loaded)

        compose.onNodeWithText("Ana (you)").assertIsDisplayed()
        compose.onNodeWithText("Ben").assertIsDisplayed()
    }

    @Test
    fun `asks you to say who you are rather than showing somebody else's position`() {
        setContent(loaded.copy(yourPosition = null, counterparties = 0))

        compose.onNodeWithText("Tell us who you are").assertIsDisplayed()
        // In place of the position card, not above it: that card has nothing
        // to say without an answer.
        compose.onNodeWithText("Your position").assertDoesNotExist()
    }

    @Test
    fun `choosing a name is one tap from the balances too`() {
        var asked = false
        setContent(
            loaded.copy(yourPosition = null, counterparties = 0),
            onPickYouOpen = { asked = true },
        )

        compose.onNodeWithText("Choose my name").performClick()

        assertEquals(true, asked)
    }

    @Test
    fun `everyone's balances are readable without saying who you are`() {
        setContent(loaded.copy(yourPosition = null, counterparties = 0))

        // The prompt is not a gate. The group's balances are public and the
        // rest of the screen works.
        compose.onNodeWithText("Ben").assertIsDisplayed()
        compose.onNodeWithText("Settle up").assertIsDisplayed()
    }

    @Test
    fun `does not ask before the balances have loaded`() {
        setContent(loaded.copy(rows = emptyList(), yourPosition = null))

        compose.onNodeWithText("Tell us who you are").assertDoesNotExist()
    }

    @Test
    fun `says nothing about identity once it is known`() {
        setContent(loaded)

        compose.onNodeWithText("Tell us who you are").assertDoesNotExist()
        compose.onNodeWithText("Your position").assertIsDisplayed()
    }

    @Test
    fun `offers to settle only when there is something to settle`() {
        setContent(loaded.copy(canSettle = false))

        compose.onNodeWithText("Settle up").assertDoesNotExist()
    }

    @Test
    fun `settling up is one tap from here`() {
        var settled = false
        setContent(loaded, onSettleUp = { settled = true })

        compose.onNodeWithText("Settle up").performClick()

        assertEquals(true, settled)
    }

    @Test
    fun `says the group is square rather than showing nothing`() {
        setContent(
            loaded.copy(
                rows = listOf(
                    BalanceRow("p1", "Ana", 0, isYou = true, weight = 0f),
                    BalanceRow("p2", "Ben", 0, isYou = false, weight = 0f),
                ),
                yourPosition = 0,
                counterparties = 0,
                canSettle = false,
            ),
        )

        compose.onNodeWithText("Everyone is square").assertIsDisplayed()
    }

    @Test
    fun `a failed load says so above what it already had`() {
        setContent(loaded.copy(error = SpliitError.Network(Exception("offline"))))

        compose.onNodeWithText("Offline. Showing the last synced copy").assertIsDisplayed()
        // The rows stay: they are the last thing that did load, and they are
        // still the best answer available.
        compose.onNodeWithText("Ana (you)").assertIsDisplayed()
    }
}
