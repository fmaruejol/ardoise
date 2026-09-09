package io.github.fmaruejol.ardoise.api.dto

import io.github.fmaruejol.ardoise.api.superjson.SuperJsonDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Inputs, matching the zod schemas in `src/lib/schemas.ts` and the procedures. */

@Serializable
internal data class GroupIdInput(val groupId: String)

@Serializable
internal data class GroupIdsInput(val groupIds: List<String>)

@Serializable
internal data class ExpenseIdInput(val groupId: String, val expenseId: String)

@Serializable
internal data class ListExpensesInput(
    val groupId: String,
    val cursor: Int? = null,
    val limit: Int? = null,
    val filter: String? = null,
)

@Serializable
internal data class ListActivitiesInput(
    val groupId: String,
    val cursor: Int = 0,
    val limit: Int = 5,
)

@Serializable
internal data class UserGroupRefInput(val groupId: String, val participantId: String)

@Serializable
internal data class ForUserBalancesInput(val groups: List<UserGroupRefInput>)

@Serializable
internal data class ParticipantFormDto(
    val id: String? = null,
    val name: String,
)

@Serializable
internal data class GroupFormValuesDto(
    val name: String,
    val information: String? = null,
    /** Display symbol; the schema allows 1 to 5 characters. */
    val currency: String,
    /** ISO 4217, exactly 3 characters, or null. */
    val currencyCode: String? = null,
    val participants: List<ParticipantFormDto>,
)

@Serializable
internal data class CreateGroupInput(val groupFormValues: GroupFormValuesDto)

@Serializable
internal data class UpdateGroupInput(
    val groupId: String,
    val groupFormValues: GroupFormValuesDto,
    val participantId: String? = null,
)

@Serializable
internal data class PaidForFormDto(
    val participant: String,
    /**
     * Already in the stored representation for the split mode: the server only
     * rescales shares that arrive as strings, and these go out as numbers.
     */
    val shares: Long,
)

@Serializable
internal data class ExpenseFormValuesDto(
    val expenseDate: SuperJsonDate,
    val title: String,
    val category: Int,
    val amount: Long,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    /** A JSON number carrying the exact decimal text, never a rounded Double. */
    val conversionRate: JsonElement? = null,
    val paidBy: String,
    val paidFor: List<PaidForFormDto>,
    val splitMode: String,
    val saveDefaultSplittingOptions: Boolean,
    val isReimbursement: Boolean,
    val documents: List<ExpenseDocumentDto> = emptyList(),
    val notes: String? = null,
    val recurrenceRule: String,
)

@Serializable
internal data class CreateExpenseInput(
    val groupId: String,
    val expenseFormValues: ExpenseFormValuesDto,
    val participantId: String? = null,
)

@Serializable
internal data class UpdateExpenseInput(
    val groupId: String,
    val expenseId: String,
    val expenseFormValues: ExpenseFormValuesDto,
    val participantId: String? = null,
)

@Serializable
internal data class DeleteExpenseInput(
    val groupId: String,
    val expenseId: String,
    val participantId: String? = null,
)
