package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.stats.GroupStats
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import kotlinx.coroutines.flow.Flow

/**
 * A group's spending, as the server adds it up. Not computed from the cached
 * expenses, which are one page of the group, and the web and iOS clients read
 * the same figures from the same procedure.
 */
class StatsRepository(private val cache: SpliitCache) {
    fun stats(groupId: String): Flow<SpliitResult<GroupStats?>> =
        cache.cachedThenFresh(
            local = cache.stats(groupId),
            isCached = { it != null },
            refresh = { cache.refreshStats(groupId) },
        )
}
