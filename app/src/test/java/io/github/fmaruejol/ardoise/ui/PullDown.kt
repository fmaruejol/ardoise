package io.github.fmaruejol.ardoise.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTouchInput

/**
 * The pull gesture, as a drag rather than a swipe: the indicator only fires
 * once the drag is past its threshold, and `swipeDown` over one row of a list
 * does not travel far enough to reach it.
 */
fun SemanticsNodeInteraction.pullDown() = performTouchInput {
    down(topCenter)
    repeat(12) { moveBy(Offset(0f, 60f)) }
    up()
}
