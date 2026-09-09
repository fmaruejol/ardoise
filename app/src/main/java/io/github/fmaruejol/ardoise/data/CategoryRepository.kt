package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.core.model.Category
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.data.local.SpliitCache
import kotlinx.coroutines.flow.Flow

/**
 * The server's fixed category list. Cached like everything else, and here it
 * is more than a convenience: once fetched, the expense form works offline
 * with real names instead of "General".
 */
class CategoryRepository(private val cache: SpliitCache) {
    fun categories(): Flow<SpliitResult<List<Category>>> =
        cache.cachedThenFresh(
            local = cache.categories(),
            isCached = { it.isNotEmpty() },
            refresh = { cache.refreshCategories() },
        )
}
