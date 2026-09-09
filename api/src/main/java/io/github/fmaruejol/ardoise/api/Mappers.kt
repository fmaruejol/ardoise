package io.github.fmaruejol.ardoise.api

import io.github.fmaruejol.ardoise.api.dto.ActivityDto
import io.github.fmaruejol.ardoise.api.dto.CategoryDto
import io.github.fmaruejol.ardoise.api.dto.ExpenseDetailDto
import io.github.fmaruejol.ardoise.api.dto.ExpenseDocumentDto
import io.github.fmaruejol.ardoise.api.dto.ExpenseFormValuesDto
import io.github.fmaruejol.ardoise.api.dto.ExpenseSummaryDto
import io.github.fmaruejol.ardoise.api.dto.GroupDto
import io.github.fmaruejol.ardoise.api.dto.GroupFormValuesDto
import io.github.fmaruejol.ardoise.api.dto.GroupSummaryDto
import io.github.fmaruejol.ardoise.api.dto.ListActivitiesResponse
import io.github.fmaruejol.ardoise.api.dto.ListBalancesResponse
import io.github.fmaruejol.ardoise.api.dto.ListExpensesResponse
import io.github.fmaruejol.ardoise.api.dto.PaidForFormDto
import io.github.fmaruejol.ardoise.api.dto.ParticipantDto
import io.github.fmaruejol.ardoise.api.dto.ParticipantFormDto
import io.github.fmaruejol.ardoise.api.dto.UserBalanceDto
import io.github.fmaruejol.ardoise.api.superjson.SuperJsonDate
import io.github.fmaruejol.ardoise.api.superjson.toUtcLocalDate
import io.github.fmaruejol.ardoise.core.model.Activity
import io.github.fmaruejol.ardoise.core.model.ActivityPage
import io.github.fmaruejol.ardoise.core.model.ActivityType
import io.github.fmaruejol.ardoise.core.model.Balance
import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.model.Expense
import io.github.fmaruejol.ardoise.core.model.ExpenseDocument
import io.github.fmaruejol.ardoise.core.model.ExpensePage
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
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.time.Instant

/**
 * DTO to domain. Anything thrown here becomes
 * [io.github.fmaruejol.ardoise.core.result.SpliitError.Malformed], so an unrecognised value
 * fails loudly rather than being guessed at.
 */

internal fun ParticipantDto.toDomain() = Participant(id = id, name = name)

internal fun CategoryDto.toDomain() = Category(id = id, grouping = grouping, name = name)

internal fun GroupDto.toDomain() = Group(
    id = id,
    name = name,
    information = information,
    currencySymbol = currency,
    currencyCode = currencyCode,
    createdAt = createdAt,
    participants = participants.map { it.toDomain() },
)

internal fun GroupSummaryDto.toDomain() = GroupSummary(
    id = id,
    name = name,
    information = information,
    currencySymbol = currency,
    currencyCode = currencyCode,
    createdAt = Instant.parse(createdAt),
    participantCount = count.participants,
)

/** An unknown split mode is fatal: falling back to `EVENLY` would produce wrong balances. */
private fun splitModeOf(wire: String): SplitMode =
    requireNotNull(SplitMode.fromWire(wire)) { "Unknown split mode: $wire" }

internal fun ExpenseSummaryDto.toDomain() = ExpenseSummary(
    id = id,
    title = title,
    amount = amount,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    expenseDate = expenseDate.toUtcLocalDate(),
    createdAt = createdAt,
    category = category?.toDomain(),
    paidBy = paidBy.toDomain(),
    paidFor = paidFor.map {
        PaidForWithParticipant(participant = it.participant.toDomain(), shares = it.shares)
    },
    splitMode = splitModeOf(splitMode),
    recurrenceRule = RecurrenceRule.fromWire(recurrenceRule),
    isReimbursement = isReimbursement,
    documentCount = count.documents,
)

internal fun ExpenseDetailDto.toDomain() = Expense(
    id = id,
    groupId = groupId,
    title = title,
    amount = amount,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    conversionRate = conversionRate,
    expenseDate = expenseDate.toUtcLocalDate(),
    createdAt = createdAt,
    category = category?.toDomain(),
    paidById = paidById,
    paidFor = paidFor.map { PaidFor(participantId = it.participantId, shares = it.shares) },
    splitMode = splitModeOf(splitMode),
    recurrenceRule = RecurrenceRule.fromWire(recurrenceRule),
    isReimbursement = isReimbursement,
    notes = notes,
    documents = documents.map { ExpenseDocument(it.id, it.url, it.width, it.height) },
)

internal fun ListExpensesResponse.toDomain() = ExpensePage(
    expenses = expenses.map { it.toDomain() },
    hasMore = hasMore,
)

internal fun ListBalancesResponse.toDomain() = GroupBalances(
    balances = balances.map { (participantId, entry) ->
        Balance(
            participantId = participantId,
            paid = entry.paid,
            paidFor = entry.paidFor,
            total = entry.total,
        )
    },
    reimbursements = reimbursements.map {
        Reimbursement(fromParticipantId = it.from, toParticipantId = it.to, amount = it.amount)
    },
)

internal fun UserBalanceDto.toDomain() = UserGroupBalance(
    groupId = groupId,
    groupName = groupName,
    currencySymbol = currency,
    currencyCode = currencyCode,
    participantId = participantId,
    participantName = participantName,
    amount = amount,
)

internal fun ActivityDto.toDomain() = Activity(
    id = id,
    groupId = groupId,
    time = time,
    activityType = ActivityType.fromWire(activityType),
    participantId = participantId,
    expenseId = expenseId,
    data = data,
)

internal fun ListActivitiesResponse.toDomain() = ActivityPage(
    activities = activities.map { it.toDomain() },
    hasMore = hasMore,
)

// --- domain to DTO ---------------------------------------------------------

internal fun GroupInput.toDto() = GroupFormValuesDto(
    name = name,
    information = information,
    currency = currencySymbol,
    currencyCode = currencyCode,
    participants = participants.map { ParticipantFormDto(id = it.id, name = it.name) },
)

@OptIn(ExperimentalSerializationApi::class)
internal fun ExpenseInput.toDto() = ExpenseFormValuesDto(
    expenseDate = SuperJsonDate.ofDate(expenseDate),
    title = title,
    category = categoryId,
    amount = amount,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    // A bare JSON number, so the exact decimal text survives.
    conversionRate = conversionRate?.let { JsonUnquotedLiteral(it) },
    paidBy = paidById,
    paidFor = paidFor.map { PaidForFormDto(participant = it.participantId, shares = it.shares) },
    splitMode = splitMode.name,
    saveDefaultSplittingOptions = saveDefaultSplittingOptions,
    isReimbursement = isReimbursement,
    documents = documents.map { ExpenseDocumentDto(it.id, it.url, it.width, it.height) },
    notes = notes,
    recurrenceRule = recurrenceRule.name,
)
