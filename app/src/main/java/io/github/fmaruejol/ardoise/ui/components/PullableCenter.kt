package io.github.fmaruejol.ardoise.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A list rather than a `Box` because only a scrollable hands the pull gesture
 * to the indicator above it. Nothing here scrolls: `fillParentMaxSize` is
 * exactly the viewport.
 */
@Composable
fun PullableCenter(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}
