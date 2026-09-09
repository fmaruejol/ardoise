package io.github.fmaruejol.ardoise.ui.group

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R

/**
 * The search pill and its chips: all ways of narrowing one feed, which
 * the summary line below counts together.
 */
@Composable
internal fun SearchBar(
    state: GroupUiState,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onCategoryFilter: (Int?) -> Unit,
    onPayerFilter: (String?) -> Unit,
    onDateFilter: (DateFilter) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onSearchClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(stringResource(R.string.group_search_placeholder))
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    // Clears the text but stays in search mode: leaving would
                    // throw the chips away too.
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            FilterChips(
                state = state,
                onCategoryFilter = onCategoryFilter,
                onPayerFilter = onPayerFilter,
                onDateFilter = onDateFilter,
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun FilterChips(
    state: GroupUiState,
    onCategoryFilter: (Int?) -> Unit,
    onPayerFilter: (String?) -> Unit,
    onDateFilter: (DateFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuChip(
            label = state.categoryFilterName ?: stringResource(R.string.filter_category),
            selected = state.filters.categoryId != null,
        ) { dismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_any_category)) },
                onClick = {
                    dismiss()
                    onCategoryFilter(null)
                },
            )
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        dismiss()
                        onCategoryFilter(category.id)
                    },
                )
            }
        }

        MenuChip(
            label = state.payerFilterName ?: stringResource(R.string.filter_paid_by),
            selected = state.filters.payerId != null,
        ) { dismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_anyone)) },
                onClick = {
                    dismiss()
                    onPayerFilter(null)
                },
            )
            state.participants.forEach { participant ->
                DropdownMenuItem(
                    text = { Text(participant.name) },
                    onClick = {
                        dismiss()
                        onPayerFilter(participant.id)
                    },
                )
            }
        }

        MenuChip(
            label = if (state.filters.date == DateFilter.AnyTime) {
                stringResource(R.string.filter_date)
            } else {
                stringResource(state.filters.date.label())
            },
            selected = state.filters.date != DateFilter.AnyTime,
        ) { dismiss ->
            DateFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    onClick = {
                        dismiss()
                        onDateFilter(option)
                    },
                )
            }
        }
    }
}

private fun DateFilter.label(): Int = when (this) {
    DateFilter.AnyTime -> R.string.filter_any_time
    DateFilter.ThisMonth -> R.string.filter_this_month
    DateFilter.Last30Days -> R.string.filter_last_30_days
    DateFilter.ThisYear -> R.string.filter_this_year
}

/**
 * A filter chip that opens its own menu: `FilterChip` alone is a toggle, and
 * these are choices out of a list.
 */
@Composable
private fun MenuChip(
    label: String,
    selected: Boolean,
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label) },
            leadingIcon = if (selected) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                null
            },
            trailingIcon = if (selected) {
                null
            } else {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}
