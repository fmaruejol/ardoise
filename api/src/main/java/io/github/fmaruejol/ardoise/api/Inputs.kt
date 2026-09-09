package io.github.fmaruejol.ardoise.api

import io.github.fmaruejol.ardoise.core.model.ExpenseDocument
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.RecurrenceRule
import io.github.fmaruejol.ardoise.core.model.SplitMode
import java.time.LocalDate

/**
 * What the caller supplies to create or update a group. The server validates
 * with the web form's zod schema, name 2 to 50 characters, symbol 1 to 5,
 * code 3 or null, unique participant names, and refuses with `BAD_REQUEST`.
 */
data class GroupInput(
    val name: String,
    val currencySymbol: String,
    val currencyCode: String?,
    val participants: List<ParticipantInput>,
    val information: String? = null,
)

/** A participant on the way in: [id] null creates one, non-null updates it. */
data class ParticipantInput(
    val name: String,
    val id: String? = null,
)

/**
 * What the caller supplies to create or update an expense. [amount] and every
 * share are in minor units.
 *
 * **The shares in [paidFor] must already be in the stored representation for
 * [splitMode]** (see [PaidFor]): nothing corrects a wrong scale here, it is
 * stored as given. The server enforces the sums, 10000 for `BY_PERCENTAGE`,
 * [amount] for `BY_AMOUNT`.
 */
data class ExpenseInput(
    val title: String,
    val amount: Long,
    val expenseDate: LocalDate,
    val paidById: String,
    val paidFor: List<PaidFor>,
    val splitMode: SplitMode,
    val categoryId: Int = 0,
    val isReimbursement: Boolean = false,
    val notes: String? = null,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    /** Exact decimal text, e.g. `"1.0825"`. A string, so never a Double. */
    val conversionRate: String? = null,
    val documents: List<ExpenseDocument> = emptyList(),
    /** For the *web* client's default split. No server-side effect, but required. */
    val saveDefaultSplittingOptions: Boolean = false,
)
