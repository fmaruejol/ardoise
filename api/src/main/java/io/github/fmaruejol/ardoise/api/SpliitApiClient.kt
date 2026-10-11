package io.github.fmaruejol.ardoise.api

import io.github.fmaruejol.ardoise.api.dto.CreateExpenseInput
import io.github.fmaruejol.ardoise.api.dto.CreateGroupInput
import io.github.fmaruejol.ardoise.api.dto.CreateGroupResponse
import io.github.fmaruejol.ardoise.api.dto.DeleteExpenseInput
import io.github.fmaruejol.ardoise.api.dto.ExpenseIdInput
import io.github.fmaruejol.ardoise.api.dto.ForUserBalancesInput
import io.github.fmaruejol.ardoise.api.dto.ForUserBalancesResponse
import io.github.fmaruejol.ardoise.api.dto.GetExpenseResponse
import io.github.fmaruejol.ardoise.api.dto.GetGroupDetailsResponse
import io.github.fmaruejol.ardoise.api.dto.GetGroupResponse
import io.github.fmaruejol.ardoise.api.dto.GroupIdInput
import io.github.fmaruejol.ardoise.api.dto.GroupIdsInput
import io.github.fmaruejol.ardoise.api.dto.ListActivitiesInput
import io.github.fmaruejol.ardoise.api.dto.ListActivitiesResponse
import io.github.fmaruejol.ardoise.api.dto.ListBalancesResponse
import io.github.fmaruejol.ardoise.api.dto.ListCategoriesResponse
import io.github.fmaruejol.ardoise.api.dto.ListExpensesInput
import io.github.fmaruejol.ardoise.api.dto.ListExpensesResponse
import io.github.fmaruejol.ardoise.api.dto.ListGroupsResponse
import io.github.fmaruejol.ardoise.api.dto.MutateExpenseResponse
import io.github.fmaruejol.ardoise.api.dto.StatsGetResponse
import io.github.fmaruejol.ardoise.api.dto.StatsOverviewResponse
import io.github.fmaruejol.ardoise.api.dto.UpdateExpenseInput
import io.github.fmaruejol.ardoise.api.dto.UpdateGroupInput
import io.github.fmaruejol.ardoise.api.dto.UserGroupRefInput
import io.github.fmaruejol.ardoise.api.trpc.TrpcClient
import io.github.fmaruejol.ardoise.core.model.ActivityPage
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpensePage
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.GroupDetails
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.result.map
import io.github.fmaruejol.ardoise.core.result.mapCatching
import io.github.fmaruejol.ardoise.core.stats.GroupStats
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The tRPC implementation of [SpliitApi]: a procedure from [Procedures] plus
 * the mapping to a domain model.
 */
class SpliitApiClient(private val client: TrpcClient) : SpliitApi {
    private val json: Json = SpliitJson

    // --- groups ------------------------------------------------------------

    override suspend fun getGroup(groupId: String): SpliitResult<Group?> =
        client.query(
            procedure = Procedures.GROUPS_GET,
            deserializer = GetGroupResponse.serializer(),
            input = encode(GroupIdInput.serializer(), GroupIdInput(groupId)),
        ).mapCatching { it.group?.toDomain() }

    override suspend fun getGroupDetails(groupId: String): SpliitResult<GroupDetails> =
        client.query(
            procedure = Procedures.GROUPS_GET_DETAILS,
            deserializer = GetGroupDetailsResponse.serializer(),
            input = encode(GroupIdInput.serializer(), GroupIdInput(groupId)),
        ).mapCatching {
            GroupDetails(
                group = it.group.toDomain(),
                participantIdsWithExpenses = it.participantsWithExpenses,
            )
        }

    override suspend fun listGroups(groupIds: List<String>): SpliitResult<List<GroupSummary>> =
        client.query(
            procedure = Procedures.GROUPS_LIST,
            deserializer = ListGroupsResponse.serializer(),
            input = encode(GroupIdsInput.serializer(), GroupIdsInput(groupIds)),
        ).mapCatching { response -> response.groups.map { it.toDomain() } }

    override suspend fun createGroup(group: GroupInput): SpliitResult<String> =
        client.mutation(
            procedure = Procedures.GROUPS_CREATE,
            deserializer = CreateGroupResponse.serializer(),
            input = encode(CreateGroupInput.serializer(), CreateGroupInput(group.toDto())),
        ).mapCatching { it.groupId }

    override suspend fun updateGroup(
        groupId: String,
        group: GroupInput,
        participantId: String?,
    ): SpliitResult<Unit> =
        client.mutation(
            procedure = Procedures.GROUPS_UPDATE,
            deserializer = Unit.serializer(),
            input = encode(
                UpdateGroupInput.serializer(),
                UpdateGroupInput(groupId, group.toDto(), participantId),
            ),
        )

    // --- expenses ----------------------------------------------------------

    override suspend fun listExpenses(
        groupId: String,
        cursor: Int?,
        limit: Int?,
        filter: String?,
    ): SpliitResult<ExpensePage> {
        if (limit == null || limit <= MAX_PAGE) return expensePage(groupId, cursor, limit, filter)
        return inPages(cursor ?: 0, limit, { it.id }) { at, size ->
            expensePage(groupId, at, size, filter).map { it.expenses to it.hasMore }
        }.map { (expenses, hasMore) -> ExpensePage(expenses, hasMore) }
    }

    private suspend fun expensePage(
        groupId: String,
        cursor: Int?,
        limit: Int?,
        filter: String?,
    ): SpliitResult<ExpensePage> =
        client.query(
            procedure = Procedures.EXPENSES_LIST,
            deserializer = ListExpensesResponse.serializer(),
            input = encode(
                ListExpensesInput.serializer(),
                ListExpensesInput(groupId, cursor, limit, filter),
            ),
        ).mapCatching { it.toDomain() }

    override suspend fun getExpense(groupId: String, expenseId: String): SpliitResult<Expense> =
        client.query(
            procedure = Procedures.EXPENSES_GET,
            deserializer = GetExpenseResponse.serializer(),
            input = encode(ExpenseIdInput.serializer(), ExpenseIdInput(groupId, expenseId)),
        ).mapCatching { it.expense.toDomain() }

    override suspend fun createExpense(
        groupId: String,
        expense: ExpenseInput,
        participantId: String?,
    ): SpliitResult<String> =
        client.mutation(
            procedure = Procedures.EXPENSES_CREATE,
            deserializer = MutateExpenseResponse.serializer(),
            input = encode(
                CreateExpenseInput.serializer(),
                CreateExpenseInput(groupId, expense.toDto(), participantId),
            ),
        ).mapCatching { it.expenseId }

    override suspend fun updateExpense(
        groupId: String,
        expenseId: String,
        expense: ExpenseInput,
        participantId: String?,
    ): SpliitResult<String> =
        client.mutation(
            procedure = Procedures.EXPENSES_UPDATE,
            deserializer = MutateExpenseResponse.serializer(),
            input = encode(
                UpdateExpenseInput.serializer(),
                UpdateExpenseInput(groupId, expenseId, expense.toDto(), participantId),
            ),
        ).mapCatching { it.expenseId }

    override suspend fun deleteExpense(
        groupId: String,
        expenseId: String,
        participantId: String?,
    ): SpliitResult<Unit> =
        client.mutation(
            procedure = Procedures.EXPENSES_DELETE,
            deserializer = Unit.serializer(),
            input = encode(
                DeleteExpenseInput.serializer(),
                DeleteExpenseInput(groupId, expenseId, participantId),
            ),
        )

    // --- balances ----------------------------------------------------------

    override suspend fun listBalances(groupId: String): SpliitResult<GroupBalances> =
        client.query(
            procedure = Procedures.BALANCES_LIST,
            deserializer = ListBalancesResponse.serializer(),
            input = encode(GroupIdInput.serializer(), GroupIdInput(groupId)),
        ).mapCatching { it.toDomain() }

    override suspend fun balancesForUser(
        groups: List<Pair<String, String>>,
    ): SpliitResult<List<UserGroupBalance>> =
        client.query(
            procedure = Procedures.BALANCES_FOR_USER,
            deserializer = ForUserBalancesResponse.serializer(),
            input = encode(
                ForUserBalancesInput.serializer(),
                ForUserBalancesInput(
                    groups.map { (groupId, participantId) ->
                        UserGroupRefInput(groupId, participantId)
                    },
                ),
            ),
        ).mapCatching { response -> response.balances.map { it.toDomain() } }

    // --- stats -------------------------------------------------------------

    /**
     * Which of the two an instance answers is deliberately not remembered: it
     * would have to be invalidated when the server is upgraded underneath it,
     * to save one `NOT_FOUND`, and only on instances that are behind.
     */
    override suspend fun groupStats(groupId: String): SpliitResult<GroupStats> {
        val overview = client.query(
            procedure = Procedures.STATS_OVERVIEW,
            deserializer = StatsOverviewResponse.serializer(),
            input = encode(GroupIdInput.serializer(), GroupIdInput(groupId)),
        ).mapCatching { it.toDomain() }
        // An unknown group is all zeros, so NOT_FOUND here is the procedure.
        if (overview !is SpliitResult.Failure || overview.error != SpliitError.NotFound) return overview

        return client.query(
            procedure = Procedures.STATS_GET,
            deserializer = StatsGetResponse.serializer(),
            input = encode(GroupIdInput.serializer(), GroupIdInput(groupId)),
        ).mapCatching { GroupStats(total = it.totalGroupSpendings, breakdown = null) }
    }

    // --- activities --------------------------------------------------------

    override suspend fun listActivities(
        groupId: String,
        cursor: Int,
        limit: Int,
    ): SpliitResult<ActivityPage> {
        if (limit <= MAX_PAGE) return activityPage(groupId, cursor, limit)
        return inPages(cursor, limit, { it.id }) { at, size ->
            activityPage(groupId, at, size).map { it.activities to it.hasMore }
        }.map { (activities, hasMore) -> ActivityPage(activities, hasMore) }
    }

    private suspend fun activityPage(groupId: String, cursor: Int, limit: Int): SpliitResult<ActivityPage> =
        client.query(
            procedure = Procedures.ACTIVITIES_LIST,
            deserializer = ListActivitiesResponse.serializer(),
            input = encode(
                ListActivitiesInput.serializer(),
                ListActivitiesInput(groupId, cursor, limit),
            ),
        ).mapCatching { it.toDomain() }

    // --- categories --------------------------------------------------------

    override suspend fun listCategories(): SpliitResult<List<Category>> =
        client.query(
            procedure = Procedures.CATEGORIES_LIST,
            deserializer = ListCategoriesResponse.serializer(),
        ).mapCatching { response -> response.categories.map { it.toDomain() } }

    private fun <T> encode(serializer: SerializationStrategy<T>, value: T): JsonElement =
        json.encodeToJsonElement(serializer, value)

    /**
     * [limit] rows from [cursor] as consecutive requests of at most
     * [MAX_PAGE], stopping early when the server has no more. The cursor is
     * an offset, so a row added between two requests pushes the last one of
     * the first into the second; deduplicating keeps it from counting twice.
     */
    private suspend fun <T> inPages(
        cursor: Int,
        limit: Int,
        id: (T) -> String,
        fetch: suspend (cursor: Int, limit: Int) -> SpliitResult<Pair<List<T>, Boolean>>,
    ): SpliitResult<Pair<List<T>, Boolean>> {
        val rows = mutableListOf<T>()
        var hasMore = true
        var at = cursor
        while (hasMore && at - cursor < limit) {
            val size = minOf(MAX_PAGE, cursor + limit - at)
            when (val page = fetch(at, size)) {
                is SpliitResult.Failure -> {
                    return page
                }

                is SpliitResult.Success -> {
                    rows += page.value.first
                    hasMore = page.value.second
                }
            }
            at += size
        }
        return SpliitResult.Success(rows.distinctBy(id) to hasMore)
    }

    private companion object {
        /** Upstream's zod schema for both list procedures: `limit` is at most 100. */
        const val MAX_PAGE = 100
    }
}

/**
 * The procedure paths, mirroring upstream's router tree. The single place
 * they are written down.
 */
object Procedures {
    const val GROUPS_GET = "groups.get"
    const val GROUPS_GET_DETAILS = "groups.getDetails"
    const val GROUPS_LIST = "groups.list"
    const val GROUPS_CREATE = "groups.create"
    const val GROUPS_UPDATE = "groups.update"

    const val EXPENSES_LIST = "groups.expenses.list"
    const val EXPENSES_GET = "groups.expenses.get"
    const val EXPENSES_CREATE = "groups.expenses.create"
    const val EXPENSES_UPDATE = "groups.expenses.update"
    const val EXPENSES_DELETE = "groups.expenses.delete"

    const val BALANCES_LIST = "groups.balances.list"
    const val BALANCES_FOR_USER = "groups.balances.forUser"

    const val ACTIVITIES_LIST = "groups.activities.list"

    const val STATS_OVERVIEW = "groups.stats.overview"
    const val STATS_GET = "groups.stats.get"

    const val CATEGORIES_LIST = "categories.list"
}
