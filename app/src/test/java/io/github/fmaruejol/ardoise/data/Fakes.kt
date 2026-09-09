package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.api.ExchangeRates
import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.api.GroupInput
import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.model.ActivityPage
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpensePage
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.GroupDetails
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** A [SpliitApi] whose answers the test states outright, and which records what it was asked. */
class FakeSpliitApi : SpliitApi {
    var groupResult: SpliitResult<Group?> = SpliitResult.Success(null)
    var groupDetailsResult: SpliitResult<GroupDetails> = notImplemented()
    var listGroupsResult: SpliitResult<List<GroupSummary>> = SpliitResult.Success(emptyList())
    var createGroupResult: SpliitResult<String> = SpliitResult.Success("new-group")
    var updateGroupResult: SpliitResult<Unit> = SpliitResult.Success(Unit)
    var listExpensesResult: SpliitResult<ExpensePage> =
        SpliitResult.Success(ExpensePage(emptyList(), hasMore = false))
    var getExpenseResult: SpliitResult<Expense> = notImplemented()
    var createExpenseResult: SpliitResult<String> = SpliitResult.Success("new-expense")
    var updateExpenseResult: SpliitResult<String> = SpliitResult.Success("expense")
    var deleteExpenseResult: SpliitResult<Unit> = SpliitResult.Success(Unit)
    var listBalancesResult: SpliitResult<GroupBalances> =
        SpliitResult.Success(GroupBalances(emptyList(), emptyList()))
    var balancesForUserResult: SpliitResult<List<UserGroupBalance>> = SpliitResult.Success(emptyList())
    var listActivitiesResult: SpliitResult<ActivityPage> =
        SpliitResult.Success(ActivityPage(emptyList(), hasMore = false))
    var listCategoriesResult: SpliitResult<List<Category>> = SpliitResult.Success(emptyList())

    /** Every call, in order, as `procedure` plus the arguments worth asserting on. */
    val calls = mutableListOf<Call>()

    data class Call(val procedure: String, val arguments: List<Any?>)

    fun callsTo(procedure: String): List<Call> = calls.filter { it.procedure == procedure }

    override suspend fun getGroup(groupId: String): SpliitResult<Group?> {
        calls += Call("getGroup", listOf(groupId))
        return groupResult
    }

    override suspend fun getGroupDetails(groupId: String): SpliitResult<GroupDetails> {
        calls += Call("getGroupDetails", listOf(groupId))
        return groupDetailsResult
    }

    override suspend fun listGroups(groupIds: List<String>): SpliitResult<List<GroupSummary>> {
        calls += Call("listGroups", listOf(groupIds))
        return listGroupsResult
    }

    override suspend fun createGroup(group: GroupInput): SpliitResult<String> {
        calls += Call("createGroup", listOf(group))
        return createGroupResult
    }

    override suspend fun updateGroup(
        groupId: String,
        group: GroupInput,
        participantId: String?,
    ): SpliitResult<Unit> {
        calls += Call("updateGroup", listOf(groupId, group, participantId))
        return updateGroupResult
    }

    override suspend fun listExpenses(
        groupId: String,
        cursor: Int?,
        limit: Int?,
        filter: String?,
    ): SpliitResult<ExpensePage> {
        calls += Call("listExpenses", listOf(groupId, cursor, limit, filter))
        return listExpensesResult
    }

    override suspend fun getExpense(groupId: String, expenseId: String): SpliitResult<Expense> {
        calls += Call("getExpense", listOf(groupId, expenseId))
        return getExpenseResult
    }

    override suspend fun createExpense(
        groupId: String,
        expense: ExpenseInput,
        participantId: String?,
    ): SpliitResult<String> {
        calls += Call("createExpense", listOf(groupId, expense, participantId))
        return createExpenseResult
    }

    override suspend fun updateExpense(
        groupId: String,
        expenseId: String,
        expense: ExpenseInput,
        participantId: String?,
    ): SpliitResult<String> {
        calls += Call("updateExpense", listOf(groupId, expenseId, expense, participantId))
        return updateExpenseResult
    }

    override suspend fun deleteExpense(
        groupId: String,
        expenseId: String,
        participantId: String?,
    ): SpliitResult<Unit> {
        calls += Call("deleteExpense", listOf(groupId, expenseId, participantId))
        return deleteExpenseResult
    }

    override suspend fun listBalances(groupId: String): SpliitResult<GroupBalances> {
        calls += Call("listBalances", listOf(groupId))
        return listBalancesResult
    }

    override suspend fun balancesForUser(
        groups: List<Pair<String, String>>,
    ): SpliitResult<List<UserGroupBalance>> {
        calls += Call("balancesForUser", listOf(groups))
        return balancesForUserResult
    }

    override suspend fun listActivities(
        groupId: String,
        cursor: Int,
        limit: Int,
    ): SpliitResult<ActivityPage> {
        calls += Call("listActivities", listOf(groupId, cursor, limit))
        return listActivitiesResult
    }

    override suspend fun listCategories(): SpliitResult<List<Category>> {
        calls += Call("listCategories", emptyList())
        return listCategoriesResult
    }

    private companion object {
        fun <T> notImplemented(): SpliitResult<T> = SpliitResult.Failure(
            SpliitError.Malformed(NotImplementedError("the test did not set this result")),
        )
    }
}

/** Rates the test states outright, and a record of what was asked for. */
class FakeExchangeRates(var rate: BigDecimal? = BigDecimal("0.17857")) : ExchangeRates {
    val asked = mutableListOf<Triple<LocalDate, String, String>>()

    override suspend fun rate(date: LocalDate, base: String, target: String): BigDecimal? {
        asked += Triple(date, base, target)
        return rate
    }
}

/** [GroupPreferences] in memory. */
class FakeGroupPreferences(
    initialGroupIds: List<String> = emptyList(),
    initialBaseUrl: String = GroupPreferences.DEFAULT_BASE_URL,
) : GroupPreferences {
    private val state = MutableStateFlow(
        State(
            baseUrl = initialBaseUrl,
            serverChosen = false,
            groupIds = mapOf(initialBaseUrl to initialGroupIds),
            activeParticipants = emptyMap(),
        ),
    )

    private data class State(
        val baseUrl: String,
        val serverChosen: Boolean,
        val groupIds: Map<String, List<String>>,
        val activeParticipants: Map<String, String>,
        val defaultCurrency: String = GroupCurrency.defaultCodeFor(),
        val languageTag: String? = null,
        val themeName: String? = null,
    )

    override val baseUrl: Flow<String> = state.map { it.baseUrl }

    override val hasChosenServer: Flow<Boolean> = state.map { it.serverChosen }

    override val knownGroupIds: Flow<List<String>> =
        state.map { it.groupIds[it.baseUrl].orEmpty() }

    override fun knownGroupIds(baseUrl: String): Flow<List<String>> =
        state.map { it.groupIds[baseUrl.withTrailingSlash()].orEmpty() }

    override fun activeParticipantId(groupId: String): Flow<String?> =
        state.map { it.activeParticipants[groupId] }

    override val defaultCurrencyCode: Flow<String> = state.map { it.defaultCurrency }

    override val languageTag: Flow<String?> = state.map { it.languageTag }

    override val themeName: Flow<String?> = state.map { it.themeName }

    override suspend fun setThemeName(name: String?) {
        state.value = state.value.copy(themeName = name)
    }

    override suspend fun setLanguageTag(tag: String?) {
        state.value = state.value.copy(languageTag = tag)
    }

    override suspend fun setDefaultCurrencyCode(code: String) {
        state.value = state.value.copy(defaultCurrency = code)
    }

    override suspend fun rememberGroup(groupId: String) {
        state.value = state.value.let { current ->
            val ids = current.groupIds[current.baseUrl].orEmpty()
            current.copy(
                groupIds = current.groupIds + (
                    current.baseUrl to listOf(groupId) + ids.filterNot { it == groupId }
                ),
            )
        }
    }

    override suspend fun forgetGroup(groupId: String) {
        state.value = state.value.let { current ->
            val ids = current.groupIds[current.baseUrl].orEmpty()
            current.copy(
                groupIds = current.groupIds + (current.baseUrl to ids.filterNot { it == groupId }),
                activeParticipants = current.activeParticipants - groupId,
            )
        }
    }

    override suspend fun setActiveParticipantId(groupId: String, participantId: String?) {
        state.value = state.value.let {
            it.copy(
                activeParticipants = if (participantId == null) {
                    it.activeParticipants - groupId
                } else {
                    it.activeParticipants + (groupId to participantId)
                },
            )
        }
    }

    override suspend fun setBaseUrl(baseUrl: String) {
        state.value = state.value.copy(
            baseUrl = baseUrl.withTrailingSlash(),
            serverChosen = true,
        )
    }

    private companion object {
        fun String.withTrailingSlash() = if (endsWith("/")) this else "$this/"
    }
}

fun groupSummary(id: String, name: String = id) = GroupSummary(
    id = id,
    name = name,
    information = null,
    currencySymbol = "€",
    currencyCode = "EUR",
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    participantCount = 2,
)

fun group(id: String, participants: List<Participant> = emptyList()) = Group(
    id = id,
    name = id,
    information = null,
    currencySymbol = "€",
    currencyCode = "EUR",
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    participants = participants,
)
