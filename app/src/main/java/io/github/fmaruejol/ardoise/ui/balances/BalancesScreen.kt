package io.github.fmaruejol.ardoise.ui.balances

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.currency.formatSigned
import io.github.fmaruejol.ardoise.ui.components.IdentityPrompt
import io.github.fmaruejol.ardoise.ui.components.InitialAvatar
import io.github.fmaruejol.ardoise.ui.components.OfflineBanner
import io.github.fmaruejol.ardoise.ui.components.ParticipantPickerDialog
import io.github.fmaruejol.ardoise.ui.group.GroupOverflowMenu
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BalancesRoute(
    groupId: String,
    onBack: () -> Unit,
    onSettleUp: () -> Unit,
    onTotals: () -> Unit,
    onGroupSettings: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: BalancesViewModel =
        koinViewModel(key = "balances-$groupId") { parametersOf(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    BalancesScreen(
        state = state,
        onBack = onBack,
        onSettleUp = onSettleUp,
        onTotals = onTotals,
        onGroupSettings = onGroupSettings,
        onRetry = viewModel::onRetry,
        onPickYouOpen = viewModel::onPickYouOpen,
        onPickYouDismiss = viewModel::onPickYouDismiss,
        onYouChange = viewModel::onYouChange,
        modifier = modifier,
        bottomBar = bottomBar,
    )
}

/**
 * Who is up and who is down. The only screen where colour carries
 * meaning: primary is money owed to you, error is money you owe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancesScreen(
    state: BalancesUiState,
    onBack: () -> Unit,
    onSettleUp: () -> Unit,
    onTotals: () -> Unit,
    onGroupSettings: () -> Unit,
    onRetry: () -> Unit,
    onPickYouOpen: () -> Unit,
    onPickYouDismiss: () -> Unit,
    onYouChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    if (state.pickingYou) {
        ParticipantPickerDialog(
            options = state.rows.map { it.name },
            selectedIndex = state.youIndex,
            onSelect = onYouChange,
            onDismiss = onPickYouDismiss,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.balances_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    // Its own button rather than a menu item: the totals are
                    // the other thing these numbers are read for.
                    IconButton(onClick = onTotals) {
                        Icon(
                            painter = painterResource(R.drawable.ic_query_stats),
                            contentDescription = stringResource(R.string.totals_title),
                        )
                    }
                    GroupOverflowMenu(onGroupSettings = onGroupSettings)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.isEmpty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.balances_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> Content(
                state = state,
                padding = padding,
                onSettleUp = onSettleUp,
                onRetry = onRetry,
                onPickYouOpen = onPickYouOpen,
            )
        }
    }
}

@Composable
private fun Content(
    state: BalancesUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onSettleUp: () -> Unit,
    onRetry: () -> Unit,
    onPickYouOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        if (state.error != null || state.isOffline) {
            OfflineBanner(error = state.error, onRetry = onRetry)
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.needsIdentity) {
                // In place of the position card, which has nothing to say
                // without an answer.
                IdentityPrompt(onChoose = onPickYouOpen)
            } else {
                YourPosition(state)
            }

            Column {
                state.rows.forEachIndexed { index, row ->
                    BalanceLine(row = row, state = state, index = index)
                }
            }

            if (state.canSettle) {
                Button(
                    onClick = onSettleUp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_handshake),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.balances_settle_up),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** The headline: what the group owes you, or you it, coloured before it is read. */
@Composable
private fun YourPosition(state: BalancesUiState) {
    val position = state.yourPosition
    val container = when {
        position == null || position == 0L -> MaterialTheme.colorScheme.surfaceContainerLow
        position > 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when {
        position == null || position == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        position > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.balances_your_position),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = when {
                    // No participant chosen, so there is no "your" to report.
                    position == null -> stringResource(R.string.group_your_share_unknown)

                    else -> state.currency.formatSigned(position)
                },
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = when {
                    position == null -> stringResource(R.string.balances_unknown)

                    position == 0L -> stringResource(R.string.balances_settled)

                    position > 0 -> pluralStringResource(
                        R.plurals.balances_owe_you,
                        state.counterparties,
                        state.counterparties,
                    )

                    else -> pluralStringResource(
                        R.plurals.balances_you_owe,
                        state.counterparties,
                        state.counterparties,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun BalanceLine(row: BalanceRow, state: BalancesUiState, index: Int) {
    val colour = when {
        row.total > 0 -> MaterialTheme.colorScheme.primary
        row.total < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialAvatar(name = row.name, index = index)
        Column(Modifier.weight(1f)) {
            Text(
                text = if (row.isYou) {
                    stringResource(R.string.split_you, row.name)
                } else {
                    row.name
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(3.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(row.weight)
                        .fillMaxHeight()
                        .background(colour, RoundedCornerShape(3.dp)),
                )
            }
        }
        Text(
            text = if (row.total == 0L) {
                state.currency.format(0)
            } else {
                state.currency.formatSigned(row.total)
            },
            style = MaterialTheme.typography.titleMedium,
            color = colour,
            textAlign = TextAlign.End,
            modifier = Modifier.width(92.dp),
        )
    }
}
