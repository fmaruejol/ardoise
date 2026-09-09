package io.github.fmaruejol.ardoise.core.model

import java.time.Instant

/** A Spliit group. There are no accounts: the [id] is the only credential. */
data class Group(
    val id: String,
    val name: String,
    val information: String?,
    /** Display symbol, free text. Not an ISO code, and says nothing about scale. */
    val currencySymbol: String,
    /**
     * ISO 4217, if the group has one. **This** decides the scale of every
     * amount in the group; null on groups created before the field existed.
     */
    val currencyCode: String?,
    val createdAt: Instant,
    val participants: List<Participant>,
) {
    fun participant(id: String?): Participant? =
        if (id == null) null else participants.firstOrNull { it.id == id }
}

data class Participant(
    val id: String,
    val name: String,
)

/** What `groups.list` returns: enough to render the group list, no participants. */
data class GroupSummary(
    val id: String,
    val name: String,
    val information: String?,
    val currencySymbol: String,
    val currencyCode: String?,
    val createdAt: Instant,
    val participantCount: Int,
)

/**
 * `groups.getDetails`: the group plus the participants that appear on an
 * expense, the ones that cannot be removed yet.
 */
data class GroupDetails(
    val group: Group,
    val participantIdsWithExpenses: List<String>,
)
