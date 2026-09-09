package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.api.SpliitApiFactory
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.github.fmaruejol.ardoise.core.result.map
import kotlinx.coroutines.flow.Flow

/**
 * Which Spliit instance the app talks to.
 *
 * Everything else in the app reads the stored base URL indirectly, through the
 * injected client. This is the one place that changes it.
 */
class ServerRepository(
    private val preferences: GroupPreferences,
    private val apiFactory: SpliitApiFactory,
) {
    val baseUrl: Flow<String> = preferences.baseUrl

    /** Whether a server has been chosen, as opposed to the default standing in. */
    val hasChosenServer: Flow<Boolean> = preferences.hasChosenServer

    suspend fun setBaseUrl(baseUrl: String) {
        preferences.setBaseUrl(baseUrl)
    }

    /**
     * Confirms that a Spliit instance actually answers at [baseUrl], before
     * anything is saved.
     *
     * Uses `categories.list` because it is the only procedure that needs no
     * input and no group: reaching it proves the address is a Spliit server
     * and not merely a host that responds. A typo caught here is worth far
     * more than the same typo surfacing later as an unexplained empty screen.
     */
    suspend fun check(baseUrl: String): SpliitResult<Unit> =
        apiFactory.create(baseUrl).listCategories().map { }
}
