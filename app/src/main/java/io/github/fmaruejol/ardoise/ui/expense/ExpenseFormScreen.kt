package io.github.fmaruejol.ardoise.ui.expense

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ExpenseFormRoute(
    groupId: String,
    expenseId: String?,
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set when opening an expense that is still waiting to be sent. */
    pendingId: String? = null,
    /** Set when the form was opened from "Mark as paid" on the settle-up screen. */
    prefill: ReimbursementPrefill? = null,
) {
    val viewModel: ExpenseFormViewModel =
        koinViewModel(key = "expense-$groupId-$expenseId-$pendingId") {
            parametersOf(groupId, expenseId, pendingId, prefill)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val queuedMessage = stringResource(R.string.outbox_queued)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.savedId, state.isDeleted) {
        if (state.savedId != null || state.isDeleted) {
            // A save that quietly did nothing would be worse than one that
            // failed loudly.
            if (state.wasQueued) {
                Toast.makeText(context, queuedMessage, Toast.LENGTH_LONG).show()
            }
            viewModel.onNavigationHandled()
            currentOnDone()
        }
    }

    ExpenseFormScreen(
        state = state,
        onClose = onClose,
        onSave = viewModel::onSave,
        onTitleChange = viewModel::onTitleChange,
        onAmountChange = viewModel::onAmountChange,
        onCurrencyPickerOpen = viewModel::onEntryCurrencyPickerOpen,
        onCurrencyPickerDismiss = viewModel::onEntryCurrencyPickerDismiss,
        onCurrencyChange = viewModel::onEntryCurrencyChange,
        onRateOpen = viewModel::onRateOpen,
        onRateDismiss = viewModel::onRateDismiss,
        onRateDraftChange = viewModel::onRateDraftChange,
        onRateLookUp = viewModel::onRateLookUp,
        onRateSave = viewModel::onRateSave,
        onNotesChange = viewModel::onNotesChange,
        onNotesToggle = viewModel::onNotesToggle,
        onCategoryPickerOpen = viewModel::onCategoryPickerOpen,
        onCategoryPickerDismiss = viewModel::onCategoryPickerDismiss,
        onCategoryChange = viewModel::onCategoryChange,
        onDatePickerOpen = viewModel::onDatePickerOpen,
        onDatePickerDismiss = viewModel::onDatePickerDismiss,
        onDateChange = viewModel::onDateChange,
        onPayerPickerOpen = viewModel::onPayerPickerOpen,
        onPayerPickerDismiss = viewModel::onPayerPickerDismiss,
        onPayerChange = viewModel::onPayerChange,
        onSplitEditorOpen = viewModel::onSplitEditorOpen,
        onSplitEditorClose = viewModel::onSplitEditorClose,
        onRecurrenceOpen = viewModel::onRecurrenceOpen,
        onRecurrenceClose = viewModel::onRecurrenceClose,
        onRecurrenceChange = viewModel::onRecurrenceChange,
        onReimbursementChange = viewModel::onReimbursementChange,
        onSplitModeChange = viewModel::onSplitModeChange,
        onParticipantToggle = viewModel::onParticipantToggle,
        onShareChange = viewModel::onShareChange,
        modifier = modifier,
    )
}

/**
 * The expense form. Full screen rather than a sheet, and the amount takes
 * the focus on open, except on a payment from "Mark as paid", where it is
 * already filled in and the keyboard would cover the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(
    state: ExpenseFormUiState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyPickerOpen: () -> Unit,
    onCurrencyPickerDismiss: () -> Unit,
    onCurrencyChange: (String) -> Unit,
    onRateOpen: () -> Unit,
    onRateDismiss: () -> Unit,
    onRateDraftChange: (String) -> Unit,
    onRateLookUp: () -> Unit,
    onRateSave: () -> Unit,
    onNotesChange: (String) -> Unit,
    onNotesToggle: () -> Unit,
    onCategoryPickerOpen: () -> Unit,
    onCategoryPickerDismiss: () -> Unit,
    onCategoryChange: (Int) -> Unit,
    onDatePickerOpen: () -> Unit,
    onDatePickerDismiss: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onPayerPickerOpen: () -> Unit,
    onPayerPickerDismiss: () -> Unit,
    onPayerChange: (String) -> Unit,
    onSplitEditorOpen: () -> Unit,
    onSplitEditorClose: () -> Unit,
    onRecurrenceOpen: () -> Unit,
    onRecurrenceClose: () -> Unit,
    onRecurrenceChange: (RecurrenceRule) -> Unit,
    onReimbursementChange: (Boolean) -> Unit,
    onSplitModeChange: (SplitMode) -> Unit,
    onParticipantToggle: (String) -> Unit,
    onShareChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.editingRecurrence) {
        RecurrenceScreen(
            state = state,
            onBack = onRecurrenceClose,
            onRecurrenceChange = onRecurrenceChange,
            modifier = modifier,
        )
        return
    }

    if (state.editingSplit) {
        SplitEditorScreen(
            state = state,
            onBack = onSplitEditorClose,
            onSplitModeChange = onSplitModeChange,
            onParticipantToggle = onParticipantToggle,
            onShareChange = onShareChange,
            modifier = modifier,
        )
        return
    }

    // The rate, over the form: one number about the amount above it, which
    // it needs in view.
    if (state.editingRate) {
        ExchangeRateDialog(
            state = state,
            onDismiss = onRateDismiss,
            onRateChange = onRateDraftChange,
            onLookUp = onRateLookUp,
            onSave = onRateSave,
        )
    }

    if (state.pickingDate) {
        DatePickerSheet(
            date = state.date,
            onDismiss = onDatePickerDismiss,
            onSelect = onDateChange,
        )
    }

    if (state.pickingCategory) {
        CategorySheet(
            categories = state.categories,
            selectedId = state.categoryId,
            onSelect = onCategoryChange,
            onDismiss = onCategoryPickerDismiss,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.expense_new_title else R.string.expense_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    // Deleting lives on the detail screen, which is the
                    // only way into an edit anyway.
                    TextButton(onClick = onSave, enabled = state.canSave) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.expense_save))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.expense_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> Form(
                state = state,
                padding = padding,
                onTitleChange = onTitleChange,
                onAmountChange = onAmountChange,
                onCurrencyPickerOpen = onCurrencyPickerOpen,
                onCurrencyPickerDismiss = onCurrencyPickerDismiss,
                onCurrencyChange = onCurrencyChange,
                onRateOpen = onRateOpen,
                onNotesChange = onNotesChange,
                onNotesToggle = onNotesToggle,
                onCategoryPickerOpen = onCategoryPickerOpen,
                onDatePickerOpen = onDatePickerOpen,
                onPayerPickerOpen = onPayerPickerOpen,
                onPayerPickerDismiss = onPayerPickerDismiss,
                onPayerChange = onPayerChange,
                onSplitEditorOpen = onSplitEditorOpen,
                onRecurrenceOpen = onRecurrenceOpen,
                onReimbursementChange = onReimbursementChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        // The picker works in UTC midnight; an expense date is
                        // a calendar day.
                        onSelect(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.split_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = picker)
    }
}
