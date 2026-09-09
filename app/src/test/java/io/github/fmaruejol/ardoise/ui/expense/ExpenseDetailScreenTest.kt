package io.github.fmaruejol.ardoise.ui.expense

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class ExpenseDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: ExpenseDetailUiState) {
        compose.setContent {
            ArdoiseTheme {
                ExpenseDetailScreen(
                    state = state,
                    onBack = {},
                    onEdit = {},
                    onDeleteClick = {},
                    onDeleteConfirm = {},
                    onRetry = {},
                    onDeleteDismiss = {},
                )
            }
        }
    }

    private val expense = ExpenseDetailUiState(
        isLoading = false,
        isLoaded = true,
        title = "Dinner at Cervejaria",
        amount = 9600,
        currency = GroupCurrency.of("EUR", "€"),
        category = Category(1, "Food and Drink", "Dining Out"),
        date = LocalDate.parse("2026-09-11"),
        payerName = "Ana",
        isPaidByYou = true,
        splitMode = SplitMode.EVENLY,
        shares = listOf(
            ExpenseShareRow("p1", "Ana", 4800, isYou = true),
            ExpenseShareRow("p2", "Ben", 4800, isYou = false),
        ),
    )

    private val payment = ExpenseDetailUiState(
        isLoading = false,
        isLoaded = true,
        title = "Reimbursement",
        amount = 4920,
        currency = GroupCurrency.of("EUR", "€"),
        date = LocalDate.parse("2026-09-14"),
        payerName = "Chloé",
        splitMode = SplitMode.EVENLY,
        shares = listOf(ExpenseShareRow("p2", "Ben", 4920, isYou = false)),
        isReimbursement = true,
        transfer = TransferParties(
            payerName = "Chloé",
            payerIndex = 2,
            payerIsYou = false,
            payeeName = "Ben",
            payeeIndex = 1,
            payeeIsYou = false,
        ),
    )

    @Test
    @Config(qualifiers = "fr-rFR-w411dp-h891dp")
    fun `writes the date the way the reader's language does`() {
        setContent(expense)

        // The month goes first in English and second in French, and Japanese
        // would put the year first. Only a locale can answer that.
        compose.onNodeWithText("11 septembre 2026", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an ordinary expense leads with its category and title`() {
        setContent(expense)

        compose.onNodeWithText("Dining Out").assertIsDisplayed()
        compose.onNodeWithText("Dinner at Cervejaria").assertIsDisplayed()
        compose.onNodeWithText("September 11, 2026 · paid by you").assertIsDisplayed()
        compose.onNodeWithText("Split evenly, 2 ways").assertIsDisplayed()
    }

    @Test
    fun `a payment says so instead of naming a category`() {
        setContent(payment)

        compose.onNodeWithText("Reimbursement").assertIsDisplayed()
        // The chip has replaced the category row, so there is no category name
        // left to read.
        compose.onNodeWithText("Uncategorised").assertDoesNotExist()
    }

    @Test
    fun `a payment is headed by who paid whom`() {
        setContent(payment)

        // Not its title: the chip above already says "Reimbursement", which is
        // also what a settled-up payment is called.
        compose.onNodeWithText("Chloé pays Ben").assertIsDisplayed()
    }

    @Test
    fun `and by you when one end is you`() {
        setContent(payment.copy(transfer = payment.transfer!!.copy(payeeIsYou = true)))

        compose.onNodeWithText("Chloé pays you").assertIsDisplayed()
    }

    @Test
    fun `a payment shows its two sides, signed`() {
        setContent(payment)

        compose.onNodeWithText("Transfer").assertIsDisplayed()
        compose.onNodeWithText("Paid").assertIsDisplayed()
        compose.onNodeWithText("Received").assertIsDisplayed()
        compose.onNodeWithText("−€49.20").assertIsDisplayed()
        compose.onNodeWithText("+€49.20").assertIsDisplayed()
    }

    @Test
    fun `the date stands alone, because the transfer says who paid`() {
        setContent(payment)

        compose.onNodeWithText("September 14, 2026").assertIsDisplayed()
    }

    @Test
    fun `a payment explains why it is not in the totals`() {
        setContent(payment)

        compose.onNodeWithText(
            "Reimbursements move money between two people. " +
                "They are left out of the group total and of the stats.",
        ).assertIsDisplayed()
    }

    @Test
    fun `a reimbursement naming several people keeps the split card`() {
        setContent(
            payment.copy(
                transfer = null,
                shares = listOf(
                    ExpenseShareRow("p2", "Ben", 2460, isYou = false),
                    ExpenseShareRow("p3", "Cleo", 2460, isYou = false),
                ),
            ),
        )

        // Still a reimbursement, so the chip and the note stay; but two sides
        // is not what it has.
        compose.onAllNodesWithText("Reimbursement").assertCountEquals(2)
        compose.onNodeWithText("Split evenly, 2 ways").assertIsDisplayed()
        compose.onNodeWithText("Transfer").assertDoesNotExist()
    }

    @Test
    fun `an ordinary expense carries no reimbursement note`() {
        setContent(expense)

        compose.onNodeWithText("Transfer").assertDoesNotExist()
        compose.onNodeWithText("Reimbursement").assertDoesNotExist()
    }

    // --- an expense that repeats -------------------------------------------

    @Test
    fun `a repeating expense says so beside its category and again below`() {
        setContent(
            expense.copy(
                recurrenceRule = RecurrenceRule.MONTHLY,
                nextCopy = LocalDate.parse("2026-10-11"),
            ),
        )

        // The chip is the glance, a repeating expense looks like any other
        // row in the feed, and the row under the card is where it is spelled
        // out.
        compose.onNodeWithText("Monthly").assertIsDisplayed()
        compose.onNodeWithText("Recurrence").assertIsDisplayed()
        compose.onNodeWithText("Every month").assertIsDisplayed()
    }

    @Test
    fun `it names the next copy in the reader's own language`() {
        setContent(
            expense.copy(
                recurrenceRule = RecurrenceRule.MONTHLY,
                nextCopy = LocalDate.parse("2026-10-11"),
            ),
        )

        compose.onNodeWithText("Next copy on October 11, 2026").assertIsDisplayed()
    }

    @Test
    fun `a next copy that is not known is simply not named`() {
        // What the ViewModel does with an occurrence whose successor the
        // server has already made: the rule still holds, the date does not.
        setContent(expense.copy(recurrenceRule = RecurrenceRule.WEEKLY, nextCopy = null))

        compose.onNodeWithText("Every week").assertIsDisplayed()
        compose.onNodeWithText("Next copy on", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an expense that does not repeat says nothing about recurrence`() {
        setContent(expense)

        compose.onNodeWithText("Recurrence").assertDoesNotExist()
        compose.onNodeWithText("Monthly").assertDoesNotExist()
    }

    @Test
    fun `a repeating payment keeps both chips`() {
        setContent(payment.copy(recurrenceRule = RecurrenceRule.WEEKLY))

        // A payment has no category row for the chip to sit on, so it shares
        // the row with the one that replaced it.
        compose.onNodeWithText("Weekly").assertIsDisplayed()
        compose.onNodeWithText("Recurrence").assertIsDisplayed()
    }

    // --- an expense paid in another currency --------------------------------

    @Test
    fun `says what a converted expense was actually paid in`() {
        setContent(
            expense.copy(
                amount = 9643,
                original = OriginalAmount(
                    amount = 54_000,
                    currency = GroupCurrency.of("BRL", "BRL"),
                    rate = "0.17857",
                ),
            ),
        )

        // The group amount is what the balances are built from; this is the
        // only place the app says where it came from.
        compose.onNodeWithText("540.00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1 BRL = 0.17857 EUR", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an expense in the group's own currency says nothing about rates`() {
        setContent(expense)

        compose.onNodeWithText("1 BRL", substring = true).assertDoesNotExist()
    }
}
