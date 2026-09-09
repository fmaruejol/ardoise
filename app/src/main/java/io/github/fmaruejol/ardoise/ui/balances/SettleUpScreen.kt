package io.github.fmaruejol.ardoise.ui.balances

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.ui.components.InitialAvatar
import io.github.fmaruejol.ardoise.ui.components.OfflineBanner
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SettleUpRoute(
    groupId: String,
    onClose: () -> Unit,
    onMarkPaid: (Transfer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettleUpViewModel = koinViewModel { parametersOf(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettleUpScreen(
        state = state,
        onClose = onClose,
        onMarkPaid = onMarkPaid,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

/**
 * The payments that would square the group. One card, one payment, one
 * button, deliberately not a "settle everything" sweep: each transfer is a
 * real expense, and the user is saying it actually happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    state: SettleUpUiState,
    onClose: () -> Unit,
    onMarkPaid: (Transfer) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settle_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                        )
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

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.error != null || state.isOffline) {
                    OfflineBanner(error = state.error, onRetry = onRetry)
                }
                Content(
                    state = state,
                    padding = PaddingValues(0.dp),
                    onMarkPaid = onMarkPaid,
                )
            }
        }
    }
}

@Composable
private fun Content(
    state: SettleUpUiState,
    padding: PaddingValues,
    onMarkPaid: (Transfer) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (state.transfers.isEmpty()) {
                stringResource(R.string.settle_none)
            } else {
                pluralStringResource(
                    R.plurals.settle_intro,
                    state.transfers.size,
                    state.transfers.size,
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        state.transfers.forEach { transfer ->
            TransferCard(
                transfer = transfer,
                state = state,
                onMarkPaid = onMarkPaid,
            )
        }
    }
}

@Composable
private fun TransferCard(
    transfer: Transfer,
    state: SettleUpUiState,
    onMarkPaid: (Transfer) -> Unit,
) {
    val title = when {
        transfer.toIsYou -> stringResource(R.string.settle_pays_you, transfer.fromName)
        transfer.fromIsYou -> stringResource(R.string.settle_you_pay, transfer.toName)
        else -> stringResource(R.string.settle_pays, transfer.fromName, transfer.toName)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InitialAvatar(
                    name = transfer.fromName,
                    index = transfer.fromIndex,
                    modifier = Modifier.size(36.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                InitialAvatar(
                    name = transfer.toName,
                    index = transfer.toIndex,
                    modifier = Modifier.size(36.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = transfer.effect(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = state.currency.format(transfer.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedButton(
                // Opens the form rather than saving: what happened is the
                // user's to confirm, and the mutation cannot be taken back.
                onClick = { onMarkPaid(transfer) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                Text(stringResource(R.string.settle_mark_paid))
            }
        }
    }
}

/** The line under the names: what this payment actually finishes. */
@Composable
private fun Transfer.effect(state: SettleUpUiState): String = when (clears) {
    Clears.Both -> stringResource(R.string.settle_clears_both)

    Clears.From -> if (fromIsYou) {
        stringResource(R.string.settle_clears_you)
    } else {
        stringResource(R.string.settle_clears_one, fromName)
    }

    Clears.To -> if (toIsYou) {
        stringResource(R.string.settle_clears_you)
    } else {
        stringResource(R.string.settle_clears_one, toName)
    }

    Clears.Neither -> stringResource(
        R.string.settle_partial,
        state.currency.format(leftOver),
    )
}
