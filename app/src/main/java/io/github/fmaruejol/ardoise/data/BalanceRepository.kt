package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.api.SpliitApi
import io.github.fmaruejol.ardoise.core.model.GroupBalances
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Balances, as the server computes them. The same numbers come out of
 * `io.github.fmaruejol.ardoise.core.settlement.settle`; one round trip beats fetching every
 * expense to add them up.
 */
class BalanceRepository(
    private val api: SpliitApi,
    private val preferences: GroupPreferences,
    private val cache: SpliitCache,
) {
    /**
     * One group's balances and the payments that would settle it. *Public*
     * balances, derived from the reimbursements rather than raw totals, see
     * [io.github.fmaruejol.ardoise.core.model.Balance].
     */
    fun balances(groupId: String): Flow<SpliitResult<GroupBalances>> =
        cache.cachedThenFresh(
            local = cache.balances(groupId),
            isCached = { it.balances.isNotEmpty() },
            refresh = { cache.refreshBalances(groupId) },
        )

    /**
     * The user's position across every group where they have said who they
     * are, in one call. Groups with no participant chosen are left out rather
     * than guessed at, and amounts stay in each group's own currency.
     *
     * **It re-asks when a balance in the cache moves.** This is the one read
     * with no rows of its own, so it had nothing to wake it:
     * [SpliitCache.balanceChanges] is that wake-up.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun userBalances(): Flow<SpliitResult<List<UserGroupBalance>>> =
        preferences.knownGroupIds
            .flatMapLatest { groupIds ->
                if (groupIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        groupIds.map { groupId ->
                            preferences.activeParticipantId(groupId).map { groupId to it }
                        },
                    ) { it.toList() }
                }
            }
            .combine(cache.balanceChanges()) { identified, _ -> identified }
            .map { identified ->
                val groups = identified.mapNotNull { (groupId, participantId) ->
                    participantId?.let { groupId to it }
                }
                if (groups.isEmpty()) {
                    SpliitResult.Success(emptyList())
                } else {
                    api.balancesForUser(groups)
                }
            }
}
