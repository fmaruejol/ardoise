package io.github.fmaruejol.ardoise.ui.creategroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.ui.components.ArdoisePickerField
import io.github.fmaruejol.ardoise.ui.components.ArdoiseTextField
import io.github.fmaruejol.ardoise.ui.components.DashedAvatarSlot
import io.github.fmaruejol.ardoise.ui.components.InitialAvatar
import io.github.fmaruejol.ardoise.ui.components.InlineNameField
import io.github.fmaruejol.ardoise.ui.components.ParticipantPickerDialog
import io.github.fmaruejol.ardoise.ui.components.SectionLabel
import io.github.fmaruejol.ardoise.ui.components.currencyLabel
import io.github.fmaruejol.ardoise.ui.components.rememberFormScroller
import io.github.fmaruejol.ardoise.ui.components.scrollTarget
import io.github.fmaruejol.ardoise.ui.describe
import org.koin.androidx.compose.koinViewModel

@Composable
fun CreateGroupRoute(
    onCreated: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateGroupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val currentOnCreated by rememberUpdatedState(onCreated)
    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let { groupId ->
            viewModel.onNavigationHandled()
            currentOnCreated(groupId)
        }
    }

    CreateGroupScreen(
        state = state,
        onClose = onClose,
        onNameChange = viewModel::onNameChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onInformationChange = viewModel::onInformationChange,
        onPickYouOpen = viewModel::onPickYouOpen,
        onPickYouDismiss = viewModel::onPickYouDismiss,
        onYouChange = viewModel::onYouChange,
        onParticipantChange = viewModel::onParticipantChange,
        onAddParticipant = viewModel::onAddParticipant,
        onRemoveParticipant = viewModel::onRemoveParticipant,
        onSave = viewModel::onSave,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onClose: () -> Unit,
    onNameChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onInformationChange: (String) -> Unit,
    onPickYouOpen: () -> Unit,
    onPickYouDismiss: () -> Unit,
    onYouChange: (Int) -> Unit,
    onParticipantChange: (Int, String) -> Unit,
    onAddParticipant: () -> Unit,
    onRemoveParticipant: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroller = rememberFormScroller(rememberScrollState())

    // A refused save marks everything wrong; this brings the first into view.
    // Keyed on the count, so saving twice unchanged scrolls twice.
    LaunchedEffect(state.refusedSaves) {
        if (state.refusedSaves > 0) {
            state.firstError?.let { error ->
                scroller.scrollTo(error.participantIndex ?: keyOf(error))
            }
        }
    }

    // A row added is a name about to be typed, so the cursor goes in it.
    // Focusing scrolls it into view, which is why there is no `scrollTo`.
    val newParticipant = remember { FocusRequester() }
    LaunchedEffect(state.participantsAdded) {
        if (state.participantsAdded > 0) newParticipant.requestFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_group_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.create_group_save))
                        }
                    }
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
                .verticalScroll(scroller.state)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ArdoiseTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.create_group_name_label),
                modifier = Modifier.scrollTarget(scroller, NameKey),
                enabled = !state.isSaving,
                isError = CreateGroupError.Name in state.errors,
                supportingText = state.errorOf(CreateGroupError.Name)?.describe(),
            )

            CurrencyPicker(
                code = state.currencyCode,
                enabled = !state.isSaving,
                onCurrencyChange = onCurrencyChange,
            )

            ArdoiseTextField(
                value = state.information,
                onValueChange = onInformationChange,
                label = stringResource(R.string.create_group_information_label),
                placeholder = stringResource(R.string.create_group_information_placeholder),
                enabled = !state.isSaving,
                singleLine = false,
                minHeight = 96.dp,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(
                    text = stringResource(R.string.create_group_participants),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )

                state.participants.forEachIndexed { index, name ->
                    ParticipantRow(
                        name = name,
                        index = index,
                        enabled = !state.isSaving,
                        removable = index > 0,
                        onNameChange = { onParticipantChange(index, it) },
                        onRemove = { onRemoveParticipant(index) },
                        error = state.errors.firstOrNull { it.participantIndex == index },
                        // The new row is always the last one.
                        focusRequester = newParticipant
                            .takeIf { index == state.participants.lastIndex },
                        modifier = Modifier.scrollTarget(scroller, index),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isSaving, onClick = onAddParticipant)
                        .heightIn(min = 56.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DashedAvatarSlot()
                    Text(
                        text = stringResource(R.string.create_group_add_participant),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            WhichOneIsYou(
                state = state,
                onOpen = onPickYouOpen,
                onDismiss = onPickYouDismiss,
                onSelect = onYouChange,
                modifier = Modifier.scrollTarget(scroller, YouKey),
            )

            // Only what belongs to no field.
            state.errors.filterIsInstance<CreateGroupError.Failed>().firstOrNull()?.let { error ->
                Text(
                    text = error.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CurrencyPicker(
    code: String,
    enabled: Boolean,
    onCurrencyChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ArdoisePickerField(
            value = currencyLabel(code),
            label = stringResource(R.string.create_group_currency_label),
            onClick = { expanded = true },
            enabled = enabled,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GroupCurrency.SUPPORTED_CODES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(currencyLabel(option)) },
                    onClick = {
                        onCurrencyChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Which participant the person creating the group is. A dropdown rather than a
 * mark on a row: the answer is about this device, not about the group.
 */
@Composable
private fun WhichOneIsYou(
    state: CreateGroupUiState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val named = state.participants.map { it.trim() }

    if (state.pickingYou) {
        ParticipantPickerDialog(
            options = named.filter { it.isNotEmpty() },
            selectedIndex = named.filter { it.isNotEmpty() }.indexOf(state.youName.trim()),
            onSelect = { visible ->
                // The dialog lists only rows with a name, so its index has to
                // be mapped back onto the full list.
                onSelect(named.indexOf(named.filter { it.isNotEmpty() }[visible]))
            },
            onDismiss = onDismiss,
        )
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ArdoisePickerField(
            value = state.youName,
            label = stringResource(R.string.which_is_you_label),
            onClick = onOpen,
            enabled = !state.isSaving,
            isError = CreateGroupError.You in state.errors,
            supportingText = state.errorOf(CreateGroupError.You)?.describe(),
        )
        Text(
            text = stringResource(R.string.which_is_you_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * One participant, drawn as a plain row: the field carries no box of its own.
 */
@Composable
private fun ParticipantRow(
    name: String,
    index: Int,
    enabled: Boolean,
    removable: Boolean,
    onNameChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    error: CreateGroupError? = null,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InitialAvatar(name = name.ifBlank { "?" }, index = index)
            InlineNameField(
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.create_group_participant_label),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                isError = error != null,
                focusRequester = focusRequester,
            )
            if (removable) {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(
                            R.string.create_group_remove_participant,
                            name,
                        ),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        // Under the name it is about, indented past the avatar.
        error?.let {
            Text(
                text = it.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 56.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun CreateGroupError.describe(): String = when (this) {
    CreateGroupError.Name -> {
        stringResource(R.string.create_group_error_name)
    }

    CreateGroupError.You -> {
        stringResource(R.string.create_group_error_you)
    }

    is CreateGroupError.ParticipantName -> {
        stringResource(R.string.create_group_error_participant)
    }

    is CreateGroupError.DuplicateParticipant -> {
        stringResource(R.string.create_group_error_duplicate)
    }

    is CreateGroupError.Failed -> {
        error.describe()
    }
}

/** The form's two named scroll targets; a participant row is keyed by index. */
private val NameKey = Any()
private val YouKey = Any()

private fun keyOf(error: CreateGroupError): Any =
    if (error == CreateGroupError.You) YouKey else NameKey
