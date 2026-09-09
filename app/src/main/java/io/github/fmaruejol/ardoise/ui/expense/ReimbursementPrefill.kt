package io.github.fmaruejol.ardoise.ui.expense

/**
 * What "Mark as paid" hands the expense form. Settling up writes nothing
 * itself: it opens the expense form filled in, because `groups.expenses.create` is not
 * idempotent and a payment nobody confirmed is as wrong as one recorded twice.
 *
 * The shape is upstream's link, `from`, `to`, `amount`, and [title] comes
 * from `strings.xml` via the caller, since a ViewModel has no `Context`.
 */
data class ReimbursementPrefill(
    val payerId: String,
    val payeeId: String,
    /** Minor units of the group's currency, as the balances report it. */
    val amount: Long,
    val title: String,
)
