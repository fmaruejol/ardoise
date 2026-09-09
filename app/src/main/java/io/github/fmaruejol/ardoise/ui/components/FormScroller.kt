package io.github.fmaruejol.ardoise.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Room left above the field, so it does not land against the app bar. */
private val Headroom = 24.dp

/**
 * Scrolls a form to the field that is wrong: a marked field off the screen is
 * the same as no answer at all.
 *
 * Positions are measured in the **root** and turned into an offset against the
 * container, because a field is nested a couple of layouts deep inside it.
 */
@Stable
class FormScroller internal constructor(val state: ScrollState, private val headroom: Float) {
    private var containerTop by mutableFloatStateOf(0f)
    private val fieldTops = mutableStateMapOf<Any, Float>()

    internal fun recordContainer(top: Float) {
        containerTop = top
    }

    internal fun recordField(key: Any, top: Float) {
        fieldTops[key] = top
    }

    /** Brings [key] into view, or does nothing if it has not been laid out. */
    suspend fun scrollTo(key: Any) {
        val top = fieldTops[key] ?: return
        val target = state.value + (top - containerTop) - headroom
        state.animateScrollTo(target.roundToInt().coerceIn(0, state.maxValue))
    }
}

/** Put this on the scrolling container itself. */
fun Modifier.scrollContainer(scroller: FormScroller): Modifier =
    onGloballyPositioned { scroller.recordContainer(it.positionInRoot().y) }

/** Put this on a field that can be scrolled to, with a key of your choosing. */
fun Modifier.scrollTarget(scroller: FormScroller, key: Any): Modifier =
    onGloballyPositioned { scroller.recordField(key, it.positionInRoot().y) }

@Composable
fun rememberFormScroller(state: ScrollState, headroom: Dp = Headroom): FormScroller {
    val px = with(LocalDensity.current) { headroom.toPx() }
    return remember(state, px) { FormScroller(state, px) }
}
