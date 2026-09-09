package io.github.fmaruejol.ardoise.api.dto

import io.github.fmaruejol.ardoise.api.superjson.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire shapes of the tRPC responses. They mirror the Prisma `select`s in
 * upstream's `src/lib/api.ts` field for field: anything not listed there is
 * not in the response. Internal, callers get :core's models.
 */

@Serializable
internal data class ParticipantDto(
    val id: String,
    val name: String,
)

@Serializable
internal data class CategoryDto(
    val id: Int,
    val grouping: String,
    val name: String,
)

// --- groups.get / groups.getDetails ---------------------------------------

@Serializable
internal data class GroupDto(
    val id: String,
    val name: String,
    val information: String? = null,
    /** Display symbol, not an ISO code. */
    val currency: String,
    val currencyCode: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val participants: List<ParticipantDto> = emptyList(),
)

@Serializable
internal data class GetGroupResponse(val group: GroupDto? = null)

@Serializable
internal data class GetGroupDetailsResponse(
    val group: GroupDto,
    /** Participant ids that appear on at least one expense. */
    val participantsWithExpenses: List<String> = emptyList(),
)

// --- groups.list -----------------------------------------------------------

@Serializable
internal data class GroupCountDto(val participants: Int = 0)

/**
 * `groups.list` is the one response whose `createdAt` is **not** a superjson
 * `Date`: the server stringifies it before returning.
 */
@Serializable
internal data class GroupSummaryDto(
    val id: String,
    val name: String,
    val information: String? = null,
    val currency: String,
    val currencyCode: String? = null,
    val createdAt: String,
    @SerialName("_count") val count: GroupCountDto = GroupCountDto(),
)

@Serializable
internal data class ListGroupsResponse(val groups: List<GroupSummaryDto> = emptyList())

@Serializable
internal data class CreateGroupResponse(val groupId: String)

// --- groups.expenses.list --------------------------------------------------

@Serializable
internal data class PaidForWithParticipantDto(
    val participant: ParticipantDto,
    val shares: Long,
)

@Serializable
internal data class ExpenseDocumentCountDto(val documents: Int = 0)

@Serializable
internal data class ExpenseSummaryDto(
    val id: String,
    val title: String,
    val amount: Long,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    @Serializable(with = InstantSerializer::class) val expenseDate: Instant,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val category: CategoryDto? = null,
    val paidBy: ParticipantDto,
    val paidFor: List<PaidForWithParticipantDto> = emptyList(),
    val splitMode: String,
    val recurrenceRule: String? = null,
    val isReimbursement: Boolean = false,
    @SerialName("_count") val count: ExpenseDocumentCountDto = ExpenseDocumentCountDto(),
)

@Serializable
internal data class ListExpensesResponse(
    val expenses: List<ExpenseSummaryDto> = emptyList(),
    val hasMore: Boolean = false,
)

// --- groups.expenses.get ---------------------------------------------------

/** `groups.expenses.get` returns the raw join rows, so the participant is a bare id. */
@Serializable
internal data class PaidForRowDto(
    val participantId: String,
    val shares: Long,
)

@Serializable
internal data class ExpenseDocumentDto(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int,
)

@Serializable
internal data class ExpenseDetailDto(
    val id: String,
    val groupId: String,
    val title: String,
    val amount: Long,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    /** A `Decimal` server-side, sent as a string. Kept as text, never a Double. */
    val conversionRate: String? = null,
    @Serializable(with = InstantSerializer::class) val expenseDate: Instant,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val category: CategoryDto? = null,
    val paidById: String,
    val paidFor: List<PaidForRowDto> = emptyList(),
    val splitMode: String,
    val recurrenceRule: String? = null,
    val isReimbursement: Boolean = false,
    val notes: String? = null,
    val documents: List<ExpenseDocumentDto> = emptyList(),
)

@Serializable
internal data class GetExpenseResponse(val expense: ExpenseDetailDto)

@Serializable
internal data class MutateExpenseResponse(val expenseId: String)

// --- groups.balances -------------------------------------------------------

@Serializable
internal data class BalanceEntryDto(
    val paid: Long = 0,
    val paidFor: Long = 0,
    val total: Long = 0,
)

@Serializable
internal data class ReimbursementDto(
    val from: String,
    val to: String,
    val amount: Long,
)

@Serializable
internal data class ListBalancesResponse(
    val balances: Map<String, BalanceEntryDto> = emptyMap(),
    val reimbursements: List<ReimbursementDto> = emptyList(),
)

@Serializable
internal data class UserBalanceDto(
    val groupId: String,
    val groupName: String,
    val currency: String,
    val currencyCode: String? = null,
    val participantId: String,
    val participantName: String,
    val amount: Long,
)

@Serializable
internal data class ForUserBalancesResponse(val balances: List<UserBalanceDto> = emptyList())

// --- groups.activities.list ------------------------------------------------

@Serializable
internal data class ActivityDto(
    val id: String,
    val groupId: String,
    @Serializable(with = InstantSerializer::class) val time: Instant,
    val activityType: String,
    val participantId: String? = null,
    val expenseId: String? = null,
    val data: String? = null,
)

@Serializable
internal data class ListActivitiesResponse(
    val activities: List<ActivityDto> = emptyList(),
    val hasMore: Boolean = false,
)

// --- categories.list -------------------------------------------------------

@Serializable
internal data class ListCategoriesResponse(val categories: List<CategoryDto> = emptyList())
