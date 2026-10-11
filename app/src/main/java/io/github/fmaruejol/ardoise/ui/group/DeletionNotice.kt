package io.github.fmaruejol.ardoise.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.components.FullDate
import io.github.fmaruejol.ardoise.ui.components.rememberDateFormatter
import java.time.Instant
import java.time.ZoneId

/**
 * A group somebody has asked the server to delete. It stays usable until
 * [deleteAt], and anyone holding the link can take the deletion back until
 * then, so this sits over the feed rather than in a menu.
 */
@Composable
internal fun DeletionNotice(
    deleteAt: Instant,
    canRestore: Boolean,
    isRestoring: Boolean,
    restoreFailed: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = rememberDateFormatter(FullDate).format(deleteAt.atZone(ZoneId.systemDefault()))
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.group_deletion_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (canRestore) {
                    stringResource(R.string.group_deletion_body, date)
                } else {
                    stringResource(R.string.group_deletion_past)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (restoreFailed) {
                Text(
                    text = stringResource(R.string.group_restore_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (canRestore) {
                Button(
                    onClick = onRestore,
                    enabled = !isRestoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.group_restore))
                }
            }
        }
    }
}
