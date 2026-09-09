package io.github.fmaruejol.ardoise.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.fmaruejol.ardoise.R
import io.github.fmaruejol.ardoise.ui.balances.SettleUpRoute
import io.github.fmaruejol.ardoise.ui.creategroup.CreateGroupRoute
import io.github.fmaruejol.ardoise.ui.expense.ExpenseDetailRoute
import io.github.fmaruejol.ardoise.ui.expense.ExpenseFormRoute
import io.github.fmaruejol.ardoise.ui.expense.ReimbursementPrefill
import io.github.fmaruejol.ardoise.ui.group.GroupHost
import io.github.fmaruejol.ardoise.ui.grouplist.GroupListRoute
import io.github.fmaruejol.ardoise.ui.groupsettings.GroupSettingsRoute
import io.github.fmaruejol.ardoise.ui.home.HomeRoute
import io.github.fmaruejol.ardoise.ui.join.JoinGroupRoute
import io.github.fmaruejol.ardoise.ui.server.ServerRoute
import io.github.fmaruejol.ardoise.ui.settings.AboutScreen
import io.github.fmaruejol.ardoise.ui.settings.SettingsRoute
import io.github.fmaruejol.ardoise.ui.totals.TotalsRoute
import org.koin.androidx.compose.koinViewModel

object Routes {
    /** What a new install opens on. Nothing is asked for; the server is a footnote. */
    const val HOME = "home"
    const val GROUPS = "groups"
    const val SERVER = "server"
    const val JOIN = "join"
    const val CREATE_GROUP = "create-group"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val GROUP = "group/{groupId}"
    const val GROUP_SETTINGS = "group/{groupId}/settings"

    /** The expense form. The optional arguments are what "Mark as paid" fills in. */
    const val NEW_EXPENSE =
        "group/{groupId}/expense?from={from}&to={to}&amount={amount}"
    const val EXPENSE = "group/{groupId}/expense/{expenseId}"
    const val EDIT_EXPENSE = "group/{groupId}/expense/{expenseId}/edit"
    const val SETTLE_UP = "group/{groupId}/settle"

    /**
     * An expense typed offline. Its id is local and nothing on the server
     * has one, which is why it is a route of its own.
     */
    const val QUEUED_EXPENSE = "group/{groupId}/queued/{pendingId}"
    const val TOTALS = "group/{groupId}/totals"

    fun group(groupId: String) = "group/$groupId"

    fun groupSettings(groupId: String) = "group/$groupId/settings"

    fun newExpense(groupId: String) = "group/$groupId/expense"

    /** The form, opened on a payment that would settle part of the group. */
    fun settleExpense(groupId: String, from: String, to: String, amount: Long) =
        "group/$groupId/expense?from=$from&to=$to&amount=$amount"

    fun expense(groupId: String, expenseId: String) = "group/$groupId/expense/$expenseId"

    fun editExpense(groupId: String, expenseId: String) =
        "group/$groupId/expense/$expenseId/edit"

    fun settleUp(groupId: String) = "group/$groupId/settle"

    fun queuedExpense(groupId: String, pendingId: String) =
        "group/$groupId/queued/$pendingId"

    fun totals(groupId: String) = "group/$groupId/totals"

    const val GROUP_ID_ARG = "groupId"
    const val EXPENSE_ID_ARG = "expenseId"
    const val FROM_ARG = "from"
    const val TO_ARG = "to"

    /** Text, not a `Long`: `NavType.LongType` has no null and this is optional. */
    const val AMOUNT_ARG = "amount"
    const val PENDING_ID_ARG = "pendingId"
}

@Composable
fun ArdoiseApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: AppViewModel = koinViewModel(),
) {
    // Nothing to draw until it is known whether this is a first run.
    val startDestination = viewModel.startDestination.collectAsStateWithLifecycle().value ?: return

    // Nor until the language is known: one frame in the wrong one is worse.
    val language = viewModel.language.collectAsStateWithLifecycle().value ?: return

    /** Leaves the first-run screen behind: there is nothing to go back to. */
    fun toGroups() {
        navController.navigate(Routes.GROUPS) {
            popUpTo(Routes.HOME) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Every screen below reads its strings, dates and amounts in the chosen
    // language; nothing above this line draws any.
    ProvideAppLanguage(language) {
        // No Scaffold here: each screen brings its own, and nesting two applies
        // the status bar inset twice.
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                    onJoinGroup = { navController.navigate(Routes.JOIN) },
                    onChangeServer = { navController.navigate(Routes.SERVER) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.GROUPS) {
                GroupListRoute(
                    onGroupClick = { navController.navigate(Routes.group(it)) },
                    onCreateGroup = { navController.navigate(Routes.CREATE_GROUP) },
                    onJoinGroup = { navController.navigate(Routes.JOIN) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.JOIN) {
                JoinGroupRoute(
                    // Where the new group appears, and what replaces the
                    // first-run screen.
                    onJoined = { toGroups() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.CREATE_GROUP) {
                CreateGroupRoute(
                    onCreated = { toGroups() },
                    onClose = { navController.popBackStack() },
                )
            }

            composable(Routes.SERVER) {
                ServerRoute(
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onServer = { navController.navigate(Routes.SERVER) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.GROUP) { entry ->
                val groupId = entry.groupId()
                GroupHost(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onExpenseClick = { navController.navigate(Routes.expense(groupId, it)) },
                    onAddExpense = { navController.navigate(Routes.newExpense(groupId)) },
                    onGroupSettings = { navController.navigate(Routes.groupSettings(groupId)) },
                    onTotals = { navController.navigate(Routes.totals(groupId)) },
                    onSettleUp = { navController.navigate(Routes.settleUp(groupId)) },
                    onPendingClick = {
                        navController.navigate(Routes.queuedExpense(groupId, it))
                    },
                )
            }

            composable(Routes.GROUP_SETTINGS) { entry ->
                GroupSettingsRoute(
                    groupId = entry.groupId(),
                    onBack = { navController.popBackStack() },
                    // The group is off this device, so the screen behind this
                    // one has nothing left to show.
                    onRemoved = { navController.popBackStack(Routes.GROUPS, inclusive = false) },
                )
            }

            composable(
                Routes.NEW_EXPENSE,
                arguments = listOf(
                    optionalArgument(Routes.FROM_ARG),
                    optionalArgument(Routes.TO_ARG),
                    optionalArgument(Routes.AMOUNT_ARG),
                ),
            ) { entry ->
                ExpenseFormRoute(
                    groupId = entry.groupId(),
                    expenseId = null,
                    onDone = { navController.popBackStack() },
                    onClose = { navController.popBackStack() },
                    // Resolved here because a ViewModel has no Context.
                    prefill = entry.reimbursementPrefill(
                        title = stringResource(R.string.expense_reimbursement_title),
                    ),
                )
            }

            composable(Routes.EXPENSE) { entry ->
                val groupId = entry.groupId()
                val expenseId = entry.expenseId()
                ExpenseDetailRoute(
                    groupId = groupId,
                    expenseId = expenseId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editExpense(groupId, expenseId)) },
                )
            }

            composable(Routes.QUEUED_EXPENSE) { entry ->
                ExpenseFormRoute(
                    groupId = entry.groupId(),
                    expenseId = null,
                    pendingId = entry.arguments?.getString(Routes.PENDING_ID_ARG),
                    onDone = { navController.popBackStack() },
                    onClose = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTLE_UP) { entry ->
                val groupId = entry.groupId()
                SettleUpRoute(
                    groupId = groupId,
                    onClose = { navController.popBackStack() },
                    // Settling up writes nothing itself: it fills the form in
                    // and the user saves it, as the web client does.
                    onMarkPaid = { transfer ->
                        navController.navigate(
                            Routes.settleExpense(
                                groupId = groupId,
                                from = transfer.fromId,
                                to = transfer.toId,
                                amount = transfer.amount,
                            ),
                        )
                    },
                )
            }

            composable(Routes.TOTALS) { entry ->
                TotalsRoute(
                    groupId = entry.groupId(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.EDIT_EXPENSE) { entry ->
                ExpenseFormRoute(
                    groupId = entry.groupId(),
                    expenseId = entry.expenseId(),
                    // Back to the expense, where the change shows.
                    onDone = { navController.popBackStack() },
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}

/** A query argument that is simply absent most of the time. */
private fun optionalArgument(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

/**
 * The payment "Mark as paid" was tapped on, or null. All three arguments have
 * to be there: two thirds of a payment is not one.
 */
private fun NavBackStackEntry.reimbursementPrefill(title: String): ReimbursementPrefill? {
    val from = arguments?.getString(Routes.FROM_ARG) ?: return null
    val to = arguments?.getString(Routes.TO_ARG) ?: return null
    val amount = arguments?.getString(Routes.AMOUNT_ARG)?.toLongOrNull() ?: return null
    return ReimbursementPrefill(payerId = from, payeeId = to, amount = amount, title = title)
}

private fun NavBackStackEntry.groupId(): String =
    arguments?.getString(Routes.GROUP_ID_ARG).orEmpty()

private fun NavBackStackEntry.expenseId(): String =
    arguments?.getString(Routes.EXPENSE_ID_ARG).orEmpty()
