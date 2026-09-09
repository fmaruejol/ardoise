package io.github.fmaruejol.ardoise.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * The offline cache's tables. Everything here came from the server and is
 * replaced by the next successful fetch, no dirty flag, no pending column, no
 * conflict to resolve. Amounts stay `Long` minor units; dates are stored the
 * way they sort.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val information: String?,
    val currencySymbol: String,
    val currencyCode: String?,
    val createdAt: Long,
    /** From the list endpoint, so a card can say "4 people" before the group is fetched. */
    val participantCount: Int,
    /**
     * False for a row built from `groups.list`, which carries no participants.
     * Without it an unfetched group would look like a group with nobody in it.
     */
    val hasParticipants: Boolean,
)

@Entity(
    tableName = "participants",
    primaryKeys = ["groupId", "id"],
    indices = [Index("groupId")],
)
data class ParticipantEntity(
    val groupId: String,
    val id: String,
    val name: String,
    /** The group's own order, which `groups.get` returns and nothing sorts. */
    val position: Int,
)

@Entity(tableName = "expenses", indices = [Index("groupId")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val amount: Long,
    val originalAmount: Long?,
    val originalCurrency: String?,
    /** Exact decimal text. Never a Double, here as everywhere else. */
    val conversionRate: String?,
    /** ISO `yyyy-MM-dd`, which sorts as text in the order it sorts as a date. */
    val expenseDate: String,
    val createdAt: Long,
    val categoryId: Int?,
    val paidById: String,
    val splitMode: String,
    val recurrenceRule: String,
    val isReimbursement: Boolean,
    val notes: String?,
    val documentCount: Int,
    /**
     * Where the server put it in the feed. The feed is "newest first" by a
     * rule the server owns, so keeping its order beats re-deriving one.
     */
    val position: Int,
)

@Entity(
    tableName = "expense_paid_for",
    primaryKeys = ["expenseId", "participantId"],
    indices = [Index("expenseId")],
)
data class PaidForEntity(
    val expenseId: String,
    val participantId: String,
    /**
     * **Not an amount.** What it means depends on the expense's split mode.
     * See `PaidFor` in `:core`. Stored exactly as the server sent it.
     */
    val shares: Long,
    val position: Int,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Int,
    val grouping: String,
    val name: String,
)

@Entity(tableName = "balances", primaryKeys = ["groupId", "participantId"])
data class BalanceEntity(
    val groupId: String,
    val participantId: String,
    val paid: Long,
    val paidFor: Long,
    val total: Long,
)

@Entity(tableName = "reimbursements", primaryKeys = ["groupId", "position"])
data class ReimbursementEntity(
    val groupId: String,
    /** The suggestions are a chain, so their order is part of the answer. */
    val position: Int,
    val fromParticipantId: String,
    val toParticipantId: String,
    val amount: Long,
)

@Entity(tableName = "activities", indices = [Index("groupId")])
data class ActivityEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val time: Long,
    val activityType: String,
    val participantId: String?,
    val expenseId: String?,
    val data: String?,
    val position: Int,
)

/**
 * The columns on an expense that only `groups.expenses.get` fills in.
 *
 * Read before a feed refresh replaces the rows, so that a page of summaries
 * does not blank them. See `ExpenseDao.replacePage`.
 */
data class ExpenseDetailColumns(
    val id: String,
    val notes: String?,
    val conversionRate: String?,
)

/** An expense and the rows saying who it was for, which are never read apart. */
data class ExpenseWithPaidFor(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumn = "id", entityColumn = "expenseId")
    val paidFor: List<PaidForEntity>,
)
