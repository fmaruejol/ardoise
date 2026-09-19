package io.github.fmaruejol.ardoise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.data.RefreshScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the offline banner's "Retry" and the pull gesture both do. A read is the
 * cache then a refresh, once per subscription, so trying again means
 * subscribing again.
 *
 * In the ViewModel rather than the repositories on purpose: a retry is one
 * screen asking for itself, not an announcement to everything else. So is
 * [refreshing], which is why a save running `refreshAfterExpenseChange` behind
 * a screen moves nothing here.
 *
 * The pull gesture and the banner's button are one action: both call [again].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Retry {
    private val attempts = MutableStateFlow(0)

    private val current = MutableStateFlow(RefreshScope())

    /** Null until something asks: the first load reports through `isLoading`. */
    private val awaited = MutableStateFlow<RefreshScope?>(null)

    /** For a read that already restarts on something else, like the feed's filters. */
    val restarts: Flow<RefreshScope> = attempts.map { current.value }

    val refreshing: Flow<Boolean> = awaited
        .flatMapLatest { scope -> scope?.state?.map { !it.quiet } ?: flowOf(false) }
        .distinctUntilChanged()

    fun again() {
        val scope = RefreshScope()
        // Both before the attempt, so nothing can subscribe against a scope
        // that is not the one being waited on.
        current.value = scope
        awaited.value = scope
        attempts.update { it + 1 }
    }

    fun <T> restarting(source: () -> Flow<T>): Flow<T> =
        attempts.flatMapLatest { track(current.value, source()) }

    /**
     * A read that refreshes nothing counts an emission as its answer, or a
     * pull on an empty group list — which asks the server nothing — would
     * spin for ever.
     */
    fun <T> track(scope: RefreshScope, source: Flow<T>): Flow<T> =
        source.onEach { scope.emitted() }.flowOn(scope)
}

/** Holds the indicator until the refresh answers, not until the cache does. */
fun <T> ViewModel.collectRefreshing(
    retry: Retry,
    state: MutableStateFlow<T>,
    withRefreshing: T.(Boolean) -> T,
): Job = viewModelScope.launch {
    retry.refreshing.collect { refreshing ->
        state.update { it.withRefreshing(refreshing) }
    }
}
