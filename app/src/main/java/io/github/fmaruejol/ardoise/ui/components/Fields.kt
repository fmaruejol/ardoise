package io.github.fmaruejol.ardoise.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R

/**
 * How far a field's label rises above its box. The label is drawn with an
 * `offset`, which does not measure, so a caller with something directly above
 * adds this to get back the gap it asked for.
 */
val FieldLabelOverhang = 8.dp

/**
 * A 4dp rounded border with the label sitting on it at all times, punched
 * out of [labelBackground].
 *
 * Material's own drops the label into an empty field; the overload that does
 * not takes a `TextFieldState`, which would undo the "state hoisted to the
 * ViewModel" rule.
 */
@Composable
fun ArdoiseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    /** What is wrong with this field, under the box, where the mistake is. */
    supportingText: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    labelBackground: Color = MaterialTheme.colorScheme.surface,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val accent = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> null
    }
    val borderColor = accent ?: MaterialTheme.colorScheme.outline
    val borderWidth = if (accent == null) 1.dp else 2.dp
    val labelColor = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
                    .heightIn(min = minHeight)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        textStyle = LocalTextStyle.current.merge(
                            MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        interactionSource = interactionSource,
                    )
                }
                trailing?.invoke()
            }

            // On the border, with the background painted behind so the border
            // appears to break for it.
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = -FieldLabelOverhang)
                    .drawBehind { drawRect(labelBackground) }
                    .padding(horizontal = 4.dp),
            )
        }
        SupportingText(text = supportingText, isError = isError)
    }
}

/** The line under a field's box, shared so "in error" looks the same everywhere. */
@Composable
private fun SupportingText(text: String?, isError: Boolean) {
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
    )
}

/**
 * A participant's name, edited in place. No border and no padding of its own:
 * Material's filled field adds 16dp, which pushed "This is me" away from the
 * name it belongs to.
 */
@Composable
fun InlineNameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    /**
     * Set on the row a screen wants the cursor in. On the field rather than
     * the row: the row is not focusable, and the name takes the keyboard.
     */
    focusRequester: FocusRequester? = null,
) {
    Box(modifier) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                ),
            enabled = enabled,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                MaterialTheme.typography.bodyLarge.copy(
                    // No border to turn red, so the name itself carries it.
                    color = when {
                        isError -> MaterialTheme.colorScheme.error
                        enabled -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.outline
                    },
                ),
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The same container as [ArdoiseTextField] for a value that is chosen rather
 * than typed. Not a read-only field: that still takes focus and shows a cursor.
 */
@Composable
fun ArdoisePickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    labelBackground: Color = MaterialTheme.colorScheme.surface,
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else null
    val borderColor = accent ?: MaterialTheme.colorScheme.outline
    val labelColor = accent ?: MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(if (accent == null) 1.dp else 2.dp, borderColor, RoundedCornerShape(4.dp))
                    .clickable(enabled = enabled, onClick = onClick)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = value.ifEmpty { placeholder.orEmpty() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.outline
                        value.isEmpty() -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 12.dp, y = -FieldLabelOverhang)
                    .drawBehind { drawRect(labelBackground) }
                    .padding(horizontal = 4.dp),
            )
        }
        SupportingText(text = supportingText, isError = isError)
    }
}
