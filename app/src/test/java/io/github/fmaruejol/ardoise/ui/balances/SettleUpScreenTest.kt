package io.github.fmaruejol.ardoise.ui.balances

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class SettleUpScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: SettleUpUiState,
        onMarkPaid: (Transfer) -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                SettleUpScreen(
                    state = state,
                    onClose = {},
                    onMarkPaid = onMarkPaid,
                    onRetry = {},
                )
            }
        }
    }

    private fun transfer(
        from: String = "Ben",
        to: String = "Ana",
        amount: Long = 6510,
        fromIsYou: Boolean = false,
        toIsYou: Boolean = false,
        clears: Clears = Clears.From,
        leftOver: Long = 0,
    ) = Transfer(
        fromId = "p2",
        fromName = from,
        fromIndex = 1,
        toId = "p1",
        toName = to,
        toIndex = 0,
        amount = amount,
        fromIsYou = fromIsYou,
        toIsYou = toIsYou,
        clears = clears,
        leftOver = leftOver,
    )

    private fun loaded(vararg transfers: Transfer) = SettleUpUiState(
        isLoading = false,
        currency = GroupCurrency.of("EUR", "€"),
        transfers = transfers.toList(),
    )

    @Test
    fun `puts the payment in the second person`() {
        setContent(loaded(transfer(toIsYou = true)))

        compose.onNodeWithText("Ben pays you").assertIsDisplayed()
    }

    @Test
    fun `and the other way round when it is you paying`() {
        setContent(loaded(transfer(fromIsYou = true)))

        compose.onNodeWithText("You pay Ana").assertIsDisplayed()
    }

    @Test
    fun `names both when neither is you`() {
        setContent(loaded(transfer()))

        compose.onNodeWithText("Ben pays Ana").assertIsDisplayed()
    }

    @Test
    fun `says what the payment finishes`() {
        setContent(loaded(transfer(clears = Clears.Both)))

        compose.onNodeWithText("clears both balances").assertIsDisplayed()
    }

    @Test
    fun `names the side it clears`() {
        setContent(loaded(transfer(clears = Clears.From)))

        compose.onNodeWithText("clears Ben’s balance").assertIsDisplayed()
    }

    @Test
    fun `says what is left when a payment finishes neither side`() {
        setContent(loaded(transfer(clears = Clears.Neither, leftOver = 1910)))

        compose.onNodeWithText("partial, €19.10 left over").assertIsDisplayed()
    }

    @Test
    fun `marking one as paid hands it on rather than recording it`() {
        var marked: Transfer? = null
        setContent(loaded(transfer())) { marked = it }

        compose.onNodeWithText("Mark as paid").performClick()

        // It opens the expense form; nothing is written until the user saves.
        assertEquals(6510L, marked?.amount)
        assertEquals("p2", marked?.fromId)
        assertEquals("p1", marked?.toId)
    }

    @Test
    fun `says so when there is nothing to settle`() {
        setContent(loaded())

        compose.onNodeWithText("Nothing to settle. The group is already square.")
            .assertIsDisplayed()
    }
}
