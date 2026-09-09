package io.github.fmaruejol.ardoise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.currency.format
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.data.PendingExpense

/**
 * "Pending upload": expenses typed here that the server has not been told
 * about. Above even the totals, because it is the one thing on the screen that
 * is not yet true of the group.
 *
 * **The outline is dashed, and that is the point**, since a solid card would
 * read as one more thing in the list. Tapping a row opens the expense, where it can
 * be corrected or thrown away, which is what makes sending it again safe.
 */
@Composable
fun PendingUploadSection(
    pending: List<PendingExpense>,
    currency: GroupCurrency,
    categories: List<Category>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.outbox_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            CountBadge(pending.size)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dashedBorder(MaterialTheme.colorScheme.errorContainer, PendingCorner)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            pending.forEach { queued ->
                PendingRow(
                    pending = queued,
                    currency = currency,
                    category = categories.firstOrNull { it.id == queued.input.categoryId },
                    onClick = { onClick(queued.id) },
                )
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun PendingRow(
    pending: PendingExpense,
    currency: GroupCurrency,
    category: Category?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                // The error tint rather than the category's: what the row says
                // is that it has not been sent.
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(categoryIcon(category?.grouping, category?.name)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = pending.input.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule_send),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.outbox_row_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Text(
            text = currency.format(pending.input.amount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val PendingCorner = 16.dp

/**
 * A dashed rounded outline, drawn rather than declared: `BorderStroke` has
 * nowhere to put a [PathEffect].
 */
private fun Modifier.dashedBorder(color: Color, corner: Dp) = drawBehind {
    val width = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(
            width = width,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}
