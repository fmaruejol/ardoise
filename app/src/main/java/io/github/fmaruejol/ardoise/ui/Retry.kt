package io.github.fmaruejol.ardoise.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

/**
 * What the offline banner's "Retry" does. A read is the cache then a refresh,
 * once per subscription, so trying again means subscribing again.
 *
 * In the ViewModel rather than the repositories on purpose: a retry is one
 * screen asking for itself, not an announcement to everything else.
 */
class Retry {
    private val _attempts = MutableStateFlow(0)

    /** For a read that already restarts on something else, like the feed's filters. */
    val attempts: Flow<Int> = _attempts

    fun again() = _attempts.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> restarting(source: () -> Flow<T>): Flow<T> = _attempts.flatMapLatest { source() }
}
