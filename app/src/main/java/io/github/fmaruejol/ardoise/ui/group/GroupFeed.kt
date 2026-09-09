package io.github.fmaruejol.ardoise.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.currency.formatSigned
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.ui.components.CategoryBadge
import io.github.fmaruejol.ardoise.ui.components.DayHeading
import io.github.fmaruejol.ardoise.ui.components.IdentityPrompt
import io.github.fmaruejol.ardoise.ui.components.PendingUploadSection
import io.github.fmaruejol.ardoise.ui.components.SectionLabel
import io.github.fmaruejol.ardoise.ui.components.rememberDateFormatter

/**
 * The feed: the pending section, the totals card or the search summary, then
 * the expenses under a heading per day. The rows and the card read their two
 * numbers from `:core`; neither is computed here.
 */
@Composable
internal fun Feed(
    state: GroupUiState,
    onExpenseClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPickYouOpen: () -> Unit,
    onPendingClick: (String) -> Unit,
) {
    // "Saturday, 12 September".
    val dayHeader = rememberDateFormatter(DayHeading)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
    ) {
        // First of everything, and outside the `isFiltered` branch: hiding a
        // queued expense behind a filter would hide the one thing on the
        // screen the server has never heard of.
        if (state.pending.isNotEmpty()) {
            item("pending") {
                PendingUploadSection(
                    pending = state.pending,
                    currency = state.currency,
                    categories = state.allCategories,
                    onClick = onPendingClick,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
            }
        }

        if (!state.isFiltered) {
            // Above the totals, because it explains why half of them are
            // missing. Not over search results, which it is not about.
            if (state.needsIdentity) {
                item("identity") {
                    IdentityPrompt(
                        onChoose = onPickYouOpen,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item { TotalsCard(state) }
        } else {
            item {
                Text(
                    // Of what matched, and without reimbursements, like the
                    // totals card.
                    text = pluralStringResource(
                        R.plurals.filter_matches,
                        state.expenseCount,
                        state.expenseCount,
                        state.currency.format(state.totalSpent),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp),
                )
            }
        }

        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                SectionLabel(
                    text = day.date.format(dayHeader),
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                )
            }
            items(day.expenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    currency = state.currency,
                    balanceChange = state.yourBalanceChanges[expense.id],
                    onClick = { onExpenseClick(expense.id) },
                )
            }
        }

        if (state.hasNoMatches) {
            item {
                Text(
                    text = if (state.query.isBlank()) {
                        stringResource(R.string.filter_none)
                    } else {
                        stringResource(R.string.group_search_none, state.query.trim())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (state.hasMore) {
            item {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.group_load_more))
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(state: GroupUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Total(
                label = stringResource(R.string.group_total_spent),
                value = state.currency.format(state.totalSpent),
                modifier = Modifier.weight(1f),
            )
            // The second half only exists once there is a "your" to compute.
            state.yourShare?.let { share ->
                // Vertical: a HorizontalDivider in a 1dp box draws a dot.
                VerticalDivider(modifier = Modifier.height(48.dp))
                Total(
                    label = stringResource(R.string.group_your_share),
                    value = state.currency.format(share),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Total(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One expense. The line under the amount is **what it did to your balance**,
 * not your share: paying for four other people is a large share and a large
 * gain at once. Null while nobody has said which participant they are.
 */
@Composable
private fun ExpenseRow(
    expense: ExpenseSummary,
    currency: GroupCurrency,
    balanceChange: Long?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CategoryBadge(category = expense.category)
        Column(Modifier.weight(1f)) {
            Text(
                text = expense.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.group_expense_subtitle,
                    expense.paidBy.name,
                    pluralStringResource(
                        R.plurals.group_expense_split,
                        expense.paidFor.size,
                        expense.paidFor.size,
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = currency.format(expense.amount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (balanceChange != null) {
                Text(
                    text = currency.formatSigned(balanceChange),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        balanceChange > 0 -> MaterialTheme.colorScheme.primary

                        balanceChange < 0 -> MaterialTheme.colorScheme.error

                        // Nothing changed hands as far as you are concerned.
                        else -> MaterialTheme.colorScheme.outline
                    },
                )
            }
        }
    }
}
