package io.github.fmaruejol.ardoise.ui.scan

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.fmaruejol.ardoise.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.util.concurrent.Executors

/**
 * A live camera preview that reports the first QR code it reads. It fills
 * whatever box it is given rather than owning a screen.
 */
@Composable
fun QrViewfinder(
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier,
    torchOn: Boolean = false,
    onTorchAvailable: (Boolean) -> Unit = {},
) {
    CameraGate(
        noCameraText = stringResource(R.string.join_no_camera),
        deniedText = stringResource(R.string.join_camera_off_body),
        modifier = modifier,
    ) { inner ->
        Camera(
            onScan = onScan,
            modifier = inner,
            torchOn = torchOn,
            onTorchAvailable = onTorchAvailable,
        )
    }
}

@Composable
private fun Camera(
    onScan: (String) -> Unit,
    modifier: Modifier = Modifier,
    torchOn: Boolean = false,
    onTorchAvailable: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var failed by remember { mutableStateOf(false) }

    // The analyzer runs on the camera executor; its result crosses back here.
    val scanned = remember { MutableStateFlow<String?>(null) }
    val analyzer = remember { QrCodeAnalyzer { code -> scanned.value = code } }
    val currentOnScan by rememberUpdatedState(onScan)
    LaunchedEffect(Unit) {
        currentOnScan(scanned.filterNotNull().first())
    }

    // Whether there is a lamp is the camera's answer and arrives only once one
    // is bound, so the button cannot be drawn until then.
    val currentOnTorchAvailable by rememberUpdatedState(onTorchAvailable)

    // Only ever what the caller asked for: the torch belongs to the camera and
    // stays on across a recomposition.
    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    DisposableEffect(Unit) {
        onDispose {
            // Leaving the screen does not end the lifecycle the use cases are
            // bound to, so nothing else releases the camera.
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        try {
            val cameraProvider = ProcessCameraProvider.awaitInstance(context)
            provider = cameraProvider

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analysis = ImageAnalysis.Builder()
                // Decoding is slower than the frame rate, and a stale frame
                // shows a code that has already moved.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, analyzer)

            // FEATURE_CAMERA_ANY includes devices whose only camera faces the
            // user.
            val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            cameraProvider.unbindAll()
            camera = cameraProvider
                .bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                .also { currentOnTorchAvailable(it.cameraInfo.hasFlashUnit()) }
        } catch (e: CancellationException) {
            // Being interrupted is not a camera failure: caught below it would
            // set `failed`, which survives in `remember`, so a viewfinder that
            // was merely left would come back saying the camera is broken.
            throw e
        } catch (e: Exception) {
            // A camera held by another app, or a provider that never starts.
            // Not worth a stack trace to someone splitting a bill.
            failed = true
        }
    }

    if (failed) {
        CameraMessage(stringResource(R.string.join_camera_failed), modifier)
    } else {
        AndroidView(
            factory = { previewView },
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}
