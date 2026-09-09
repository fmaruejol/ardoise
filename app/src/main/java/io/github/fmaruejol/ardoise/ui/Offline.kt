package io.github.fmaruejol.ardoise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fmaruejol.ardoise.data.Connectivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps a screen's state in step with whether the platform has a network, so
 * the offline banner can go up over rows that came from the cache.
 *
 * Read **only** for that: never to block, hide or skip anything. The platform
 * saying "no network" is reliable, the platform saying "yes" is not. Pairs
 * with [Retry], which is what the banner's button calls.
 */
fun <T> ViewModel.collectOffline(
    connectivity: Connectivity,
    state: MutableStateFlow<T>,
    withOffline: T.(Boolean) -> T,
): Job = viewModelScope.launch {
    connectivity.offline().collect { offline ->
        state.update { it.withOffline(offline) }
    }
}
