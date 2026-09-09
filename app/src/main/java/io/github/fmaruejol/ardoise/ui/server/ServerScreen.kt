package io.github.fmaruejol.ardoise.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.components.ArdoiseTextField
import io.github.fmaruejol.ardoise.ui.components.InfoCard
import io.github.fmaruejol.ardoise.ui.describe
import io.github.fmaruejol.ardoise.ui.host
import io.github.fmaruejol.ardoise.ui.openAppSettings
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServerRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back from Android settings is the only way network access can be
    // granted, and it lands here as a resume.
    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResumed()
        onPauseOrDispose {}
    }

    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.onNavigationHandled()
            currentOnDone()
        }
    }

    ServerScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::onSave,
        onTest = viewModel::onTest,
        onChoiceChange = viewModel::onChoiceChange,
        onUrlChange = viewModel::onUrlChange,
        onPermissionPromptDismiss = viewModel::onPermissionPromptDismissed,
        onPermissionPromptRequest = viewModel::onPermissionPromptRequested,
        onOpenAppSettings = { context.openAppSettings() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    state: ServerUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onChoiceChange: (InstanceChoice) -> Unit,
    onUrlChange: (String) -> Unit,
    onPermissionPromptDismiss: () -> Unit,
    onPermissionPromptRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showPermissionPrompt) {
        NetworkPermissionDialog(
            onDismiss = onPermissionPromptDismiss,
            onOpenSettings = onOpenAppSettings,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) {
                        Text(stringResource(R.string.server_save))
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.server_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InstanceOption(
                    title = stringResource(R.string.server_cloud_title),
                    subtitle = stringResource(R.string.server_cloud_subtitle),
                    selected = state.choice == InstanceChoice.Cloud,
                    onSelect = { onChoiceChange(InstanceChoice.Cloud) },
                    // Only the selected option is raised off the background.
                    raised = state.choice == InstanceChoice.Cloud,
                )

                SelfHostedOption(
                    state = state,
                    onSelect = { onChoiceChange(InstanceChoice.SelfHosted) },
                    onUrlChange = onUrlChange,
                    onTest = onTest,
                )
            }

            if (state.showSwitchNote) {
                InfoCard(
                    icon = R.drawable.ic_swap_horiz,
                    text = stringResource(
                        R.string.server_switch_note,
                        pluralStringResource(
                            R.plurals.server_switch_note_groups,
                            state.savedGroupCount,
                            state.savedGroupCount,
                        ),
                        state.savedBaseUrl.host(),
                    ),
                )
            }

            if (state.networkAccessBlocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.network_permission_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onPermissionPromptRequest) {
                        Text(stringResource(R.string.network_permission_allow))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    raised: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (raised) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = if (content == null) 8.dp else 20.dp,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = null)
            }
            content?.invoke()
        }
    }
}

@Composable
private fun SelfHostedOption(
    state: ServerUiState,
    onSelect: () -> Unit,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    val selected = state.choice == InstanceChoice.SelfHosted
    InstanceOption(
        title = stringResource(R.string.server_self_hosted_title),
        subtitle = stringResource(R.string.server_self_hosted_subtitle),
        selected = selected,
        onSelect = onSelect,
        raised = selected,
        content = if (!selected) {
            null
        } else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ArdoiseTextField(
                        value = state.customUrl,
                        onValueChange = onUrlChange,
                        label = stringResource(R.string.server_url_label),
                        modifier = Modifier.padding(top = 4.dp),
                        placeholder = stringResource(R.string.server_url_placeholder),
                        isError = state.urlError != null,
                        supportingText = state.urlError?.describe(),
                        // The field sits inside the selected option's card.
                        labelBackground = MaterialTheme.colorScheme.surfaceContainerLow,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onTest() }),
                    )

                    CheckStatus(state = state, onTest = onTest)
                }
            }
        },
    )
}

/** What the last test said, and the way to run another. It reports; it does not gate. */
@Composable
private fun CheckStatus(state: ServerUiState, onTest: () -> Unit) {
    val outcome = state.checkOutcome
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.isChecking -> {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.server_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }

            outcome is CheckOutcome.Reachable -> {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.server_reachable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }

            outcome is CheckOutcome.Failed -> {
                Icon(
                    painter = painterResource(R.drawable.ic_error),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = outcome.error.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }

        TextButton(onClick = onTest, enabled = state.canTest) {
            Text(
                stringResource(
                    if (outcome == null) R.string.server_test else R.string.server_test_again,
                ),
            )
        }
    }
}

/**
 * Shown when Android is refusing the app the network. It cannot be asked for
 * back, so the only useful action is a trip to the settings entry.
 */
@Composable
private fun NetworkPermissionDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_permission_title)) },
        text = { Text(stringResource(R.string.network_permission_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_app_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.network_permission_not_now))
            }
        },
    )
}
