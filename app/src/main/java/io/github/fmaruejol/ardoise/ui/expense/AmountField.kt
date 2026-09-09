package io.github.fmaruejol.ardoise.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.ui.components.CurrencyChip

/**
 * The amount, big and centred, with the currency it is typed in beside it.
 * Read back through that currency, never a `Double`: `12.34` is `1234` minor
 * units in euros and `1234` in yen.
 *
 * The chip replaced the currency symbol, which says what the amount is in
 * *and* changes it, so there is no side for a symbol to go on.
 */
@Composable
internal fun AmountField(
    state: ExpenseFormUiState,
    onAmountChange: (String) -> Unit,
    onCurrencyPickerOpen: () -> Unit,
    onCurrencyPickerDismiss: () -> Unit,
    onCurrencyChange: (String) -> Unit,
    onRateOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The keyboard comes up on a blank form, where the amount is what the user
    // came to type. Not on a prefilled payment, which is here to be checked.
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.isNew, state.isLoading, state.isPrefilled) {
        if (state.isNew && !state.isLoading && !state.isPrefilled) focus.requestFocus()
    }

    // Nothing here has a border, so the number itself goes red.
    val isError = ExpenseFormError.Amount in state.errors
    val amountColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                CurrencyChip(
                    label = state.entryCurrency.code ?: state.entryCurrency.symbol,
                    enabled = state.canChooseCurrency && !state.isSaving,
                    onClick = onCurrencyPickerOpen,
                    modifier = Modifier.padding(end = 10.dp),
                )
                DropdownMenu(
                    expanded = state.pickingEntryCurrency,
                    onDismissRequest = onCurrencyPickerDismiss,
                ) {
                    GroupCurrency.SUPPORTED_CODES.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = { onCurrencyChange(code) },
                        )
                    }
                }
            }

            // A BasicTextField takes the whole width it is offered; measuring
            // the digits is what keeps it centred with the chip.
            val style = MaterialTheme.typography.displayLarge
            val measurer = rememberTextMeasurer()
            val shown = state.amountText.ifEmpty { stringResource(R.string.expense_amount_hint) }
            val width = with(LocalDensity.current) {
                measurer.measure(shown, style).size.width.toDp()
            }

            BasicTextField(
                value = state.amountText,
                onValueChange = onAmountChange,
                modifier = Modifier
                    .focusRequester(focus)
                    .width(width + CARET_ROOM),
                enabled = !state.isSaving,
                singleLine = true,
                textStyle = style.copy(color = amountColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                decorationBox = { field ->
                    Box {
                        if (state.amountText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.expense_amount_hint),
                                style = style,
                                // The placeholder goes red too: with nothing
                                // typed it *is* the amount on screen.
                                color = if (isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                        }
                        field()
                    }
                },
            )
        }

        // What the group is actually charged, and what made it that.
        if (state.isConverted) {
            ConversionLine(state = state, onClick = onRateOpen)
        }

        val complaint = when {
            isError -> stringResource(R.string.expense_error_amount)
            ExpenseFormError.Rate in state.errors -> stringResource(R.string.expense_error_rate)
            else -> null
        }
        if (complaint != null) {
            Text(
                text = complaint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * "= €96.43 · 1 BRL = 0.17857 EUR", and the way into changing the rate.
 * **"1 original = rate group"**, upstream's direction and the stored one, so
 * the web client shows the same number rather than its reciprocal.
 */
@Composable
private fun ConversionLine(state: ExpenseFormUiState, onClick: () -> Unit) {
    val rate = state.conversionRate
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClick,
            enabled = !state.isSaving,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (rate == null) {
                        stringResource(R.string.expense_rate_needed)
                    } else {
                        stringResource(
                            R.string.expense_converted,
                            state.currency.format(state.amount),
                            state.entryCurrency.code.orEmpty(),
                            state.conversionRateText,
                            state.currency.code.orEmpty(),
                        )
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Room for the caret past the last digit, so it is never clipped. */
private val CARET_ROOM = 3.dp
