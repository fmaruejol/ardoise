package io.github.fmaruejol.ardoise.ui.group

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.fmaruejol.ardoise.R

/** The three views of a group, in the bottom bar. */
enum class GroupTab {
    Expenses,
    Balances,
    Activity,
}

private fun GroupTab.icon(): Int = when (this) {
    GroupTab.Expenses -> R.drawable.ic_receipt_long
    GroupTab.Balances -> R.drawable.ic_balance
    GroupTab.Activity -> R.drawable.ic_history
}

private fun GroupTab.label(): Int = when (this) {
    GroupTab.Expenses -> R.string.tab_expenses
    GroupTab.Balances -> R.string.tab_balances
    GroupTab.Activity -> R.string.tab_activity
}

/**
 * The bottom bar inside a group, passed into each tab's screen rather than
 * wrapping them: two `Scaffold`s apply the window insets twice.
 *
 * The tabs are not navigation destinations. See [GroupHost].
 */
@Composable
fun GroupTabs(
    selected: GroupTab,
    onSelect: (GroupTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        GroupTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.icon()),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(tab.label())) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * The group's own menu: one item, now that Totals has its own button on the balances.
 */
@Composable
fun GroupOverflowMenu(onGroupSettings: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.group_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_settings_title)) },
                onClick = {
                    expanded = false
                    onGroupSettings()
                },
            )
        }
    }
}
