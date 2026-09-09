package io.github.fmaruejol.ardoise.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.ui.components.InfoCard
import io.github.fmaruejol.ardoise.ui.components.InitialAvatar

/**
 * Editing how an expense is split.
 *
 * Amount, Shares, Percent and **Evenly**. The fourth is there because the
 * form's Split row is the only door here, and without it there would be no way
 * back to an even split.
 *
 * Every number is stored in the representation the *server* expects for the
 * mode (see `PaidFor` in `:core`): a wrong scale is stored as given and
 * quietly skews everyone's balance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditorScreen(
    state: ExpenseFormUiState,
    onBack: () -> Unit,
    onSplitModeChange: (SplitMode) -> Unit,
    onParticipantToggle: (String) -> Unit,
    onShareChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.split_title, state.currency.format(state.amount)))
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
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeSelector(selected = state.splitMode, onSelect = onSplitModeChange)

            state.participants.forEach { participant ->
                ParticipantSplitRow(
                    participant = participant,
                    state = state,
                    onToggle = { onParticipantToggle(participant.id) },
                    onShareChange = { onShareChange(participant.id, it) },
                )
            }

            state.requiredTotal?.let { required ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.split_assigned),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (state.splitMode == SplitMode.BY_PERCENTAGE) {
                            stringResource(R.string.split_assigned_percent, percent(state.assigned))
                        } else {
                            stringResource(
                                R.string.split_assigned_value,
                                state.currency.format(state.assigned),
                                state.currency.format(required),
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.assigned == required) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            val excluded = state.excluded
            if (excluded.isNotEmpty()) {
                InfoCard(
                    icon = R.drawable.ic_info,
                    text = if (excluded.size == 1) {
                        stringResource(R.string.split_excluded_one, excluded.single().name)
                    } else {
                        stringResource(
                            R.string.split_excluded_many,
                            excluded.joinToString { it.name },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: SplitMode, onSelect: (SplitMode) -> Unit) {
    val modes = listOf(
        SplitMode.EVENLY to R.string.split_evenly,
        SplitMode.BY_AMOUNT to R.string.split_amount,
        SplitMode.BY_SHARES to R.string.split_shares,
        SplitMode.BY_PERCENTAGE to R.string.split_percent,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(1.dp),
    ) {
        modes.forEachIndexed { index, (mode, label) ->
            val isSelected = mode == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shapeFor(index, modes.size),
                    )
                    .clickable { onSelect(mode) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 2.dp),
                    )
                }
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

private fun shapeFor(index: Int, count: Int) = when (index) {
    0 -> RoundedCornerShape(topStart = 19.dp, bottomStart = 19.dp)
    count - 1 -> RoundedCornerShape(topEnd = 19.dp, bottomEnd = 19.dp)
    else -> RoundedCornerShape(0.dp)
}

@Composable
private fun ParticipantSplitRow(
    participant: Participant,
    state: ExpenseFormUiState,
    onToggle: () -> Unit,
    onShareChange: (String) -> Unit,
) {
    val index = state.participants.indexOfFirst { it.id == participant.id }
    val isMe = participant.id == state.activeParticipantId
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InitialAvatar(name = participant.name, index = index)
        Text(
            text = if (isMe) {
                stringResource(R.string.split_you, participant.name)
            } else {
                participant.name
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        if (state.splitMode == SplitMode.EVENLY) {
            Checkbox(
                checked = participant.id in state.paidFor,
                onCheckedChange = { onToggle() },
            )
        } else {
            ShareField(
                value = state.splitText[participant.id].orEmpty(),
                onValueChange = onShareChange,
            )
        }
    }
}

@Composable
private fun ShareField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .width(104.dp)
            .height(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        decorationBox = { field ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd,
            ) { field() }
        },
    )
}
