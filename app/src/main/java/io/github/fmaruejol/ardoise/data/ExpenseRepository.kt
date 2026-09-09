package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpensePage
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** What became of a save: it went, or it is waiting for a network. */
sealed interface Created {
    data class Sent(val expenseId: String) : Created

    /** The local id of the queue entry, not an expense id. */
    data class Queued(val pendingId: String) : Created
}

/**
 * A group's expenses. Every mutation attributes itself to the participant the
 * user says they are, which is what fills the activity log.
 */
class ExpenseRepository(
    private val api: SpliitApi,
    private val preferences: GroupPreferences,
    private val cache: SpliitCache,
    private val outbox: ExpenseOutbox,
) {
    /**
     * The first page of expenses, newest first, cache then server.
     *
     * **A text filter is not cached**: a search is a question about the whole
     * group that only the server can answer, and storing its answer would
     * overwrite the feed with a subset of itself.
     */
    fun expenses(
        groupId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        filter: String? = null,
    ): Flow<SpliitResult<ExpensePage>> {
        if (filter != null) {
            return flow {
                emit(api.listExpenses(groupId = groupId, cursor = 0, limit = limit, filter = filter))
            }
        }
        return cache.cachedThenFresh(
            local = cache.expenses(groupId, limit).map { expenses ->
                // hasMore is the server's and is not cached; a full page is
                // the only thing the cache can say about it.
                ExpensePage(expenses, hasMore = expenses.size >= limit)
            },
            isCached = { it.expenses.isNotEmpty() },
            refresh = { cache.refreshExpenses(groupId, limit) },
        )
    }

    fun expense(groupId: String, expenseId: String): Flow<SpliitResult<Expense>> =
        cache.cachedThenFresh(
            local = cache.expense(expenseId),
            isCached = { it != null },
            refresh = { cache.refreshExpense(groupId, expenseId) },
        ).mapNotNull { result ->
            when (result) {
                is SpliitResult.Failure -> result

                // The feed's fields alone would be a half-built expense, so
                // nothing is emitted until the refresh lands.
                is SpliitResult.Success -> result.value?.let { SpliitResult.Success(it) }
            }
        }

    /**
     * Creates an expense, or queues it if there is no network.
     *
     * **Only a network failure is queued**: nothing reached the server, so the
     * expense is merely waiting. A `BAD_REQUEST` is the server refusing this
     * expense, and queueing that would promise to send something it will never
     * accept. Nothing retries by itself. See [ExpenseOutbox].
     */
    suspend fun create(groupId: String, expense: ExpenseInput): SpliitResult<Created> {
        val result = api.createExpense(
            groupId = groupId,
            expense = expense,
            participantId = activeParticipant(groupId),
        )
        return when (result) {
            is SpliitResult.Success -> {
                cache.refreshAfterExpenseChange(groupId, DEFAULT_PAGE_SIZE)
                SpliitResult.Success(Created.Sent(result.value))
            }

            is SpliitResult.Failure -> {
                if (result.error is SpliitError.Network) {
                    SpliitResult.Success(Created.Queued(outbox.enqueue(groupId, expense)))
                } else {
                    result
                }
            }
        }
    }

    /**
     * Replaces a queued expense with an edited one: the old entry goes and the
     * edit takes the ordinary create path, so it cannot leave the original
     * behind.
     */
    suspend fun replaceQueued(
        pendingId: String,
        groupId: String,
        expense: ExpenseInput,
    ): SpliitResult<Created> {
        outbox.discard(pendingId)
        return create(groupId, expense)
    }

    /** Expenses typed offline in this group, oldest first. */
    fun pending(groupId: String): Flow<List<PendingExpense>> = outbox.pending(groupId)

    suspend fun pendingExpense(id: String): PendingExpense? = outbox.find(id)

    suspend fun discardQueued(id: String) = outbox.discard(id)

    /** Sends what is waiting. Returns how many went. */
    suspend fun sendQueued(): Int = outbox.flush()

    suspend fun update(
        groupId: String,
        expenseId: String,
        expense: ExpenseInput,
    ): SpliitResult<String> {
        val documents = when (val current = api.getExpense(groupId, expenseId)) {
            is SpliitResult.Success -> current.value.documents
            is SpliitResult.Failure -> return current
        }
        val result = api.updateExpense(
            groupId = groupId,
            expenseId = expenseId,
            expense = expense.copy(documents = documents),
            participantId = activeParticipant(groupId),
        )
        if (result is SpliitResult.Success) {
            // This one in full as well as the feed: the list carries no notes
            // and no rate, and the detail screen reads them.
            cache.refreshExpense(groupId, expenseId)
            cache.refreshAfterExpenseChange(groupId, DEFAULT_PAGE_SIZE)
        }
        return result
    }

    suspend fun delete(groupId: String, expenseId: String): SpliitResult<Unit> {
        val result = api.deleteExpense(
            groupId = groupId,
            expenseId = expenseId,
            participantId = activeParticipant(groupId),
        )
        if (result is SpliitResult.Success) {
            cache.refreshAfterExpenseChange(groupId, DEFAULT_PAGE_SIZE)
        }
        return result
    }

    private suspend fun activeParticipant(groupId: String): String? =
        preferences.activeParticipantId(groupId).first()

    companion object {
        /** Matches the server's own default for `groups.expenses.list`. */
        const val DEFAULT_PAGE_SIZE: Int = 10
    }
}
