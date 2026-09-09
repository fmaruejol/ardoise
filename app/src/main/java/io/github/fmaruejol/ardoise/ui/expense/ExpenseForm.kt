package io.github.fmaruejol.ardoise.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.ui.components.ArdoiseTextField
import io.github.fmaruejol.ardoise.ui.components.CategoryBadge
import io.github.fmaruejol.ardoise.ui.components.FieldLabelOverhang
import io.github.fmaruejol.ardoise.ui.components.InitialAvatar
import io.github.fmaruejol.ardoise.ui.components.ShortDate
import io.github.fmaruejol.ardoise.ui.components.rememberDateFormatter
import io.github.fmaruejol.ardoise.ui.components.rememberFormScroller
import io.github.fmaruejol.ardoise.ui.components.scrollContainer
import io.github.fmaruejol.ardoise.ui.components.scrollTarget
import io.github.fmaruejol.ardoise.ui.describe

/**
 * The form's body. Split out of [ExpenseFormScreen], which keeps the
 * `Scaffold`, the two states that replace the form and the sheets over it.
 */
@Composable
internal fun Form(
    state: ExpenseFormUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyPickerOpen: () -> Unit,
    onCurrencyPickerDismiss: () -> Unit,
    onCurrencyChange: (String) -> Unit,
    onRateOpen: () -> Unit,
    onNotesChange: (String) -> Unit,
    onNotesToggle: () -> Unit,
    onCategoryPickerOpen: () -> Unit,
    onDatePickerOpen: () -> Unit,
    onPayerPickerOpen: () -> Unit,
    onPayerPickerDismiss: () -> Unit,
    onPayerChange: (String) -> Unit,
    onSplitEditorOpen: () -> Unit,
    onRecurrenceOpen: () -> Unit,
    onReimbursementChange: (Boolean) -> Unit,
) {
    val scroller = rememberFormScroller(rememberScrollState())

    // A refused save marks every field that is wrong; this brings the first
    // into view. Keyed on the count, so saving twice unchanged scrolls twice.
    LaunchedEffect(state.refusedSaves) {
        if (state.refusedSaves > 0) {
            state.firstError?.let { scroller.scrollTo(it.scrollKey) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scroller.state)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scrollContainer(scroller),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AmountField(
            state = state,
            onAmountChange = onAmountChange,
            onCurrencyPickerOpen = onCurrencyPickerOpen,
            onCurrencyPickerDismiss = onCurrencyPickerDismiss,
            onCurrencyChange = onCurrencyChange,
            onRateOpen = onRateOpen,
            modifier = Modifier.scrollTarget(scroller, ScrollKey.Amount),
        )

        ArdoiseTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.expense_title_label),
            enabled = !state.isSaving,
            isError = ExpenseFormError.Title in state.errors,
            supportingText = state.errorOf(ExpenseFormError.Title)?.describe(state),
            modifier = Modifier.scrollTarget(scroller, ScrollKey.Title),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTile(
                label = stringResource(R.string.expense_category),
                value = state.category?.name ?: stringResource(R.string.expense_category_none),
                onClick = onCategoryPickerOpen,
                modifier = Modifier.weight(1f),
                leading = { CategoryBadge(state.category, size = 32.dp, iconSize = 20.dp) },
            )
            FormTile(
                label = stringResource(R.string.expense_date),
                value = state.date.format(rememberDateFormatter(ShortDate)),
                onClick = onDatePickerOpen,
                modifier = Modifier.weight(1f),
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_event),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                },
            )
        }

        Box {
            FormTile(
                label = stringResource(R.string.expense_paid_by),
                value = state.payer?.name.orEmpty(),
                onClick = onPayerPickerOpen,
                leading = {
                    val payer = state.payer
                    if (payer != null) {
                        InitialAvatar(
                            name = payer.name,
                            index = state.participants.indexOfFirst { it.id == payer.id },
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                trailing = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                },
            )
            DropdownMenu(expanded = state.pickingPayer, onDismissRequest = onPayerPickerDismiss) {
                state.participants.forEach { participant ->
                    DropdownMenuItem(
                        text = { Text(participant.name) },
                        onClick = { onPayerChange(participant.id) },
                    )
                }
            }
        }

        FormTile(
            label = stringResource(R.string.expense_split),
            value = state.splitSummary(),
            onClick = onSplitEditorOpen,
            isError = state.splitError != null,
            supportingText = state.splitError?.describe(state),
            modifier = Modifier.scrollTarget(scroller, ScrollKey.Split),
            leading = {
                Icon(
                    painter = painterResource(R.drawable.ic_call_split),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            },
        )

        ReimbursementRow(
            checked = state.isReimbursement,
            enabled = !state.isSaving,
            onCheckedChange = onReimbursementChange,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Filled once set, so the form says what it will do.
            Chip(
                icon = R.drawable.ic_repeat,
                label = if (state.recurrenceRule == RecurrenceRule.NONE) {
                    stringResource(R.string.repeat_chip)
                } else {
                    stringResource(state.recurrenceRule.label())
                },
                onClick = onRecurrenceOpen,
                selected = state.recurrenceRule != RecurrenceRule.NONE,
            )
            if (!state.showNotes) {
                Chip(
                    icon = R.drawable.ic_notes,
                    label = stringResource(R.string.expense_note),
                    onClick = onNotesToggle,
                )
            }
        }

        if (state.showNotes) {
            ArdoiseTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = stringResource(R.string.expense_note_label),
                enabled = !state.isSaving,
                singleLine = false,
                minHeight = 72.dp,
                // The label hangs above the field's own top, and the chips are
                // directly above it.
                modifier = Modifier.padding(top = FieldLabelOverhang),
            )
        }

        // Only what belongs to no field: the rest is marked where it happened.
        state.errors.filterIsInstance<ExpenseFormError.Failed>().firstOrNull()?.let { error ->
            Text(
                text = error.describe(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Whether this is somebody paying somebody back: it moves balances but is not spending. */
@Composable
private fun ReimbursementRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_handshake),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.expense_reimbursement),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.expense_reimbursement_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** The three places on the form a save can send the user back to. */
private enum class ScrollKey { Amount, Title, Split }

/** Which of them an error belongs to. */
private val ExpenseFormError.scrollKey: ScrollKey
    get() = when (this) {
        // The rate is drawn under the amount and is about it.
        ExpenseFormError.Amount, ExpenseFormError.Rate -> ScrollKey.Amount

        ExpenseFormError.Title -> ScrollKey.Title

        else -> ScrollKey.Split
    }

/** What is wrong with the split, which is the tile that opens the editor. */
private val ExpenseFormUiState.splitError: ExpenseFormError?
    get() = errors.firstOrNull {
        it is ExpenseFormError.NobodyPaidFor || it is ExpenseFormError.SplitTotal
    }

@Composable
private fun Chip(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(8.dp),
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp),
                    )
                },
            )
            .clickable(onClick = onClick)
            .height(32.dp)
            .padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
