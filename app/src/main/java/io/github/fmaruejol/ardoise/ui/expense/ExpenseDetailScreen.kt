package io.github.fmaruejol.ardoise.ui.expense

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.currency.formatForEditing
import io.github.fmaruejol.ardoise.core.currency.formatSigned
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.ui.components.CategoryBadge
import io.github.fmaruejol.ardoise.ui.components.FullDate
import io.github.fmaruejol.ardoise.ui.components.InitialAvatar
import io.github.fmaruejol.ardoise.ui.components.OfflineBanner
import io.github.fmaruejol.ardoise.ui.components.rememberDateFormatter
import io.github.fmaruejol.ardoise.ui.currentLocale
import io.github.fmaruejol.ardoise.ui.describe
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.math.BigDecimal

@Composable
fun ExpenseDetailRoute(
    groupId: String,
    expenseId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ExpenseDetailViewModel =
        koinViewModel(key = "detail-$groupId-$expenseId") { parametersOf(groupId, expenseId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            viewModel.onNavigationHandled()
            currentOnBack()
        }
    }

    ExpenseDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onDeleteClick = viewModel::onDeleteClick,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

/**
 * One expense, read, or one payment. The same screen says different
 * things: the category becomes a "Reimbursement" chip, the heading
 * becomes who paid whom, and the split card becomes the two sides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    state: ExpenseDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on having read the expense, not on the absence of an error: a
    // cached expense followed by a failed refresh has both, and keying on the
    // error replaced a good expense with an error box.
    val readable = state.isLoaded && !state.notFound

    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.expense_delete_title)) },
            text = { Text(stringResource(R.string.expense_delete_body)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(stringResource(R.string.expense_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (readable) {
                        IconButton(onClick = onDeleteClick, enabled = !state.isDeleting) {
                            if (state.isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.expense_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (readable) {
                ExtendedFloatingActionButton(
                    onClick = onEdit,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.expense_edit))
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            // Nothing was ever read, so there is nothing to put a banner over.
            !readable -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.notFound) {
                        stringResource(R.string.expense_not_found)
                    } else {
                        state.loadError!!.describe()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> Column(modifier = Modifier.padding(padding)) {
                if (state.loadError != null || state.isOffline) {
                    OfflineBanner(error = state.loadError, onRetry = onRetry)
                }
                Detail(state = state, padding = PaddingValues())
            }
        }
    }
}

@Composable
private fun Detail(
    state: ExpenseDetailUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    val dateFormat = rememberDateFormatter(FullDate)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isReimbursement) {
                    ReimbursementChip()
                    // A payment names no category, so nothing here grows by
                    // itself and the chip needs pushing to the end.
                    Spacer(Modifier.weight(1f))
                } else {
                    CategoryBadge(state.category, size = 32.dp, iconSize = 18.dp)
                    Text(
                        text = state.category?.name
                            ?: stringResource(R.string.expense_category_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.recurrenceRule != RecurrenceRule.NONE) {
                    RecurrenceChip(state.recurrenceRule)
                }
            }
            Text(
                // A payment is headed by what it does: the chip already says
                // "Reimbursement", and so does its title.
                text = state.transfer?.sentence() ?: state.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.currency.format(state.amount),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
            state.original?.let { original ->
                Text(
                    text = stringResource(
                        R.string.expense_detail_converted,
                        original.currency.format(original.amount),
                        original.currency.code.orEmpty(),
                        rateText(original.rate),
                        state.currency.code.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                // The transfer card below says who paid.
                text = if (state.transfer != null) {
                    state.date.format(dateFormat)
                } else {
                    stringResource(
                        R.string.expense_detail_paid_by,
                        state.date.format(dateFormat),
                        if (state.isPaidByYou) {
                            stringResource(R.string.expense_detail_paid_by_you)
                        } else {
                            state.payerName
                        },
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (state.transfer != null) {
            TransferCard(transfer = state.transfer, state = state)
        } else {
            SplitCard(state)
        }

        if (state.recurrenceRule != RecurrenceRule.NONE) {
            DetailRow(R.drawable.ic_event_repeat) {
                DetailLabel(stringResource(R.string.expense_detail_recurrence))
                DetailValue(stringResource(state.recurrenceRule.label()))
                // Only ever a date still ahead of the reader: see `nextCopy`.
                state.nextCopy?.let { next ->
                    Text(
                        text = stringResource(
                            R.string.expense_detail_recurrence_next,
                            next.format(dateFormat),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        if (state.isReimbursement) {
            DetailRow(R.drawable.ic_info) {
                Text(
                    text = stringResource(R.string.expense_detail_reimbursement_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.notes.isNotBlank()) {
            DetailRow(R.drawable.ic_notes) {
                DetailLabel(stringResource(R.string.expense_note_label))
                DetailValue(state.notes)
            }
        }

        state.deleteError?.let { error ->
            Text(
                text = error.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * One of the lines under the card: an outline icon and what it is about. The
 * recurrence, the note and the footnote share it so they line up.
 */
@Composable
private fun DetailRow(
    @DrawableRes icon: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), content = content)
    }
}

/** What the row is about: "Recurrence", "Note". */
@Composable
private fun DetailLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What it says: "Every month", the note itself. */
@Composable
private fun DetailValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * "Monthly", beside the category. Short because it is the glance: a
 * repeating expense looks like any other row in the feed. The row under the
 * card spells the rule out.
 */
@Composable
private fun RecurrenceChip(rule: RecurrenceRule) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_event_repeat),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(rule.shortLabel()),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Who the expense reached, and for how much each. */
@Composable
private fun SplitCard(state: ExpenseDetailUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = state.splitHeading(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            state.shares.forEachIndexed { index, share ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    InitialAvatar(
                        name = share.name,
                        index = index,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text = if (share.isYou) {
                            stringResource(R.string.split_you, share.name)
                        } else {
                            share.name
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.currency.format(share.amount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The two sides of a payment, signed and coloured the way the feed
 * colours a balance change. `formatSigned` uses U+2212, a digit's width, so
 * the two lines align.
 */
@Composable
private fun TransferCard(transfer: TransferParties, state: ExpenseDetailUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.expense_detail_transfer),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            TransferSide(
                name = transfer.payerName,
                index = transfer.payerIndex,
                isYou = transfer.payerIsYou,
                caption = stringResource(R.string.expense_detail_paid),
                amount = state.currency.formatSigned(-state.amount),
                amountColour = MaterialTheme.colorScheme.error,
            )
            TransferSide(
                name = transfer.payeeName,
                index = transfer.payeeIndex,
                isYou = transfer.payeeIsYou,
                caption = stringResource(R.string.expense_detail_received),
                amount = state.currency.formatSigned(state.amount),
                amountColour = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TransferSide(
    name: String,
    index: Int,
    isYou: Boolean,
    caption: String,
    amount: String,
    amountColour: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InitialAvatar(name = name, index = index, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isYou) stringResource(R.string.split_you, name) else name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(text = amount, style = MaterialTheme.typography.bodyLarge, color = amountColour)
    }
}

/** A stored rate as the reader writes numbers. */
@Composable
private fun rateText(stored: String): String {
    val rate = remember(stored) { runCatching { BigDecimal(stored) }.getOrNull() }
    return rate?.let { formatForEditing(it, locale = currentLocale()) } ?: stored
}

/** The pill that replaces the category row on a payment. */
@Composable
private fun ReimbursementChip() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_handshake),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.expense_detail_reimbursement),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** "Chloé pays Ben", settle up's own three strings, for the same payment. */
@Composable
private fun TransferParties.sentence(): String = when {
    payeeIsYou -> stringResource(R.string.settle_pays_you, payerName)
    payerIsYou -> stringResource(R.string.settle_you_pay, payeeName)
    else -> stringResource(R.string.settle_pays, payerName, payeeName)
}

/** "Split evenly, 5 ways", the mode and how many people it reached. */
@Composable
private fun ExpenseDetailUiState.splitHeading(): String {
    val count = shares.size
    return when (splitMode) {
        SplitMode.EVENLY -> {
            pluralStringResource(R.plurals.expense_detail_split_evenly, count, count)
        }

        SplitMode.BY_AMOUNT -> {
            pluralStringResource(R.plurals.expense_detail_split_amount, count, count)
        }

        SplitMode.BY_SHARES -> {
            pluralStringResource(R.plurals.expense_detail_split_shares, count, count)
        }

        SplitMode.BY_PERCENTAGE -> {
            pluralStringResource(R.plurals.expense_detail_split_percent, count, count)
        }
    }
}
