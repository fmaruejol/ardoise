package io.github.fmaruejol.ardoise.ui.join

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.components.ArdoiseTextField
import io.github.fmaruejol.ardoise.ui.components.OrDivider
import io.github.fmaruejol.ardoise.ui.describe
import io.github.fmaruejol.ardoise.ui.scan.FullScreenScanner
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun JoinGroupRoute(
    onJoined: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JoinGroupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val currentOnJoined by rememberUpdatedState(onJoined)
    LaunchedEffect(state.joinedGroupId) {
        state.joinedGroupId?.let { groupId ->
            viewModel.onNavigationHandled()
            currentOnJoined(groupId)
        }
    }

    JoinGroupScreen(
        state = state,
        onBack = onBack,
        onLinkChange = viewModel::onLinkChange,
        onScan = viewModel::onScanned,
        onJoin = viewModel::onJoin,
        onFullScreenOpen = viewModel::onFullScreenOpen,
        onFullScreenClose = viewModel::onFullScreenClose,
        modifier = modifier,
    )
}

/**
 * Joining a group: scanning and pasting are the same act with two inputs, and
 * refusing the camera has to leave something usable on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupScreen(
    state: JoinGroupUiState,
    onBack: () -> Unit,
    onLinkChange: (String) -> Unit,
    onScan: (String) -> Unit,
    onJoin: () -> Unit,
    onFullScreenOpen: () -> Unit,
    onFullScreenClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.scanningFullScreen) {
        // Over the screen: the camera is the whole of what the user is doing,
        // and the way out is the bar's close or the link button under it.
        FullScreenScanner(
            onScan = onScan,
            onClose = onFullScreenClose,
            onUseLink = onFullScreenClose,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ScanCard(onOpen = onFullScreenOpen)
            OrDivider()

            LinkField(state = state, onLinkChange = onLinkChange, onJoin = onJoin)

            Button(
                onClick = onJoin,
                enabled = state.canJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                if (state.isJoining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.join_confirm))
                }
            }
        }
    }
}

/**
 * The way to the scanner.
 *
 * **It does not open the camera.** The card used to hold a live viewfinder, so
 * arriving here put the system's dialog in front of somebody who may have come
 * to paste a link. The camera belongs to the full-screen scanner, one tap
 * away, which also makes this screen testable without one.
 */
@Composable
private fun ScanCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_code_scanner),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
            Text(
                text = stringResource(R.string.join_scan_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            // What the camera is for and what happens to what it sees, which
            // is what the system dialog cannot say.
            Text(
                text = stringResource(R.string.join_scan_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_photo_camera),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.join_scan_open),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LinkField(
    state: JoinGroupUiState,
    onLinkChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    ArdoiseTextField(
        value = state.link,
        onValueChange = onLinkChange,
        label = stringResource(R.string.join_link_label),
        placeholder = stringResource(R.string.join_link_placeholder),
        enabled = !state.isJoining,
        isError = state.error != null,
        supportingText = state.error?.describe(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onJoin() }),
        trailing = {
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.getClipEntry()
                            ?.clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(null)
                            ?.toString()
                            ?.takeIf { it.isNotBlank() }
                            ?.let(onLinkChange)
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_paste),
                    contentDescription = stringResource(R.string.join_paste),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun JoinError.describe(): String = when (this) {
    JoinError.NotALink -> stringResource(R.string.join_error_not_a_link)
    JoinError.NotFound -> stringResource(R.string.join_error_not_found)
    is JoinError.Failed -> error.describe()
}
