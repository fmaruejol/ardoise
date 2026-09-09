package io.github.fmaruejol.ardoise.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.theme.WarningColors

/**
 * "Tell us who you are", shown wherever a screen would otherwise leave a
 * number out, the feed's "your share", the balances' "your position".
 *
 * The one amber thing in the app: not an error, since every one of those
 * screens works without an answer, and not the primary action either. It sits
 * in the content rather than over it, because the question is not urgent.
 */
@Composable
fun IdentityPrompt(onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val warning = WarningColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = warning.container,
        contentColor = warning.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person_alert),
                contentDescription = null,
                tint = warning.color,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.group_identity_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.group_identity_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = warning.onContainerVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Button(
                    onClick = onChoose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = warning.color,
                        contentColor = warning.onColor,
                    ),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .height(40.dp),
                ) {
                    Text(stringResource(R.string.group_identity_action))
                }
            }
        }
    }
}
