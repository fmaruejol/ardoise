package io.github.fmaruejol.ardoise.ui.group

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.components.IdentityPrompt
import io.github.fmaruejol.ardoise.ui.components.OfflineBanner
import io.github.fmaruejol.ardoise.ui.components.ParticipantPickerDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GroupRoute(
    groupId: String,
    onBack: () -> Unit,
    onExpenseClick: (String) -> Unit,
    onAddExpense: () -> Unit,
    onGroupSettings: () -> Unit,
    onPendingClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: GroupViewModel = koinViewModel(key = "group-$groupId") { parametersOf(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // One of the two moments the app knows it might be online again.
    LaunchedEffect(Unit) { viewModel.onSendQueued() }

    GroupScreen(
        state = state,
        onBack = onBack,
        onExpenseClick = onExpenseClick,
        onAddExpense = onAddExpense,
        onGroupSettings = onGroupSettings,
        onSearchOpen = viewModel::onSearchOpen,
        onSearchClose = viewModel::onSearchClose,
        onQueryChange = viewModel::onQueryChange,
        onPickYouOpen = viewModel::onPickYouOpen,
        onPendingClick = onPendingClick,
        onPickYouDismiss = viewModel::onPickYouDismiss,
        onYouChange = viewModel::onYouChange,
        onCategoryFilter = viewModel::onCategoryFilter,
        onPayerFilter = viewModel::onPayerFilter,
        onDateFilter = viewModel::onDateFilter,
        onLoadMore = viewModel::onLoadMore,
        onRetry = viewModel::onRefresh,
        modifier = modifier,
        bottomBar = bottomBar,
    )
}

/**
 * A group's expenses. The bottom bar is passed in as [bottomBar] by
 * `GroupHost`, so this screen keeps exactly one `Scaffold`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    state: GroupUiState,
    onBack: () -> Unit,
    onExpenseClick: (String) -> Unit,
    onAddExpense: () -> Unit,
    onGroupSettings: () -> Unit,
    onPendingClick: (String) -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPickYouOpen: () -> Unit,
    onPickYouDismiss: () -> Unit,
    onYouChange: (Int) -> Unit,
    onCategoryFilter: (Int?) -> Unit,
    onPayerFilter: (String?) -> Unit,
    onDateFilter: (DateFilter) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    if (state.pickingYou) {
        // The same dialog group settings uses: both write the one preference.
        ParticipantPickerDialog(
            options = state.participants.map { it.name },
            selectedIndex = state.youIndex,
            onSelect = onYouChange,
            onDismiss = onPickYouDismiss,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (state.isSearching) {
                SearchBar(
                    state = state,
                    onQueryChange = onQueryChange,
                    onSearchClose = onSearchClose,
                    onCategoryFilter = onCategoryFilter,
                    onPayerFilter = onPayerFilter,
                    onDateFilter = onDateFilter,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(state.groupName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSearchOpen) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = stringResource(R.string.group_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        GroupOverflowMenu(onGroupSettings = onGroupSettings)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (!state.isEmpty && !state.isLoading && !state.isSearching) {
                FloatingActionButton(
                    onClick = onAddExpense,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.expense_add),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Above the feed rather than in it: it is about all the rows
            // below, and a row of its own would scroll away from them.
            if (state.error != null || state.isOffline) {
                OfflineBanner(error = state.error, onRetry = onRetry)
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    state.isEmpty -> Column(Modifier.fillMaxSize()) {
                        if (state.needsIdentity) {
                            IdentityPrompt(
                                onChoose = onPickYouOpen,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            NoExpensesYet(
                                onAddExpense = onAddExpense,
                                onInvite = onGroupSettings,
                            )
                        }
                    }

                    else -> Feed(
                        state = state,
                        onExpenseClick = onExpenseClick,
                        onLoadMore = onLoadMore,
                        onPickYouOpen = onPickYouOpen,
                        onPendingClick = onPendingClick,
                    )
                }
            }
        }
    }
}
