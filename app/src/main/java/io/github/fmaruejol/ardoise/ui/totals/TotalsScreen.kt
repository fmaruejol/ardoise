package io.github.fmaruejol.ardoise.ui.totals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
fun TotalsRoute(
    groupId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TotalsViewModel = koinViewModel { parametersOf(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    TotalsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

/**
 * What the group has spent, and who put it in. Every figure excludes
 * reimbursements: they move money without the group spending anything, and
 * counting them would hand whoever settles up most the "who paid most" spot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalsScreen(
    state: TotalsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.totals_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
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

            state.isEmpty -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.totals_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> Content(state = state, padding = padding, onRetry = onRetry)
        }
    }
}

@Composable
private fun Content(state: TotalsUiState, padding: PaddingValues, onRetry: () -> Unit) {
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            state.stats?.let { Headline(state = state, stats = it) }

            if (state.categories.isNotEmpty()) {
                Section(stringResource(R.string.totals_by_category)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        state.categories.forEachIndexed { index, bar ->
                            CategoryBarRow(bar = bar, state = state, index = index)
                        }
                    }
                }
            }

            if (state.payers.isNotEmpty()) {
                Section(stringResource(R.string.totals_who_paid)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.payers.forEach { payer -> PayerLine(payer = payer, state = state) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Headline(state: TotalsUiState, stats: io.github.fmaruejol.ardoise.core.stats.GroupStats) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = pluralStringResource(R.plurals.totals_spent_in, stats.days, stats.days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.currency.format(stats.total),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = stringResource(
                    R.string.totals_per_person,
                    state.currency.format(stats.perPerson),
                    state.currency.format(stats.perDayEach),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
        )
        content()
    }
}

@Composable
private fun CategoryBarRow(bar: CategoryBar, state: TotalsUiState, index: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = bar.label ?: stringResource(R.string.totals_uncategorised),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = state.currency.format(bar.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(12.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bar.weight)
                    .fillMaxHeight()
                    .background(barColour(index), RoundedCornerShape(6.dp)),
            )
        }
    }
}

/**
 * Colours the bars down the list. They carry no meaning. The order already
 * says which is biggest, so it is a fixed rotation rather than a colour per
 * category.
 */
@Composable
private fun barColour(index: Int): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.outline,
    )
    return palette[index % palette.size]
}

@Composable
private fun PayerLine(payer: PayerRow, state: TotalsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InitialAvatar(
            name = payer.name,
            index = payer.index,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = if (payer.isYou) {
                stringResource(R.string.split_you, payer.name)
            } else {
                payer.name
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = state.currency.format(payer.paid),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
