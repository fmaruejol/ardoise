package io.github.fmaruejol.ardoise.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The two schemes, from one tonal palette with a teal-green seed.
 *
 * **The dark one is fixed, value for value.** The light one is *derived* from
 * it: M3's tone is CIELAB L*, so a missing tone is an interpolation rather
 * than a guess (`tools/tones.py`). Roles follow the standard M3 mapping, so
 * the two stay one palette read both ways.
 */
internal val ArdoiseDarkColors = darkColorScheme(
    primary = Color(0xFF53DBC5), // P80
    onPrimary = Color(0xFF003730), // P20
    primaryContainer = Color(0xFF005046), // P30
    onPrimaryContainer = Color(0xFF71F8E1), // P90
    inversePrimary = Color(0xFF006A5E), // P40
    secondary = Color(0xFFB1CCC6), // S80
    onSecondary = Color(0xFF1C3531), // S20
    secondaryContainer = Color(0xFF334B46), // S30
    onSecondaryContainer = Color(0xFFCFE8E2), // S90
    tertiary = Color(0xFFADCAE6), // T80
    onTertiary = Color(0xFF16324A), // T20
    tertiaryContainer = Color(0xFF2E4961), // T30
    onTertiaryContainer = Color(0xFFCBE6FF), // T90
    error = Color(0xFFFFB4AB), // E80
    onError = Color(0xFF690005), // E20
    errorContainer = Color(0xFF93000A), // E30
    onErrorContainer = Color(0xFFFFDAD6), // E90
    background = Color(0xFF0E1513), // N6
    onBackground = Color(0xFFDEE4E1), // N90
    surface = Color(0xFF0E1513), // N6
    onSurface = Color(0xFFDEE4E1), // N90
    surfaceVariant = Color(0xFF3F4946), // NV30
    onSurfaceVariant = Color(0xFFBEC9C5), // NV80
    surfaceContainerLowest = Color(0xFF090F0E), // N4
    surfaceContainerLow = Color(0xFF171D1B), // N10
    surfaceContainer = Color(0xFF1B2220), // N12
    surfaceContainerHigh = Color(0xFF252C2A), // N17
    surfaceContainerHighest = Color(0xFF303735), // N22
    outline = Color(0xFF889390), // NV60
    outlineVariant = Color(0xFF3F4946), // NV30
    inverseSurface = Color(0xFFDEE4E1), // N90
    inverseOnSurface = Color(0xFF2B3230), // N20
    surfaceTint = Color(0xFF53DBC5), // P80
)

/** The same palette read the other way up. Tones marked *derived* are new. */
internal val ArdoiseLightColors = lightColorScheme(
    primary = Color(0xFF006A5E), // P40
    onPrimary = Color(0xFFFFFFFF), // P100
    primaryContainer = Color(0xFF71F8E1), // P90
    onPrimaryContainer = Color(0xFF00201C), // P10, derived
    inversePrimary = Color(0xFF53DBC5), // P80
    secondary = Color(0xFF4B635E), // S40, derived
    onSecondary = Color(0xFFFFFFFF), // S100
    secondaryContainer = Color(0xFFCFE8E2), // S90
    onSecondaryContainer = Color(0xFF061F1C), // S10, derived
    tertiary = Color(0xFF46617A), // T40, derived
    onTertiary = Color(0xFFFFFFFF), // T100
    tertiaryContainer = Color(0xFFCBE6FF), // T90
    onTertiaryContainer = Color(0xFF001D32), // T10, derived
    error = Color(0xFFBA1A1A), // E40
    onError = Color(0xFFFFFFFF), // E100
    errorContainer = Color(0xFFFFDAD6), // E90
    onErrorContainer = Color(0xFF410002), // E10
    background = Color(0xFFF5FBF8), // N98, derived
    onBackground = Color(0xFF171D1B), // N10
    surface = Color(0xFFF5FBF8), // N98, derived
    onSurface = Color(0xFF171D1B), // N10
    surfaceVariant = Color(0xFFDAE5E1), // NV90, derived
    onSurfaceVariant = Color(0xFF3F4946), // NV30
    surfaceContainerLowest = Color(0xFFFFFFFF), // N100
    surfaceContainerLow = Color(0xFFEFF5F2), // N96, derived
    surfaceContainer = Color(0xFFE9EFEC), // N94, derived
    surfaceContainerHigh = Color(0xFFE3E9E6), // N92, derived
    surfaceContainerHighest = Color(0xFFDEE4E1), // N90
    outline = Color(0xFF6F7976), // NV50, derived
    outlineVariant = Color(0xFFBEC9C5), // NV80
    inverseSurface = Color(0xFF2B3230), // N20
    inverseOnSurface = Color(0xFFECF2EF), // N95, derived
    surfaceTint = Color(0xFF006A5E), // P40
)

/**
 * Amber, for the one thing that is neither an error nor business as usual.
 * Material 3 has no warning role, so it is carried beside the scheme. Used in
 * exactly one place, which is the bar for adding another.
 */
@Immutable
data class WarningPalette(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
    /** The body line under the heading, a step quieter than [onContainer]. */
    val onContainerVariant: Color,
)

/** The amber pair, on dark. */
private val DarkWarning = WarningPalette(
    color = Color(0xFFF2BF67), // W80
    onColor = Color(0xFF3B2E1A), // W20
    container = Color(0xFF3B2E1A), // W20
    onContainer = Color(0xFFF6E4C4), // W90
    onContainerVariant = Color(0xFFE0D0B4), // W85
)

/** The same pair the other way up, on the same tones as the scheme. */
private val LightWarning = WarningPalette(
    color = Color(0xFF735A32), // W40, derived
    onColor = Color(0xFFFFFFFF), // W100
    container = Color(0xFFF6E4C4), // W90
    onContainer = Color(0xFF261A00), // W10, derived
    onContainerVariant = Color(0xFF564426), // W30, derived
)

object WarningColors {
    /** The pair for whichever scheme is showing. */
    val current: WarningPalette
        @Composable
        @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) DarkWarning else LightWarning
}

/**
 * Container/content pairs that tell one group apart from the next: Spliit
 * stores no colour, so the tile is picked from the group id.
 *
 * Read from the scheme rather than fixed, so the tiles turn over with it.
 */
val groupTiles: List<Pair<Color, Color>>
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tiles()

private fun ColorScheme.tiles(): List<Pair<Color, Color>> = listOf(
    primaryContainer to onPrimaryContainer,
    tertiaryContainer to onTertiaryContainer,
    secondaryContainer to onSecondaryContainer,
)
