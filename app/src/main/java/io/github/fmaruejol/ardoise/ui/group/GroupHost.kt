package io.github.fmaruejol.ardoise.ui.group

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.fmaruejol.ardoise.ui.activity.ActivityRoute
import io.github.fmaruejol.ardoise.ui.balances.BalancesRoute

/**
 * The three views of one group, behind one bottom bar.
 *
 * The tabs are **not** navigation destinations: on the back stack the back
 * button would walk through whichever tabs had been looked at, which is not
 * what a bottom bar means anywhere else on Android.
 */
@Composable
fun GroupHost(
    groupId: String,
    onBack: () -> Unit,
    onExpenseClick: (String) -> Unit,
    onAddExpense: () -> Unit,
    onGroupSettings: () -> Unit,
    onTotals: () -> Unit,
    onSettleUp: () -> Unit,
    onPendingClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(GroupTab.Expenses) }
    val bar: @Composable () -> Unit = { GroupTabs(selected = tab, onSelect = { tab = it }) }

    when (tab) {
        GroupTab.Expenses -> GroupRoute(
            groupId = groupId,
            onBack = onBack,
            onExpenseClick = onExpenseClick,
            onAddExpense = onAddExpense,
            onGroupSettings = onGroupSettings,
            onPendingClick = onPendingClick,
            modifier = modifier,
            bottomBar = bar,
        )

        GroupTab.Balances -> BalancesRoute(
            groupId = groupId,
            onBack = onBack,
            onSettleUp = onSettleUp,
            onTotals = onTotals,
            onGroupSettings = onGroupSettings,
            modifier = modifier,
            bottomBar = bar,
        )

        GroupTab.Activity -> ActivityRoute(
            groupId = groupId,
            onBack = onBack,
            onExpenseClick = onExpenseClick,
            modifier = modifier,
            bottomBar = bar,
        )
    }
}
