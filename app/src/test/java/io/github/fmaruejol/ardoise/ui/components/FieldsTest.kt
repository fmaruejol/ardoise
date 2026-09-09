package io.github.fmaruejol.ardoise.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class FieldsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `keeps the label in the border while the field is empty`() {
        compose.setContent {
            ArdoiseTheme {
                ArdoiseTextField(
                    value = "",
                    onValueChange = {},
                    label = "Group name",
                    placeholder = "Lisbon trip",
                )
            }
        }

        // Material's own field drops the label into the field when it is empty
        // and unfocused, so the placeholder cannot be seen at the same time.
        compose.onNodeWithText("Group name").assertIsDisplayed()
        compose.onNodeWithText("Lisbon trip").assertIsDisplayed()
    }

    @Test
    fun `shows the value in place of the placeholder`() {
        compose.setContent {
            ArdoiseTheme {
                ArdoiseTextField(
                    value = "Lisbon trip",
                    onValueChange = {},
                    label = "Group name",
                    placeholder = "Type a name",
                )
            }
        }

        compose.onNodeWithText("Group name").assertIsDisplayed()
        compose.onNodeWithText("Type a name").assertDoesNotExist()
    }

    @Test
    fun `keeps a picker's label in the border with nothing chosen`() {
        compose.setContent {
            ArdoiseTheme {
                ArdoisePickerField(value = "", label = "Which one is you", onClick = {})
            }
        }

        compose.onNodeWithText("Which one is you").assertIsDisplayed()
    }

    @Test
    fun `keeps the gap a caller asked for above a field`() {
        compose.setContent {
            ArdoiseTheme {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Above")
                    ArdoiseTextField(
                        value = "",
                        onValueChange = {},
                        label = "Note",
                        modifier = Modifier.padding(top = FieldLabelOverhang),
                    )
                }
            }
        }

        // The label is drawn with an offset, which moves it without measuring
        // it, so a field's top is its border and the label hangs over whatever
        // is above.
        val above = compose.onNodeWithText("Above").getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("Note").getUnclippedBoundsInRoot()
        val gap = label.top - above.bottom
        assertTrue("only $gap between the two", gap.value >= 11f)
    }

    @Test
    fun `keeps a name and the caption under it together`() {
        compose.setContent {
            ArdoiseTheme {
                Column(Modifier.padding(16.dp)) {
                    InlineNameField(value = "Ana", onValueChange = {}, placeholder = "Name")
                    Text("This is me")
                }
            }
        }

        // A Material filled field would put 16dp of its own padding under the
        // name, which is what pushed the caption away from the name it belongs
        // to.
        val name = compose.onNodeWithText("Ana").getUnclippedBoundsInRoot()
        val caption = compose.onNodeWithText("This is me").getUnclippedBoundsInRoot()
        val gap = caption.top - name.bottom
        assertTrue("name and caption are $gap apart", gap.value <= 1f)
    }
}
