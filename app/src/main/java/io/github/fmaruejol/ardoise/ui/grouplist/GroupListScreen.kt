package io.github.fmaruejol.ardoise.ui.grouplist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.ui.components.EmptyMessage
import io.github.fmaruejol.ardoise.ui.components.EmptyStateMark
import io.github.fmaruejol.ardoise.ui.components.OfflineBanner
import io.github.fmaruejol.ardoise.ui.components.SectionLabel
import io.github.fmaruejol.ardoise.ui.describe
import org.koin.androidx.compose.koinViewModel
import kotlin.math.abs

/** Below this many groups a search field costs a tap and saves none. */
private const val SEARCH_THRESHOLD = 5

@Composable
fun GroupListRoute(
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GroupListScreen(
        state = state,
        onGroupClick = onGroupClick,
        onCreateGroup = onCreateGroup,
        onJoinGroup = onJoinGroup,
        onSettings = onSettings,
        onRefresh = viewModel::onRefresh,
        onSearchOpen = viewModel::onSearchOpen,
        onSearchClose = viewModel::onSearchClose,
        onQueryChange = viewModel::onQueryChange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    state: GroupListUiState,
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearching) {
                        SearchField(query = state.query, onQueryChange = onQueryChange)
                    } else {
                        Text(stringResource(R.string.group_list_title))
                    }
                },
                actions = {
                    if (state.isSearching) {
                        IconButton(onClick = onSearchClose) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    } else {
                        if (state.groups.size > SEARCH_THRESHOLD) {
                            IconButton(onClick = onSearchOpen) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = stringResource(R.string.group_list_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = onSettings) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.group_list_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (!state.isEmpty && !state.isLoading) {
                // The other way in. Smaller and in the secondary container,
                // because starting a group is the commoner thing.
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JoinGroupFab(onClick = onJoinGroup)
                    ExtendedFloatingActionButton(
                        onClick = onCreateGroup,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(stringResource(R.string.group_list_new))
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.isEmpty -> NoGroupsYet(
                    onCreateGroup = onCreateGroup,
                    onJoinGroup = onJoinGroup,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> GroupList(
                    state = state,
                    onGroupClick = onGroupClick,
                    onRetry = onRefresh,
                )
            }
        }
    }
}

/**
 * The smaller of the two FABs. Not `ExtendedFloatingActionButton`, which
 * is 56dp tall whatever is in it; this one is 40dp.
 */
@Composable
private fun JoinGroupFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_group_add),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.group_list_join),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.group_list_search_placeholder)) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun GroupList(
    state: GroupListUiState,
    onGroupClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Above the list, not instead of it: a failed refresh should not hide
        // groups the user was already reading.
        if (state.error != null || state.isOffline) {
            item {
                OfflineBanner(error = state.error, onRetry = onRetry)
            }
        }

        if (state.groups.isNotEmpty()) {
            item {
                SectionLabel(
                    text = pluralStringResource(
                        R.plurals.group_list_count,
                        state.groups.size,
                        state.groups.size,
                    ),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }

        items(state.visibleGroups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                position = state.positions[group.id],
                onClick = { onGroupClick(group.id) },
            )
        }

        if (state.hasNoMatches) {
            item {
                Text(
                    text = stringResource(R.string.group_list_no_matches, state.query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/**
 * One group, and where the user stands in it.
 *
 * **No leading tile**: the name is what tells one group from another.
 *
 * **The standing is absent, not zero, when there is nothing to say**,
 * because nobody has said which participant they are, or the read failed. Zero already means
 * settled, so neither can borrow it.
 *
 * The subtitle says "5 people" and not "5 people · 7 expenses":
 * `groups.list` returns no expense count.
 */
@Composable
private fun GroupCard(group: GroupSummary, position: Long?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.group_list_participants,
                            group.participantCount,
                            group.participantCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (position != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Position(group = group, amount = position)
            }
        }
    }
}

/**
 * "You are owed €84.20", or "Settled up". In the group's own currency and
 * never added to another's. The sign is in the words and the colour, which is
 * the pair the feed uses for a balance change.
 */
@Composable
private fun Position(group: GroupSummary, amount: Long) {
    val currency = GroupCurrency.of(group.currencyCode, group.currencySymbol)
    if (amount == 0L) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.group_list_settled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(
                if (amount > 0) R.string.group_list_owed else R.string.group_list_owes,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = currency.format(abs(amount)),
            style = MaterialTheme.typography.titleLarge,
            color = if (amount > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun NoGroupsYet(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        EmptyStateMark()

        EmptyMessage(
            title = stringResource(R.string.group_list_empty_title),
            body = stringResource(R.string.group_list_empty_body),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.group_list_empty_create),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(onClick = onJoinGroup, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.group_list_empty_join),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
