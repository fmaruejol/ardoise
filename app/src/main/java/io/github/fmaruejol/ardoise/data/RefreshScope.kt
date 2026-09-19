package io.github.fmaruejol.ardoise.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Whether a refresh has finished cannot be read off what a read emits: one
 * that changes nothing writes identical rows, and `cachedThenFresh` drops the
 * repeat, so the answer would never arrive.
 *
 * **Carried in the coroutine context rather than injected**, because what has
 * to be counted is the dynamic extent of one read. Two screens on the same
 * group refresh independently, and `SpliitCache.refreshAfterExpenseChange` is
 * nobody's pull: it runs with no scope in context and moves nothing here.
 */
class RefreshScope : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RefreshScope>

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(val inFlight: Int = 0, val progressed: Boolean = false) {
        /**
         * A new scope is **not** quiet, so the indicator is owed from the
         * moment the gesture asks rather than from the moment work begins.
         */
        val quiet: Boolean get() = inFlight == 0 && progressed
    }

    fun begin() = _state.update { it.copy(inFlight = it.inFlight + 1) }

    fun finished() = _state.update { it.copy(inFlight = it.inFlight - 1, progressed = true) }

    fun emitted() = _state.update { it.copy(progressed = true) }
}
