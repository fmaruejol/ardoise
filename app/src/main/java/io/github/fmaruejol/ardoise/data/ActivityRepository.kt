package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.core.model.ActivityPage
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A group's activity log. Read only: the server writes it as a side effect of
 * the other repositories' mutations, which refresh it through the cache.
 */
class ActivityRepository(private val cache: SpliitCache) {
    fun activities(
        groupId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Flow<SpliitResult<ActivityPage>> =
        cache.cachedThenFresh(
            local = cache.activities(groupId, limit).map { activities ->
                ActivityPage(
                    activities = activities,
                    // The server's `hasMore` is not cached, so a full page
                    // stands in for it.
                    hasMore = activities.size >= limit,
                )
            },
            isCached = { it.activities.isNotEmpty() },
            refresh = { cache.refreshActivities(groupId, limit) },
        )

    companion object {
        /** The server defaults to five, which is a widget's worth. This is a screen. */
        const val DEFAULT_PAGE_SIZE: Int = 30
    }
}
