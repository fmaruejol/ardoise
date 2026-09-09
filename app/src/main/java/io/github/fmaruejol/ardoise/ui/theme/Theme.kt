package io.github.fmaruejol.ardoise.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which way up the scheme is, for what the scheme cannot carry, the amber
 * warning pair. A `ColorScheme` has no "is this dark" flag, and reading a
 * surface's luminance back would be guessing at what the theme knows.
 */
internal val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * The app theme. **Dark is the fixed scheme**; light is derived from the same
 * tonal palette (see `Color.kt`). No dynamic colour: Material You would
 * replace the palette rather than turn it over.
 *
 * [darkTheme] defaults to true, and `MainActivity` is the one place that
 * passes the user's choice.
 */
@Composable
fun ArdoiseTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ArdoiseDarkColors else ArdoiseLightColors,
        typography = ArdoiseTypography,
    ) {
        CompositionLocalProvider(LocalIsDarkTheme provides darkTheme, content = content)
    }
}
