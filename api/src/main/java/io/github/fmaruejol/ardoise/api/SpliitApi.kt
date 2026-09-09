package io.github.fmaruejol.ardoise.api

import io.github.fmaruejol.ardoise.core.model.ActivityPage
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpensePage
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.GroupDetails
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import io.github.fmaruejol.ardoise.core.result.SpliitResult

/**
 * Every Spliit tRPC procedure this client speaks, as suspending functions
 * returning domain types. The names and input shapes are undocumented
 * upstream and are written down in [Procedures]; when adding one, read the
 * procedure *and* the Prisma `select` behind it, which decides what comes
 * back. Not yet mapped: `groups.stats.*`.
 *
 * An interface because this is the seam between `:app` and `:api`: it lets a
 * repository test state its case instead of staging HTTP responses.
 */
interface SpliitApi {
    // --- groups ------------------------------------------------------------

    /** Null when no group has this id, since an unknown id is not an error here. */
    suspend fun getGroup(groupId: String): SpliitResult<Group?>

    /** Fails with `NOT_FOUND` when the group does not exist. */
    suspend fun getGroupDetails(groupId: String): SpliitResult<GroupDetails>

    /**
     * Groups are addressed by id: this is how the remembered ids become a
     * list. Ids that no longer exist are absent rather than reported.
     */
    suspend fun listGroups(groupIds: List<String>): SpliitResult<List<GroupSummary>>

    /** Returns the new group's id. */
    suspend fun createGroup(group: GroupInput): SpliitResult<String>

    /** [participantId] only attributes the change in the activity log. */
    suspend fun updateGroup(
        groupId: String,
        group: GroupInput,
        participantId: String? = null,
    ): SpliitResult<Unit>

    // --- expenses ----------------------------------------------------------

    /**
     * One page of expenses, newest first. [cursor] is a count of rows to skip;
     * [filter] is a case-insensitive substring of the title.
     */
    suspend fun listExpenses(
        groupId: String,
        cursor: Int? = null,
        limit: Int? = null,
        filter: String? = null,
    ): SpliitResult<ExpensePage>

    /** Fails with `NOT_FOUND` when the expense does not exist. */
    suspend fun getExpense(groupId: String, expenseId: String): SpliitResult<Expense>

    /**
     * Returns the new expense's id. **Not idempotent**: a retry after a
     * timeout creates a second expense, which is a wrong balance for everyone.
     */
    suspend fun createExpense(
        groupId: String,
        expense: ExpenseInput,
        participantId: String? = null,
    ): SpliitResult<String>

    suspend fun updateExpense(
        groupId: String,
        expenseId: String,
        expense: ExpenseInput,
        participantId: String? = null,
    ): SpliitResult<String>

    suspend fun deleteExpense(
        groupId: String,
        expenseId: String,
        participantId: String? = null,
    ): SpliitResult<Unit>

    // --- balances ----------------------------------------------------------

    /**
     * The group's balances and the payments that would settle it. Derived from
     * the suggested reimbursements, not raw totals, see
     * [io.github.fmaruejol.ardoise.core.model.Balance].
     */
    suspend fun listBalances(groupId: String): SpliitResult<GroupBalances>

    /** The user's net position across several groups. Unresolved ones are dropped. */
    suspend fun balancesForUser(
        groups: List<Pair<String, String>>,
    ): SpliitResult<List<UserGroupBalance>>

    // --- activities --------------------------------------------------------

    suspend fun listActivities(
        groupId: String,
        cursor: Int = 0,
        limit: Int = 5,
    ): SpliitResult<ActivityPage>

    // --- categories --------------------------------------------------------

    /** The server's fixed category list. Takes no input. */
    suspend fun listCategories(): SpliitResult<List<Category>>
}
