package io.github.fmaruejol.ardoise.core.model

import java.time.Instant
import java.time.LocalDate

enum class SplitMode {
    EVENLY,
    BY_SHARES,
    BY_PERCENTAGE,
    BY_AMOUNT,
    ;

    /**
     * The divisor for [PaidFor.shares] in this mode: 100 for `BY_SHARES` and
     * `BY_PERCENTAGE`, 1 for the rest. The server only rescales shares that
     * arrive as strings, so a wrong scale here is stored as given.
     */
    val shareScale: Long
        get() = when (this) {
            EVENLY, BY_AMOUNT -> 1L
            BY_SHARES, BY_PERCENTAGE -> 100L
        }

    /**
     * What the shares of one expense must add up to, or null when the mode
     * does not constrain them. The server enforces it.
     */
    fun requiredTotal(amount: Long): Long? = when (this) {
        BY_AMOUNT -> amount
        BY_PERCENTAGE -> PERCENT_TOTAL
        EVENLY, BY_SHARES -> null
    }

    companion object {
        /** The server's rule: `BY_PERCENTAGE` shares sum to 10000. */
        const val PERCENT_TOTAL: Long = 10_000

        /** Null for an unknown mode: a guess here produces wrong balances. */
        fun fromWire(value: String): SplitMode? = entries.firstOrNull { it.name == value }
    }
}

enum class RecurrenceRule {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    companion object {
        /** Unknown rules degrade to [NONE]: this only affects display. */
        fun fromWire(value: String?): RecurrenceRule =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

data class Category(
    val id: Int,
    val grouping: String,
    val name: String,
)

/**
 * One participant's entry on an expense.
 *
 * **[shares] is not an amount**: a count scaled by 100 for `BY_SHARES`, a
 * percentage scaled by 100 for `BY_PERCENTAGE`, minor units for `BY_AMOUNT`,
 * ignored for `EVENLY`. Write against [SplitMode.shareScale] and
 * [SplitMode.requiredTotal] rather than re-deriving it.
 */
data class PaidFor(
    val participantId: String,
    val shares: Long,
)

/** An expense as it appears in a group's list. */
data class ExpenseSummary(
    val id: String,
    val title: String,
    /** Minor units of the group's currency. Negative for income. */
    val amount: Long,
    /** Set when the expense was entered in another currency and converted. */
    val originalAmount: Long?,
    val originalCurrency: String?,
    val expenseDate: LocalDate,
    val createdAt: Instant,
    val category: Category?,
    val paidBy: Participant,
    val paidFor: List<PaidForWithParticipant>,
    val splitMode: SplitMode,
    val recurrenceRule: RecurrenceRule,
    val isReimbursement: Boolean,
    val documentCount: Int,
)

/** `groups.expenses.list` inlines the participant; `groups.expenses.get` does not. */
data class PaidForWithParticipant(
    val participant: Participant,
    val shares: Long,
)

/** A single expense with everything needed to edit it. */
data class Expense(
    val id: String,
    val groupId: String,
    val title: String,
    val amount: Long,
    val originalAmount: Long?,
    val originalCurrency: String?,
    /** The exact decimal string the server stored. Never a Double. */
    val conversionRate: String?,
    val expenseDate: LocalDate,
    val createdAt: Instant,
    val category: Category?,
    val paidById: String,
    val paidFor: List<PaidFor>,
    val splitMode: SplitMode,
    val recurrenceRule: RecurrenceRule,
    val isReimbursement: Boolean,
    val notes: String?,
    val documents: List<ExpenseDocument>,
)

data class ExpenseDocument(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int,
)

/** One page of `groups.expenses.list`. */
data class ExpensePage(
    val expenses: List<ExpenseSummary>,
    /** Whether the server has more beyond this page. */
    val hasMore: Boolean,
)
