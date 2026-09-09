package io.github.fmaruejol.ardoise.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.openAppSettings

/**
 * Asks for the camera where the camera is, and says what to do when there is
 * none. Split out because its three answers, no camera, permission refused,
 * or a preview, are about the camera rather than about what is being read.
 *
 * **Asking on arrival is honest here**: the only screen with a camera is the
 * full-screen scanner, reached from the join screen's "Open scanner", so the
 * dialog follows a tap that asked for it. Refusing costs nothing, since the
 * join screen is mostly a link field.
 *
 * @param noCameraText what to say on a device with no camera at all.
 * @param deniedText what to say when the permission was refused.
 */
@Composable
fun CameraGate(
    noCameraText: String,
    deniedText: String,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val context = LocalContext.current

    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> granted = allowed }

    LaunchedEffect(hasCamera, granted) {
        if (hasCamera && !granted) request.launch(Manifest.permission.CAMERA)
    }

    when {
        !hasCamera -> CameraMessage(noCameraText, modifier)

        granted -> content(modifier)

        else -> CameraMessage(
            text = deniedText,
            modifier = modifier,
            // Refused twice, and it can no longer be asked for at all.
            actionLabel = stringResource(R.string.open_app_settings),
            onAction = { context.openAppSettings() },
        )
    }
}

@Composable
internal fun CameraMessage(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
