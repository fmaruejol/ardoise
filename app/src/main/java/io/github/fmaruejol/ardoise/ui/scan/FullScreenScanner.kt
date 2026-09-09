package io.github.fmaruejol.ardoise.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R

/**
 * The scanner with the whole screen to itself.
 *
 * **A state of the join screen, not a destination**, like the split editor on
 * the expense form: it exists to fill in the link that screen is waiting for.
 * It lives here rather than in `ui/join` because everything on it is about the
 * camera.
 */
@Composable
fun FullScreenScanner(
    onScan: (String) -> Unit,
    onClose: () -> Unit,
    onUseLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        QrViewfinder(
            onScan = onScan,
            modifier = Modifier.fillMaxSize(),
            torchOn = torchOn,
            onTorchAvailable = { hasTorch = it },
        )

        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            ScannerBar(
                torchOn = torchOn,
                hasTorch = hasTorch,
                onClose = onClose,
                onToggleTorch = { torchOn = !torchOn },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                ScanFrame(modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Text(
                    text = stringResource(R.string.scan_full_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }

            // The way back to the link field.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onUseLink,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.textButtonColors(
                        // Over a camera picture a tint has to carry its own
                        // background: there is no telling what is behind it.
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_link),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(end = 0.dp),
                    )
                    Text(
                        text = stringResource(R.string.scan_full_use_link),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerBar(
    torchOn: Boolean,
    hasTorch: Boolean,
    onClose: () -> Unit,
    onToggleTorch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.close),
            )
        }
        Text(
            text = stringResource(R.string.scan_full_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        // Absent rather than disabled without a lamp: a control that cannot do
        // anything is a question the user has to answer.
        if (hasTorch) {
            IconButton(
                onClick = onToggleTorch,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    painter = painterResource(
                        if (torchOn) R.drawable.ic_flash_off else R.drawable.ic_flash_on,
                    ),
                    contentDescription = stringResource(
                        if (torchOn) R.string.scan_full_torch_off else R.string.scan_full_torch_on,
                    ),
                )
            }
        }
    }
}

/**
 * The four corner brackets around the target area, drawn because a Compose
 * `border` is all four sides or none.
 *
 * Only a guide: the analyzer reads the whole frame, so a code a little outside
 * still scans. A frame that cropped would turn a near miss into a failure.
 */
@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier = modifier) {
        val arm = 48.dp.toPx()
        val radius = 16.dp.toPx()
        val stroke = 4.dp.toPx()
        // Half the stroke in, so the line is inside the box.
        val bounds = Rect(
            offset = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
        )

        val path = Path().apply {
            moveTo(bounds.left, bounds.top + arm)
            lineTo(bounds.left, bounds.top + radius)
            quadraticTo(bounds.left, bounds.top, bounds.left + radius, bounds.top)
            lineTo(bounds.left + arm, bounds.top)
            moveTo(bounds.right - arm, bounds.top)
            lineTo(bounds.right - radius, bounds.top)
            quadraticTo(bounds.right, bounds.top, bounds.right, bounds.top + radius)
            lineTo(bounds.right, bounds.top + arm)
            moveTo(bounds.right, bounds.bottom - arm)
            lineTo(bounds.right, bounds.bottom - radius)
            quadraticTo(bounds.right, bounds.bottom, bounds.right - radius, bounds.bottom)
            lineTo(bounds.right - arm, bounds.bottom)
            moveTo(bounds.left + arm, bounds.bottom)
            lineTo(bounds.left + radius, bounds.bottom)
            quadraticTo(bounds.left, bounds.bottom, bounds.left, bounds.bottom - radius)
            lineTo(bounds.left, bounds.bottom - arm)
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke))
    }
}
