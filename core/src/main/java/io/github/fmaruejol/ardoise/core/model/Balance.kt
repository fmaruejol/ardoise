package io.github.fmaruejol.ardoise.core.model

/**
 * A participant's position, in minor units. These are *public* balances,
 * derived from the suggested reimbursements, so [paid] and [paidFor] are
 * "will receive" and "will pay", not what anyone spent.
 */
data class Balance(
    val participantId: String,
    val paid: Long,
    val paidFor: Long,
    val total: Long,
)

/** A suggested payment that settles part of the group. */
data class Reimbursement(
    val fromParticipantId: String,
    val toParticipantId: String,
    val amount: Long,
)

data class GroupBalances(
    val balances: List<Balance>,
    val reimbursements: List<Reimbursement>,
)

/** One entry of `groups.balances.forUser`: the user's net position in a group. */
data class UserGroupBalance(
    val groupId: String,
    val groupName: String,
    val currencySymbol: String,
    val currencyCode: String?,
    val participantId: String,
    val participantName: String,
    /** Positive means the group owes the participant. */
    val amount: Long,
)
