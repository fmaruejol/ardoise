package io.github.fmaruejol.ardoise.ui.expense

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class CategoryTileTest {
    @get:Rule
    val compose = createComposeRule()

    /** The longest and shortest names the server actually sends. */
    private val names = listOf("Rent", "Household Supplies", "TV/Phone/Internet", "Pets")

    @Test
    fun `every tile is the same height, whatever its name`() {
        compose.setContent {
            ArdoiseTheme {
                // A third of the sheet, as the three-column grid gives each.
                Column(Modifier.width(120.dp)) {
                    names.forEachIndexed { index, name ->
                        CategoryTile(
                            category = Category(index, "Home", name),
                            selected = false,
                            onClick = {},
                        )
                    }
                }
            }
        }

        // Asserted against the constant rather than against each other: names
        // wrap at different widths and font metrics, so two tiles agreeing
        // proves nothing on a run where neither happened to wrap.
        val tiles = compose.onAllNodes(hasClickAction())
        names.indices.forEach { index ->
            val bounds = tiles[index].getUnclippedBoundsInRoot()
            assertEquals(CategoryTileHeight, bounds.bottom - bounds.top)
        }
    }

    @Test
    fun `a name that wraps is still readable`() {
        compose.setContent {
            ArdoiseTheme {
                // Narrow enough to force the wrap the fixed height allows for.
                Column(Modifier.width(88.dp)) {
                    CategoryTile(
                        Category(1, "Home", "Household Supplies"),
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }

        // Fixing the height must not clip the label it was fixed for.
        compose.onNodeWithText("Household Supplies").assertIsDisplayed()
    }
}
