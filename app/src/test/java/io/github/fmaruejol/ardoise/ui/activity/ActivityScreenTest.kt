package io.github.fmaruejol.ardoise.ui.activity

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.fmaruejol.ardoise.core.model.ActivityType
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.ui.pullDown
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class ActivityScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: ActivityUiState,
        onExpenseClick: (String) -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        compose.setContent {
            ArdoiseTheme {
                ActivityScreen(
                    state = state,
                    onBack = {},
                    onExpenseClick = onExpenseClick,
                    onLoadMore = {},
                    onRefresh = onRefresh,
                )
            }
        }
    }

    @Test
    fun `pulling the log down refreshes it`() {
        var pulled = 0
        setContent(day(row()), onRefresh = { pulled++ })

        compose.onNodeWithText("Surf lesson", substring = true).pullDown()

        assertEquals(1, pulled)
    }

    @Test
    fun `an empty log can be pulled down too`() {
        var pulled = 0
        setContent(ActivityUiState(isLoading = false), onRefresh = { pulled++ })

        // A centred Box would swallow the gesture; nothing here scrolls.
        compose.onNodeWithText("Nothing has happened in this group yet.").pullDown()

        assertEquals(1, pulled)
    }

    private fun row(
        id: String = "a1",
        type: ActivityType = ActivityType.CREATE_EXPENSE,
        who: String = "Ben",
        byYou: Boolean = false,
        what: String? = "Surf lesson",
        expenseId: String? = "e1",
    ) = ActivityRow(
        id = id,
        type = type,
        // 09:00 on the given day in the device's own zone, so the rendered time
        // does not depend on where the test runs.
        time = LocalDate.now().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant(),
        who = who,
        byYou = byYou,
        what = what,
        expenseId = expenseId,
    )

    private fun day(vararg rows: ActivityRow, date: LocalDate = LocalDate.now()) =
        ActivityUiState(isLoading = false, days = listOf(ActivityDay(date, rows.toList())))

    @Test
    fun `heads today by name rather than by date`() {
        setContent(day(row()))

        compose.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun `and yesterday too`() {
        setContent(day(row(), date = LocalDate.now().minusDays(1)))

        compose.onNodeWithText("Yesterday").assertIsDisplayed()
    }

    @Test
    fun `anything older gets its date`() {
        setContent(day(row(), date = LocalDate.parse("2026-01-14")))

        compose.onNodeWithText("January 14, 2026").assertIsDisplayed()
    }

    @Test
    fun `names who did it`() {
        setContent(day(row()))

        compose.onNodeWithText("Ben added Surf lesson").assertIsDisplayed()
    }

    @Test
    fun `says you rather than your own name`() {
        setContent(day(row(byYou = true)))

        compose.onNodeWithText("You added Surf lesson").assertIsDisplayed()
    }

    @Test
    fun `an entry the server did not attribute is still readable`() {
        setContent(day(row(who = "")))

        compose.onNodeWithText("Someone added Surf lesson").assertIsDisplayed()
    }

    @Test
    fun `an expense with no title left is still an expense`() {
        setContent(day(row(type = ActivityType.DELETE_EXPENSE, what = null)))

        compose.onNodeWithText("Ben deleted an expense").assertIsDisplayed()
    }

    @Test
    fun `an entry about an expense leads to it`() {
        var opened: String? = null
        setContent(day(row()), onExpenseClick = { opened = it })

        compose.onNodeWithText("Ben added Surf lesson").performClick()

        assertEquals("e1", opened)
    }

    @Test
    fun `a deleted expense leads nowhere`() {
        var opened: String? = null
        setContent(
            day(row(type = ActivityType.DELETE_EXPENSE)),
            onExpenseClick = { opened = it },
        )

        compose.onNodeWithText("Ben deleted Surf lesson").performClick()

        // The expense is gone; the only thing that screen could report is that
        // it is gone.
        assertEquals(null, opened)
    }

    @Test
    fun `an empty log says so`() {
        setContent(ActivityUiState(isLoading = false))

        compose.onNodeWithText("Nothing has happened in this group yet.").assertIsDisplayed()
    }

    @Test
    fun `a failed load keeps what it already had`() {
        setContent(day(row()).copy(error = SpliitError.Network(Exception("offline"))))

        compose.onNodeWithText("Offline. Showing the last synced copy").assertIsDisplayed()
        compose.onNodeWithText("Ben added Surf lesson").assertIsDisplayed()
    }

    @Test
    fun `shows the time in the device's own zone`() {
        setContent(day(row()))

        // The fixture is built as 09:00 in the device's zone and stored as an
        // instant, so reading it back is a round trip through the zone.
        compose.onNodeWithText("9:00", substring = true).assertIsDisplayed()
    }
}
