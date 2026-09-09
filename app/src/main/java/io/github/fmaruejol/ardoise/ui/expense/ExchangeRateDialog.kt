package io.github.fmaruejol.ardoise.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.convertToGroupCurrency
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.currency.parseConversionRate
import io.github.fmaruejol.ardoise.ui.components.ArdoiseTextField
import io.github.fmaruejol.ardoise.ui.components.FullDate
import io.github.fmaruejol.ardoise.ui.components.rememberDateFormatter
import java.time.LocalDate

/**
 * The exchange rate for one expense. **"1 [original] = rate [group]"**,
 * upstream's direction and the one its rate source is queried in, so this is
 * the number the web client shows for the same expense.
 */
@Composable
internal fun ExchangeRateDialog(
    state: ExpenseFormUiState,
    onDismiss: () -> Unit,
    onRateChange: (String) -> Unit,
    onLookUp: () -> Unit,
    onSave: () -> Unit,
) {
    val from = state.entryCurrency.code.orEmpty()
    val into = state.currency.code.orEmpty()
    val draft = parseConversionRate(state.rateDraft)
    val converted = draft?.let {
        convertToGroupCurrency(
            originalAmount = state.enteredAmount ?: 0L,
            originalCurrency = state.entryCurrency,
            rate = it,
            groupCurrency = state.currency,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rate_title)) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.rate_body, into, from),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ArdoiseTextField(
                    value = state.rateDraft,
                    onValueChange = onRateChange,
                    label = stringResource(R.string.rate_label, from),
                    // The dialog's own surface, so the label punches out of
                    // the right colour.
                    labelBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
                    isError = state.rateDraft.isNotEmpty() && draft == null,
                    supportingText = stringResource(R.string.rate_unavailable)
                        .takeIf { state.rateUnavailable },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailing = {
                        Text(
                            text = into,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                // What the group will be charged.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.rate_becomes,
                            state.entryCurrency.format(state.enteredAmount ?: 0L),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = converted?.let { state.currency.format(it) }
                            ?: stringResource(R.string.group_your_share_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                OutlinedButton(onClick = onLookUp, enabled = !state.fetchingRate) {
                    if (state.fetchingRate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = lookUpLabel(state.date),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft != null) {
                Text(stringResource(R.string.expense_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/**
 * "Use today's rate", or the day the expense is dated: the rate looked up is
 * the one that applied when the money was spent, as upstream asks for it.
 */
@Composable
private fun lookUpLabel(date: LocalDate): String = if (date == LocalDate.now()) {
    stringResource(R.string.rate_use_today)
} else {
    stringResource(R.string.rate_use_on, date.format(rememberDateFormatter(FullDate)))
}
