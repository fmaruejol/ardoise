package io.github.fmaruejol.ardoise.data.local

import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.RefreshScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The cache, and the one place that knows what a change makes stale.
 *
 * Room's DAO `Flow`s already wake every query over a table that was written
 * to; what is left here is the part Room cannot know, that changing an
 * *expense* invalidates the *balances* and the *activity log* too, because the
 * server recomputes both.
 *
 * Every `refresh` returns the error or null. Nothing throws across this
 * boundary, and a failed refresh leaves the cache as it was.
 */
class SpliitCache(
    private val api: SpliitApi,
    private val database: SpliitDatabase,
) {
    // --- reading -----------------------------------------------------------

    /**
     * Ticks whenever any group's balances change, starting with one tick for
     * the state they are in.
     *
     * `groups.balances.forUser` is the one read with no rows of its own. It
     * spans every group, so it had nothing to wake it, and adding an expense
     * left the group list's "You owe" as it was. The balances table is the
     * right signal: `refreshAfterExpenseChange` rewrites it every time.
     */
    fun balanceChanges(): Flow<Unit> =
        database.invalidationTracker
            .createFlow("balances", emitInitialState = true)
            // For the case where several land while a request is still out.
            .conflate()
            .map { }

    fun groups(ids: List<String>): Flow<List<io.github.fmaruejol.ardoise.core.model.GroupSummary>> =
        database.groups().observeAll(ids).map { rows ->
            // The ids carry the user's order; `IN (:ids)` does not preserve it.
            val byId = rows.associateBy { it.id }
            ids.mapNotNull { byId[it]?.toSummary() }
        }

    fun group(groupId: String): Flow<io.github.fmaruejol.ardoise.core.model.Group?> =
        combine(
            database.groups().observe(groupId),
            database.groups().observeParticipants(groupId),
        ) { group, participants ->
            // A group known only from the list endpoint has no participants
            // yet, which is not the same as having none.
            group?.takeIf { it.hasParticipants }?.toGroup(participants)
        }

    fun expenses(groupId: String, limit: Int): Flow<List<io.github.fmaruejol.ardoise.core.model.ExpenseSummary>> =
        combine(
            database.expenses().observePage(groupId, limit),
            database.groups().observeParticipants(groupId),
            database.categories().observe(),
        ) { expenses, participants, categories ->
            val byId = participants.associate { it.id to Participant(it.id, it.name) }
            val byCategory = categories.associate { it.id to it.toCategory() }
            expenses.map { it.toSummary(byId, byCategory) }
        }

    fun expense(expenseId: String): Flow<io.github.fmaruejol.ardoise.core.model.Expense?> =
        combine(
            database.expenses().observe(expenseId),
            database.categories().observe(),
        ) { expense, categories ->
            expense?.toExpense(categories.associate { it.id to it.toCategory() })
        }

    fun balances(groupId: String): Flow<io.github.fmaruejol.ardoise.core.model.GroupBalances> =
        combine(
            database.balances().observeBalances(groupId),
            database.balances().observeReimbursements(groupId),
        ) { balances, reimbursements -> toGroupBalances(balances, reimbursements) }

    fun activities(groupId: String, limit: Int): Flow<List<io.github.fmaruejol.ardoise.core.model.Activity>> =
        database.activities().observe(groupId, limit).map { rows -> rows.map { it.toActivity() } }

    fun categories(): Flow<List<Category>> =
        database.categories().observe().map { rows -> rows.map { it.toCategory() } }

    // --- refreshing --------------------------------------------------------

    suspend fun refreshGroups(ids: List<String>): SpliitError? {
        if (ids.isEmpty()) return null
        return when (val result = api.listGroups(ids)) {
            is SpliitResult.Failure -> {
                result.error
            }

            is SpliitResult.Success -> {
                val existing = database.groups().observeAll(ids).first().associateBy { it.id }
                database.groups().upsert(result.value.map { it.toEntity(existing[it.id]) })
                // A group the server no longer has is gone, not merely stale.
                val returned = result.value.map { it.id }.toSet()
                ids.filterNot { it in returned }.forEach { database.groups().delete(it) }
                null
            }
        }
    }

    suspend fun refreshGroup(groupId: String): SpliitError? =
        when (val result = api.getGroup(groupId)) {
            is SpliitResult.Failure -> {
                result.error
            }

            is SpliitResult.Success -> {
                val group = result.value
                if (group == null) {
                    database.groups().delete(groupId)
                } else {
                    database.groups().replace(group.toEntity(), group.participantEntities())
                }
                null
            }
        }

    suspend fun refreshExpenses(groupId: String, limit: Int): SpliitError? =
        when (val result = api.listExpenses(groupId = groupId, cursor = 0, limit = limit)) {
            is SpliitResult.Failure -> {
                result.error
            }

            is SpliitResult.Success -> {
                val page = result.value
                // The categories the expenses came with: otherwise a cached
                // feed loses every icon until something fetches the list.
                database.categories().upsert(
                    page.expenses.mapNotNull { it.category }.distinctBy { it.id }
                        .map { it.toEntity() },
                )
                database.expenses().replacePage(
                    groupId = groupId,
                    expenses = page.expenses.mapIndexed { index, it -> it.toEntity(groupId, index) },
                    paidFor = page.expenses.flatMap { it.paidForEntities() },
                    // Nothing beyond the page, so anything missing from it
                    // has been deleted upstream.
                    complete = !page.hasMore,
                )
                null
            }
        }

    suspend fun refreshExpense(groupId: String, expenseId: String): SpliitError? =
        when (val result = api.getExpense(groupId, expenseId)) {
            is SpliitResult.Failure -> {
                result.error
            }

            is SpliitResult.Success -> {
                val expense = result.value
                expense.category?.let { database.categories().upsert(listOf(it.toEntity())) }
                // Keep its place in the feed: this is one expense being
                // filled in, not the feed being re-ordered.
                val position = database.expenses().observe(expenseId).first()
                    ?.expense?.position ?: 0
                database.expenses().upsert(listOf(expense.toEntity(position)))
                database.expenses().upsertPaidFor(expense.paidForEntities())
                null
            }
        }

    suspend fun refreshBalances(groupId: String): SpliitError? =
        when (val result = api.listBalances(groupId)) {
            is SpliitResult.Failure -> {
                result.error
            }

            is SpliitResult.Success -> {
                database.balances().replace(
                    groupId = groupId,
                    balances = result.value.balanceEntities(groupId),
                    reimbursements = result.value.reimbursementEntities(groupId),
                )
                null
            }
        }

    suspend fun refreshActivities(groupId: String, limit: Int): SpliitError? =
        when (val result = api.listActivities(groupId = groupId, cursor = 0, limit = limit)) {
            is SpliitResult.Failure -> {
                result.error
            }

            is SpliitResult.Success -> {
                database.activities().replace(
                    groupId = groupId,
                    activities = result.value.activities
                        .mapIndexed { index, it -> it.toEntity(index) },
                )
                null
            }
        }

    suspend fun refreshCategories(): SpliitError? =
        when (val result = api.listCategories()) {
            is SpliitResult.Failure -> {
                result.error
            }

            is SpliitResult.Success -> {
                database.categories().upsert(result.value.map { it.toEntity() })
                null
            }
        }

    /**
     * What one expense changing makes stale: the server rewrites the balances
     * and appends to the activity log, so all three are fetched again.
     *
     * **The page fetched is at least as wide as the one cached.** A refresh
     * replaces the window its page describes, so a narrower page cannot delete
     * a row beyond it, and a deletion is exactly when a row has to go.
     */
    suspend fun refreshAfterExpenseChange(groupId: String, expenseLimit: Int) {
        val cached = database.expenses().count(groupId)
        refreshExpenses(groupId, maxOf(expenseLimit, cached))
        refreshBalances(groupId)
        refreshActivities(groupId, ACTIVITY_LIMIT)
    }

    /** Everything cached about a group, gone in one transaction. */
    suspend fun forget(groupId: String) = database.cache().forget(groupId)

    /**
     * The cache first, then whatever the network had to say.
     *
     * Every read in the app is this shape: what is known goes out immediately,
     * a refresh follows, and a failure arrives **after** the cached value, so
     * a screen shows its rows under an "Offline" banner rather than an empty
     * state.
     *
     * **An empty cache is not an answer.** "Nothing stored" and "the server
     * says there is nothing" look identical, so until a refresh has run an
     * empty cache emits nothing; [isCached] is what empty means for the type.
     * Written as one sequence because the order of emissions is the contract.
     */
    fun <T> cachedThenFresh(
        local: Flow<T>,
        isCached: (T) -> Boolean = { true },
        refresh: suspend () -> SpliitError?,
    ): Flow<SpliitResult<T>> = flow {
        // Before the cached value goes out: it arrives at once, and a screen
        // seeing nothing in flight beside it would read the refresh as over.
        val scope = currentCoroutineContext()[RefreshScope]
        scope?.begin()

        val existing = local.first()
        val hadCache = isCached(existing)
        if (hadCache) emit(SpliitResult.Success(existing))

        val error = refresh()
        if (error != null) emit(SpliitResult.Failure(error))
        // Not once the rows land: a failure writes nothing, so `tail` may
        // never speak again.
        scope?.finished()

        val tail = when {
            // The refresh wrote nothing, so the cache still holds what went out.
            hadCache && error != null -> local.drop(1)

            // Nothing cached and nothing fetched: still not an answer.
            error != null -> local.filter(isCached)

            else -> local
        }
        // From here the cache is the answer, including when it is empty.
        emitAll(tail.map { SpliitResult.Success(it) })
    }.distinctUntilChanged()

    companion object {
        /** Matches `ActivityRepository`'s own page, which is a screenful. */
        const val ACTIVITY_LIMIT: Int = 30
    }
}
