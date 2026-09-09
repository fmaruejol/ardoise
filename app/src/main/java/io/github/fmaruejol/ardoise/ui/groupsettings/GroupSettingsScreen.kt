package io.github.fmaruejol.ardoise.ui.groupsettings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.components.ArdoiseTextField
import io.github.fmaruejol.ardoise.ui.components.DashedAvatarSlot
import io.github.fmaruejol.ardoise.ui.components.FormScroller
import io.github.fmaruejol.ardoise.ui.components.InitialAvatar
import io.github.fmaruejol.ardoise.ui.components.InlineNameField
import io.github.fmaruejol.ardoise.ui.components.ParticipantPickerDialog
import io.github.fmaruejol.ardoise.ui.components.SectionLabel
import io.github.fmaruejol.ardoise.ui.components.rememberFormScroller
import io.github.fmaruejol.ardoise.ui.components.scrollTarget
import io.github.fmaruejol.ardoise.ui.describe
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GroupSettingsRoute(
    groupId: String,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GroupSettingsViewModel = koinViewModel { parametersOf(groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTemplate = stringResource(R.string.group_settings_share_text, state.name, state.inviteUrl)

    val currentOnRemoved by rememberUpdatedState(onRemoved)
    LaunchedEffect(state.isRemoved) {
        if (state.isRemoved) {
            viewModel.onNavigationHandled()
            currentOnRemoved()
        }
    }

    GroupSettingsScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::onSave,
        onNameChange = viewModel::onNameChange,
        onInformationChange = viewModel::onInformationChange,
        onParticipantNameChange = viewModel::onParticipantNameChange,
        onAddParticipant = viewModel::onAddParticipant,
        onRemoveParticipant = viewModel::onRemoveParticipant,
        onBlockedRemovalDismiss = viewModel::onBlockedRemovalDismiss,
        onPickYouOpen = viewModel::onPickYouOpen,
        onPickYouDismiss = viewModel::onPickYouDismiss,
        onYouChange = viewModel::onYouChange,
        onRemoveGroupClick = viewModel::onRemoveGroupClick,
        onRemoveGroupConfirm = viewModel::onRemoveGroupConfirm,
        onRemoveGroupDismiss = viewModel::onRemoveGroupDismiss,
        onRetry = viewModel::onRetry,
        onShare = {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareTemplate)
                    },
                    null,
                ),
            )
        },
        modifier = modifier,
    )
}

/**
 * Group settings. The invite is at the top because, with no accounts, the
 * link is the only way anyone else gets in; "which one is you" sits under its
 * own "On this device" heading, because that answer never leaves the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    state: GroupSettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onInformationChange: (String) -> Unit,
    onParticipantNameChange: (Int, String) -> Unit,
    onAddParticipant: () -> Unit,
    onRemoveParticipant: (Int) -> Unit,
    onBlockedRemovalDismiss: () -> Unit,
    onPickYouOpen: () -> Unit,
    onPickYouDismiss: () -> Unit,
    onYouChange: (Int) -> Unit,
    onRemoveGroupClick: () -> Unit,
    onRemoveGroupConfirm: () -> Unit,
    onRemoveGroupDismiss: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.pickingYou) {
        ParticipantPickerDialog(
            options = state.participants.filter { it.id != null }.map { it.name },
            selectedIndex = state.youIndex,
            onSelect = onYouChange,
            onDismiss = onPickYouDismiss,
        )
    }

    state.blockedRemoval?.let { name ->
        AlertDialog(
            onDismissRequest = onBlockedRemovalDismiss,
            text = { Text(stringResource(R.string.group_settings_participant_has_expenses, name)) },
            confirmButton = {
                TextButton(onClick = onBlockedRemovalDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.confirmingRemoval) {
        AlertDialog(
            onDismissRequest = onRemoveGroupDismiss,
            title = { Text(stringResource(R.string.group_list_remove_title, state.name)) },
            text = { Text(stringResource(R.string.group_list_remove_body)) },
            confirmButton = {
                TextButton(onClick = onRemoveGroupConfirm) {
                    Text(stringResource(R.string.group_list_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = onRemoveGroupDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val scroller = rememberFormScroller(rememberScrollState())

    // See the expense form: the first field that is wrong is brought into view.
    LaunchedEffect(state.refusedSaves) {
        if (state.refusedSaves > 0) {
            state.firstError?.let { error ->
                scroller.scrollTo(error.participantIndex ?: NameKey)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (!state.isLoading && !state.notFound && state.loadError == null) {
                        TextButton(onClick = onSave, enabled = state.canSave) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(R.string.group_settings_save))
                            }
                        }
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

            // No banner here, unlike every other reading screen: this one reads
            // `groups.getDetails`, which is not cached, so a failure leaves
            // nothing behind to put a banner over. It needs a way back instead.
            state.notFound || state.loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.notFound) {
                            stringResource(R.string.group_settings_not_found)
                        } else {
                            state.loadError!!.describe()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // A group that is gone stays gone; only a failed read is
                    // worth asking about again.
                    if (!state.notFound) {
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.offline_retry))
                        }
                    }
                }
            }

            else -> Content(
                state = state,
                padding = padding,
                scroller = scroller,
                onNameChange = onNameChange,
                onInformationChange = onInformationChange,
                onParticipantNameChange = onParticipantNameChange,
                onAddParticipant = onAddParticipant,
                onRemoveParticipant = onRemoveParticipant,
                onPickYouOpen = onPickYouOpen,
                onRemoveGroupClick = onRemoveGroupClick,
                onShare = onShare,
            )
        }
    }
}

@Composable
private fun Content(
    state: GroupSettingsUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    scroller: FormScroller,
    onNameChange: (String) -> Unit,
    onInformationChange: (String) -> Unit,
    onParticipantNameChange: (Int, String) -> Unit,
    onAddParticipant: () -> Unit,
    onRemoveParticipant: (Int) -> Unit,
    onPickYouOpen: () -> Unit,
    onRemoveGroupClick: () -> Unit,
    onShare: () -> Unit,
) {
    // The cursor goes into a row the moment it is added, as on the
    // create-group form. Here rather than beside the screen's other effect:
    // this is where the rows are.
    val newParticipant = remember { FocusRequester() }
    LaunchedEffect(state.participantsAdded) {
        if (state.participantsAdded > 0) newParticipant.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(scroller.state)
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InviteCard(state = state, onShare = onShare)

        ArdoiseTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.group_settings_name_label),
            enabled = !state.isSaving,
            isError = GroupSettingsError.Name in state.saveErrors,
            supportingText = state.errorOf(GroupSettingsError.Name)?.describe(),
            modifier = Modifier.scrollTarget(scroller, NameKey),
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
        )

        ArdoiseTextField(
            value = state.information,
            onValueChange = onInformationChange,
            label = stringResource(R.string.group_settings_information_label),
            enabled = !state.isSaving,
            singleLine = false,
            minHeight = 72.dp,
        )

        Column(modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = stringResource(
                    R.string.group_settings_participants,
                    state.participants.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )

            state.participants.forEachIndexed { index, participant ->
                ParticipantRow(
                    participant = participant,
                    index = index,
                    isMe = participant.id != null && participant.id == state.activeParticipantId,
                    enabled = !state.isSaving,
                    onNameChange = { onParticipantNameChange(index, it) },
                    onRemove = { onRemoveParticipant(index) },
                    error = state.saveErrors.firstOrNull { it.participantIndex == index },
                    focusRequester = newParticipant
                        .takeIf { index == state.participants.lastIndex },
                    modifier = Modifier.scrollTarget(scroller, index),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isSaving, onClick = onAddParticipant)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DashedAvatarSlot()
                Text(
                    text = stringResource(R.string.group_settings_add_participant),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Only what belongs to no field.
        state.saveErrors
            .filterIsInstance<GroupSettingsError.Failed>()
            .firstOrNull()
            ?.let { error ->
                Text(
                    text = error.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        SectionLabel(
            text = stringResource(R.string.group_settings_on_this_device),
            modifier = Modifier.padding(start = 4.dp),
        )

        WhichOneIsYouRow(state = state, onClick = onPickYouOpen)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRemoveGroupClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_logout),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.group_settings_remove),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The invite: with no accounts the link is the only way anyone else gets in. */
@Composable
private fun InviteCard(state: GroupSettingsUiState, onShare: () -> Unit) {
    val foreground = MaterialTheme.colorScheme.onSecondaryContainer.toArgb()
    val background = MaterialTheme.colorScheme.secondaryContainer.toArgb()
    val code = remember(state.inviteUrl, foreground, background) {
        state.inviteUrl.takeIf { it.isNotBlank() }?.let {
            qrCodeBitmap(it, QR_SIZE_PX, foreground, background)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShare),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_code_2),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.group_settings_invite, state.name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.group_settings_invite_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_ios_share),
                    contentDescription = stringResource(R.string.group_settings_share),
                    modifier = Modifier.size(24.dp),
                )
            }

            if (code != null) {
                Image(
                    bitmap = code,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(180.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
    }
}

/**
 * "Which one is you", under "On this device" because that is what it is: the
 * answer is never sent anywhere.
 */
@Composable
private fun WhichOneIsYouRow(state: GroupSettingsUiState, onClick: () -> Unit) {
    val name = state.activeParticipantName
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_account_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.which_is_you_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (name == null) {
                    stringResource(R.string.which_is_you_unset)
                } else {
                    stringResource(R.string.which_is_you_value, name)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (name == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_drop_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantEdit,
    index: Int,
    isMe: Boolean,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    error: GroupSettingsError? = null,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InitialAvatar(name = participant.name.ifBlank { "?" }, index = index)
        Column(Modifier.weight(1f)) {
            InlineNameField(
                value = participant.name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.create_group_participant_label),
                enabled = enabled,
                isError = error != null,
                focusRequester = focusRequester,
            )
            // Under the name it is about, in the same column as "This is me".
            error?.let {
                Text(
                    text = it.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (isMe) {
                Text(
                    text = stringResource(R.string.group_settings_this_is_me),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isMe) {
            // The same footprint as the remove button below, so the two line up.
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(
                        R.string.group_settings_remove_participant,
                        participant.name,
                    ),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupSettingsError.describe(): String = when (this) {
    GroupSettingsError.Name -> {
        stringResource(R.string.create_group_error_name)
    }

    is GroupSettingsError.ParticipantName -> {
        stringResource(R.string.create_group_error_participant)
    }

    is GroupSettingsError.DuplicateParticipant -> {
        stringResource(R.string.create_group_error_duplicate)
    }

    is GroupSettingsError.Failed -> {
        error.describe()
    }
}

/** Rendered once at 180dp and scaled to the card. */
private const val QR_SIZE_PX = 512

/** The group name's scroll target; a participant row is keyed by its index. */
private val NameKey = Any()
