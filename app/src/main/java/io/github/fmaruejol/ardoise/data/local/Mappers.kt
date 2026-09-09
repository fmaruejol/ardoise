package io.github.fmaruejol.ardoise.data.local

import io.github.fmaruejol.ardoise.core.model.Activity
import io.github.fmaruejol.ardoise.core.model.ActivityType
import io.github.fmaruejol.ardoise.core.model.Balance
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpenseSummary
import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.PaidForWithParticipant
import io.github.fmaruejol.ardoise.core.model.Participant
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.Reimbursement
import io.github.fmaruejol.ardoise.core.model.SplitMode
import java.time.Instant
import java.time.LocalDate

/*
 * Domain models to rows and back. Mechanical on purpose: nothing is derived
 * on the way through, because a rounding or a scale applied here would be a
 * wrong balance no settlement test could catch.
 *
 * An enum this build does not know degrades rather than throws, since the
 * cache is filled from a server that may be newer than the app.
 */

// --- groups ----------------------------------------------------------------

internal fun Group.toEntity(participantCount: Int = participants.size) = GroupEntity(
    id = id,
    name = name,
    information = information,
    currencySymbol = currencySymbol,
    currencyCode = currencyCode,
    createdAt = createdAt.toEpochMilli(),
    participantCount = participantCount,
    hasParticipants = true,
)

internal fun Group.participantEntities(): List<ParticipantEntity> =
    participants.mapIndexed { index, participant ->
        ParticipantEntity(
            groupId = id,
            id = participant.id,
            name = participant.name,
            position = index,
        )
    }

internal fun GroupSummary.toEntity(existing: GroupEntity?) = GroupEntity(
    id = id,
    name = name,
    information = information,
    currencySymbol = currencySymbol,
    currencyCode = currencyCode,
    createdAt = createdAt.toEpochMilli(),
    participantCount = participantCount,
    // A row built from the list endpoint must not claim the cached
    // participants are gone.
    hasParticipants = existing?.hasParticipants == true,
)

internal fun GroupEntity.toSummary() = GroupSummary(
    id = id,
    name = name,
    information = information,
    currencySymbol = currencySymbol,
    currencyCode = currencyCode,
    createdAt = Instant.ofEpochMilli(createdAt),
    participantCount = participantCount,
)

internal fun GroupEntity.toGroup(participants: List<ParticipantEntity>) = Group(
    id = id,
    name = name,
    information = information,
    currencySymbol = currencySymbol,
    currencyCode = currencyCode,
    createdAt = Instant.ofEpochMilli(createdAt),
    participants = participants.map { Participant(it.id, it.name) },
)

// --- expenses --------------------------------------------------------------

internal fun ExpenseSummary.toEntity(groupId: String, position: Int) = ExpenseEntity(
    id = id,
    groupId = groupId,
    title = title,
    amount = amount,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    // The list endpoint does not return it; a later `get` fills it in.
    conversionRate = null,
    expenseDate = expenseDate.toString(),
    createdAt = createdAt.toEpochMilli(),
    categoryId = category?.id,
    paidById = paidBy.id,
    splitMode = splitMode.name,
    recurrenceRule = recurrenceRule.name,
    isReimbursement = isReimbursement,
    notes = null,
    documentCount = documentCount,
    position = position,
)

internal fun ExpenseSummary.paidForEntities(): List<PaidForEntity> =
    paidFor.mapIndexed { index, row ->
        PaidForEntity(
            expenseId = id,
            participantId = row.participant.id,
            shares = row.shares,
            position = index,
        )
    }

internal fun ExpenseWithPaidFor.toSummary(
    participants: Map<String, Participant>,
    categories: Map<Int, Category>,
): ExpenseSummary = ExpenseSummary(
    id = expense.id,
    title = expense.title,
    amount = expense.amount,
    originalAmount = expense.originalAmount,
    originalCurrency = expense.originalCurrency,
    expenseDate = LocalDate.parse(expense.expenseDate),
    createdAt = Instant.ofEpochMilli(expense.createdAt),
    category = expense.categoryId?.let { categories[it] },
    // A payer no longer in the cached group still has to be somebody: the feed
    // prints "X paid" on every row.
    paidBy = participants[expense.paidById] ?: Participant(expense.paidById, ""),
    paidFor = paidFor.sortedBy { it.position }.map { row ->
        PaidForWithParticipant(
            participant = participants[row.participantId]
                ?: Participant(row.participantId, ""),
            shares = row.shares,
        )
    },
    splitMode = expense.splitMode.toSplitMode(),
    recurrenceRule = expense.recurrenceRule.toRecurrenceRule(),
    isReimbursement = expense.isReimbursement,
    documentCount = expense.documentCount,
)

internal fun Expense.toEntity(position: Int) = ExpenseEntity(
    id = id,
    groupId = groupId,
    title = title,
    amount = amount,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    conversionRate = conversionRate,
    expenseDate = expenseDate.toString(),
    createdAt = createdAt.toEpochMilli(),
    categoryId = category?.id,
    paidById = paidById,
    splitMode = splitMode.name,
    recurrenceRule = recurrenceRule.name,
    isReimbursement = isReimbursement,
    notes = notes,
    documentCount = documents.size,
    position = position,
)

internal fun Expense.paidForEntities(): List<PaidForEntity> =
    paidFor.mapIndexed { index, row ->
        PaidForEntity(
            expenseId = id,
            participantId = row.participantId,
            shares = row.shares,
            position = index,
        )
    }

internal fun ExpenseWithPaidFor.toExpense(categories: Map<Int, Category>): Expense = Expense(
    id = expense.id,
    groupId = expense.groupId,
    title = expense.title,
    amount = expense.amount,
    originalAmount = expense.originalAmount,
    originalCurrency = expense.originalCurrency,
    conversionRate = expense.conversionRate,
    expenseDate = LocalDate.parse(expense.expenseDate),
    createdAt = Instant.ofEpochMilli(expense.createdAt),
    category = expense.categoryId?.let { categories[it] },
    paidById = expense.paidById,
    paidFor = paidFor.sortedBy { it.position }
        .map { PaidFor(it.participantId, it.shares) },
    splitMode = expense.splitMode.toSplitMode(),
    recurrenceRule = expense.recurrenceRule.toRecurrenceRule(),
    isReimbursement = expense.isReimbursement,
    notes = expense.notes,
    // Never cached: the app shows a count and no document, and an expired URL
    // is worse than none.
    documents = emptyList(),
)

// --- the rest --------------------------------------------------------------

internal fun Category.toEntity() = CategoryEntity(id = id, grouping = grouping, name = name)

internal fun CategoryEntity.toCategory() = Category(id = id, grouping = grouping, name = name)

internal fun GroupBalances.balanceEntities(groupId: String): List<BalanceEntity> =
    balances.map {
        BalanceEntity(
            groupId = groupId,
            participantId = it.participantId,
            paid = it.paid,
            paidFor = it.paidFor,
            total = it.total,
        )
    }

internal fun GroupBalances.reimbursementEntities(groupId: String): List<ReimbursementEntity> =
    reimbursements.mapIndexed { index, it ->
        ReimbursementEntity(
            groupId = groupId,
            position = index,
            fromParticipantId = it.fromParticipantId,
            toParticipantId = it.toParticipantId,
            amount = it.amount,
        )
    }

internal fun toGroupBalances(
    balances: List<BalanceEntity>,
    reimbursements: List<ReimbursementEntity>,
) = GroupBalances(
    balances = balances.map { Balance(it.participantId, it.paid, it.paidFor, it.total) },
    reimbursements = reimbursements.sortedBy { it.position }
        .map { Reimbursement(it.fromParticipantId, it.toParticipantId, it.amount) },
)

internal fun Activity.toEntity(position: Int) = ActivityEntity(
    id = id,
    groupId = groupId,
    time = time.toEpochMilli(),
    activityType = activityType.name,
    participantId = participantId,
    expenseId = expenseId,
    data = data,
    position = position,
)

internal fun ActivityEntity.toActivity() = Activity(
    id = id,
    groupId = groupId,
    time = Instant.ofEpochMilli(time),
    activityType = ActivityType.fromWire(activityType),
    participantId = participantId,
    expenseId = expenseId,
    data = data,
)

/**
 * A cached split mode, through `:core`'s own decoder, which returns null for a
 * mode this build has never heard of rather than guessing, because the feed
 * computes a balance change from it.
 *
 * The fallback is still EVENLY and that is narrow on purpose: every row here
 * was written by this same build, so an unknown mode is unreachable today.
 * What matters is that the decision is in one place and named.
 */
private fun String.toSplitMode(): SplitMode = SplitMode.fromWire(this) ?: SplitMode.EVENLY

/** Unknown rules degrade to NONE by `:core`'s own contract, display only. */
private fun String.toRecurrenceRule(): RecurrenceRule = RecurrenceRule.fromWire(this)
