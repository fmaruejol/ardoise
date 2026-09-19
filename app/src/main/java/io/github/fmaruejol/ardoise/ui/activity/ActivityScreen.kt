package io.github.fmaruejol.ardoise.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.model.ActivityType
import io.github.fmaruejol.ardoise.ui.components.FullDate
import io.github.fmaruejol.ardoise.ui.components.OfflineBanner
import io.github.fmaruejol.ardoise.ui.components.PullableCenter
import io.github.fmaruejol.ardoise.ui.components.TimeOfDay
import io.github.fmaruejol.ardoise.ui.components.rememberDateFormatter
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ActivityRoute(
    groupId: String,
    onBack: () -> Unit,
    onExpenseClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    val viewModel: ActivityViewModel =
        koinViewModel(key = "activity-$groupId") { parametersOf(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ActivityScreen(
        state = state,
        onBack = onBack,
        onExpenseClick = onExpenseClick,
        onLoadMore = viewModel::onLoadMore,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
        bottomBar = bottomBar,
    )
}

/**
 * What has happened in the group. The second line is the time and nothing
 * else: the log carries no amount, so there is no number here to be wrong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onBack: () -> Unit,
    onExpenseClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title)) },
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
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.error != null || state.isOffline) {
                OfflineBanner(error = state.error, onRetry = onRefresh)
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    state.isEmpty -> PullableCenter {
                        Text(
                            text = stringResource(R.string.activity_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }

                    else -> Log(
                        state = state,
                        onExpenseClick = onExpenseClick,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun Log(
    state: ActivityUiState,
    onExpenseClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
    ) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(day.date) }
            items(day.rows, key = { it.id }) { row ->
                ActivityLine(row = row, onExpenseClick = onExpenseClick)
            }
        }

        if (state.hasMore) {
            item("more") {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.activity_load_more))
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val formatter = rememberDateFormatter(FullDate)
    Text(
        text = when (date) {
            today -> stringResource(R.string.activity_today)
            today.minusDays(1) -> stringResource(R.string.activity_yesterday)
            else -> formatter.format(date)
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 10.dp),
    )
}

@Composable
private fun ActivityLine(row: ActivityRow, onExpenseClick: (String) -> Unit) {
    val time = rememberDateFormatter(TimeOfDay)
        .format(row.time.atZone(ZoneId.systemDefault()))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Only expense activities lead anywhere, and a deleted one would
            // lead to a screen reporting the deletion.
            .let { modifier ->
                val expenseId = row.expenseId
                if (expenseId != null && row.type != ActivityType.DELETE_EXPENSE) {
                    modifier.clickable { onExpenseClick(expenseId) }
                } else {
                    modifier
                }
            }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(row.type.icon()),
            contentDescription = null,
            tint = row.type.tint(),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = row.sentence(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ActivityType.icon(): Int = when (this) {
    ActivityType.CREATE_EXPENSE -> R.drawable.ic_add_circle
    ActivityType.UPDATE_EXPENSE -> R.drawable.ic_edit
    ActivityType.DELETE_EXPENSE -> R.drawable.ic_delete
    ActivityType.UPDATE_GROUP -> R.drawable.ic_groups
    ActivityType.UNKNOWN -> R.drawable.ic_history
}

@Composable
private fun ActivityType.tint() = when (this) {
    // Only the two ends of an expense's life are coloured; an edit stays quiet.
    ActivityType.CREATE_EXPENSE -> MaterialTheme.colorScheme.primary

    ActivityType.DELETE_EXPENSE -> MaterialTheme.colorScheme.error

    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The line itself, with the title picked out. The sentence comes from
 * `strings.xml` whole and the title is found inside the result, so a
 * translation can put it wherever its grammar wants.
 */
@Composable
private fun ActivityRow.sentence(): AnnotatedString {
    val subject = who.ifBlank { stringResource(R.string.activity_someone) }
    val title = what ?: stringResource(R.string.activity_untitled)

    val text = when (type) {
        ActivityType.CREATE_EXPENSE -> if (byYou) {
            stringResource(R.string.activity_created_by_you, title)
        } else {
            stringResource(R.string.activity_created, subject, title)
        }

        ActivityType.UPDATE_EXPENSE -> if (byYou) {
            stringResource(R.string.activity_updated_by_you, title)
        } else {
            stringResource(R.string.activity_updated, subject, title)
        }

        ActivityType.DELETE_EXPENSE -> if (byYou) {
            stringResource(R.string.activity_deleted_by_you, title)
        } else {
            stringResource(R.string.activity_deleted, subject, title)
        }

        ActivityType.UPDATE_GROUP -> if (byYou) {
            stringResource(R.string.activity_group_by_you)
        } else {
            stringResource(R.string.activity_group, subject)
        }

        ActivityType.UNKNOWN -> stringResource(R.string.activity_unknown, subject)
    }

    // A deleted expense's title is greyed rather than highlighted: there is
    // nothing left to go and look at.
    val highlight = SpanStyle(
        color = if (type == ActivityType.DELETE_EXPENSE) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
    )

    return buildAnnotatedString {
        append(text)
        if (what != null) {
            val start = text.lastIndexOf(title)
            if (start >= 0) addStyle(highlight, start, start + title.length)
        }
    }
}
