package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.api.ExpenseInput
import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import io.github.fmaruejol.ardoise.data.local.outbox.OutboxDao
import io.github.fmaruejol.ardoise.data.local.outbox.PendingExpenseEntity
import io.github.fmaruejol.ardoise.data.local.outbox.PendingExpenseWithPaidFor
import io.github.fmaruejol.ardoise.data.local.outbox.PendingPaidForEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One expense typed offline, as a screen needs to read it. */
data class PendingExpense(
    val id: String,
    val groupId: String,
    val input: ExpenseInput,
    val queuedAt: Instant,
    /** Zero until it has been sent at least once. */
    val attempts: Int,
    /** A short note on why the last attempt failed, for a card to show. */
    val lastError: String?,
)

/**
 * Expenses typed offline, and the sending of them.
 *
 * The queue is **visible and the user drives it**: a waiting expense shows on
 * the feed, can be edited and can be thrown away. That is what makes retrying
 * safe. `groups.expenses.create` has no idempotency key, so a send whose
 * answer was lost cannot be told from one that never arrived, and nothing
 * retries silently where a duplicate would go unnoticed. [alreadySent]
 * narrows that window; a client-supplied expense id would remove the need.
 */
class ExpenseOutbox(
    private val api: SpliitApi,
    private val dao: OutboxDao,
    private val cache: SpliitCache,
    private val preferences: GroupPreferences,
) {
    /** One send at a time, so a flush from two places cannot double up. */
    private val sending = Mutex()

    fun pending(groupId: String): Flow<List<PendingExpense>> =
        dao.observe(groupId).map { rows -> rows.map { it.toPending() } }

    suspend fun find(id: String): PendingExpense? = dao.find(id)?.toPending()

    /**
     * Keeps an expense that could not be sent. Only ever for a network
     * failure: a `BAD_REQUEST` is the server refusing the expense itself.
     */
    suspend fun enqueue(groupId: String, input: ExpenseInput): String {
        val id = UUID.randomUUID().toString()
        dao.enqueue(input.toEntity(id, groupId), input.paidForEntities(id))
        return id
    }

    suspend fun discard(id: String) = dao.remove(id)

    suspend fun discardAllFor(groupId: String) = dao.removeAllFor(groupId)

    /**
     * Sends everything waiting, oldest first, and returns how many went.
     *
     * Stops at the first network failure. If one send could not reach the
     * server the next will not either. A *rejected* expense does not stop the
     * ones behind it, which are unrelated.
     */
    suspend fun flush(): Int = sending.withLock {
        var sent = 0
        for (queued in dao.all()) {
            when (val result = send(queued.toPending())) {
                SendOutcome.Sent -> sent++
                SendOutcome.Rejected -> Unit
                SendOutcome.Unreachable -> return@withLock sent
            }
        }
        sent
    }

    private enum class SendOutcome { Sent, Rejected, Unreachable }

    private suspend fun send(queued: PendingExpense): SendOutcome {
        // Only for an expense that has actually been tried, so two identical
        // ones typed offline are never confused before either goes out.
        if (queued.attempts > 0 && alreadySent(queued)) {
            dao.remove(queued.id)
            return SendOutcome.Sent
        }

        val result = api.createExpense(
            groupId = queued.groupId,
            expense = queued.input,
            participantId = preferences.activeParticipantId(queued.groupId).first(),
        )
        return when (result) {
            is SpliitResult.Success -> {
                dao.remove(queued.id)
                cache.refreshAfterExpenseChange(queued.groupId, ExpenseRepository.DEFAULT_PAGE_SIZE)
                SendOutcome.Sent
            }

            is SpliitResult.Failure -> {
                dao.recordAttempt(
                    id = queued.id,
                    attempts = queued.attempts + 1,
                    error = result.error.shortCode(),
                )
                if (result.error is SpliitError.Network) {
                    SendOutcome.Unreachable
                } else {
                    SendOutcome.Rejected
                }
            }
        }
    }

    /**
     * Whether a previous attempt landed after all: the request arrived, the
     * expense was created, and the answer was lost. Looks for an expense on
     * the server with the same title, amount, date and payer, created since
     * this one was queued.
     *
     * It can be wrong in one direction, where two genuinely identical
     * expenses would look like one, and that failure is a missing expense the user can
     * see and re-add, rather than a duplicate skewing a balance nobody
     * rechecks.
     */
    private suspend fun alreadySent(queued: PendingExpense): Boolean {
        val page = api.listExpenses(
            groupId = queued.groupId,
            cursor = 0,
            limit = ExpenseRepository.DEFAULT_PAGE_SIZE,
        )
        val expenses = (page as? SpliitResult.Success)?.value?.expenses ?: return false
        return expenses.any {
            it.title == queued.input.title &&
                it.amount == queued.input.amount &&
                it.expenseDate == queued.input.expenseDate &&
                it.paidBy.id == queued.input.paidById &&
                !it.createdAt.isBefore(queued.queuedAt)
        }
    }
}

private fun PendingExpenseWithPaidFor.toPending() = PendingExpense(
    id = expense.id,
    groupId = expense.groupId,
    input = ExpenseInput(
        title = expense.title,
        amount = expense.amount,
        expenseDate = LocalDate.parse(expense.expenseDate),
        paidById = expense.paidById,
        paidFor = paidFor.sortedBy { it.position }
            .map { PaidFor(it.participantId, it.shares) },
        // `:core`'s decoder, which returns null for a mode this build does not
        // know rather than guessing. A wrong mode here is *sent*, and becomes
        // a wrong expense for the whole group.
        splitMode = SplitMode.fromWire(expense.splitMode) ?: SplitMode.EVENLY,
        categoryId = expense.categoryId,
        isReimbursement = expense.isReimbursement,
        notes = expense.notes,
        recurrenceRule = RecurrenceRule.fromWire(expense.recurrenceRule),
        originalAmount = expense.originalAmount,
        originalCurrency = expense.originalCurrency,
        conversionRate = expense.conversionRate,
    ),
    queuedAt = Instant.ofEpochMilli(expense.queuedAt),
    attempts = expense.attempts,
    lastError = expense.lastError,
)

private fun ExpenseInput.toEntity(id: String, groupId: String) = PendingExpenseEntity(
    id = id,
    groupId = groupId,
    title = title,
    amount = amount,
    expenseDate = expenseDate.toString(),
    paidById = paidById,
    splitMode = splitMode.name,
    categoryId = categoryId,
    isReimbursement = isReimbursement,
    notes = notes,
    recurrenceRule = recurrenceRule.name,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    conversionRate = conversionRate,
    queuedAt = Instant.now().toEpochMilli(),
    attempts = 0,
    lastError = null,
)

private fun ExpenseInput.paidForEntities(id: String) =
    paidFor.mapIndexed { index, row ->
        PendingPaidForEntity(
            pendingId = id,
            participantId = row.participantId,
            shares = row.shares,
            position = index,
        )
    }

/**
 * A short note for the card and for whoever reads a bug report. Not the
 * user-facing wording, which is `ui/ErrorMessages.kt`.
 */
private fun SpliitError.shortCode(): String = when (this) {
    is SpliitError.Network -> "network"
    is SpliitError.Http -> "http $status"
    SpliitError.NotFound -> "not found"
    is SpliitError.Procedure -> code
    is SpliitError.Malformed -> "malformed"
}
