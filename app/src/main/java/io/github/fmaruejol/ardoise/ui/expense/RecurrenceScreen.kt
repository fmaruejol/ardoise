package io.github.fmaruejol.ardoise.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.recurrence.nextOccurrences
import io.github.fmaruejol.ardoise.ui.components.FullDate
import io.github.fmaruejol.ardoise.ui.components.rememberDateFormatter

/**
 * How often an expense repeats.
 *
 * **The rule set is the server's: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`.** A
 * yearly rule would come back rejected. The dates are predictions from
 * `:core`'s `nextOccurrences`, a port of the server's own arithmetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceScreen(
    state: ExpenseFormUiState,
    onBack: () -> Unit,
    onRecurrenceChange: (RecurrenceRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repeat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.split_done)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
                .padding(bottom = 24.dp),
        ) {
            Column(Modifier.selectableGroup()) {
                RecurrenceRule.entries.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        selected = rule == state.recurrenceRule,
                        onSelect = { onRecurrenceChange(rule) },
                    )
                }
            }

            if (state.recurrenceRule != RecurrenceRule.NONE) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                NextOccurrences(state)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_schedule),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.repeat_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RecurrenceRule, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
                RoundedCornerShape(12.dp),
            )
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(rule.label()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun NextOccurrences(state: ExpenseFormUiState) {
    val formatter = rememberDateFormatter(FullDate)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.repeat_next),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        nextOccurrences(state.date, state.recurrenceRule, PREVIEW_COUNT).forEach { date ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_event_repeat),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = date.format(formatter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.currency.format(state.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun RecurrenceRule.label(): Int = when (this) {
    RecurrenceRule.NONE -> R.string.repeat_none
    RecurrenceRule.DAILY -> R.string.repeat_daily
    RecurrenceRule.WEEKLY -> R.string.repeat_weekly
    RecurrenceRule.MONTHLY -> R.string.repeat_monthly
}

/** The same rules in one word, for the detail screen's chip. No chip is drawn for `NONE`. */
internal fun RecurrenceRule.shortLabel(): Int = when (this) {
    RecurrenceRule.NONE -> R.string.repeat_none
    RecurrenceRule.DAILY -> R.string.repeat_short_daily
    RecurrenceRule.WEEKLY -> R.string.repeat_short_weekly
    RecurrenceRule.MONTHLY -> R.string.repeat_short_monthly
}

/** Three: enough to see the pattern, not a calendar. */
private const val PREVIEW_COUNT = 3
