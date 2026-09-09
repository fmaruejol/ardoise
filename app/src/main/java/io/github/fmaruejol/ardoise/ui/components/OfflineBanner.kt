package io.github.fmaruejol.ardoise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.ui.describe

/**
 * The strip under the app bar when a read came from the cache.
 *
 * It goes up for two different reasons: a read that failed says what went
 * wrong, and a **null** [error] means the platform reports no network, which
 * is why a screen already open when the phone loses signal does not sit there
 * looking current. The platform's answer is read for nothing else.
 */
@Composable
fun OfflineBanner(
    error: SpliitError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_off),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                // Only a failure to reach the server is "offline"; anything
                // else has something specific to say.
                text = if (error == null || error is SpliitError.Network) {
                    stringResource(R.string.offline_banner)
                } else {
                    error.describe()
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRetry,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.offline_retry))
            }
        }
    }
}
