package io.github.fmaruejol.ardoise.ui.theme

import android.app.Application
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.fmaruejol.ardoise.data.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dark scheme is fixed; the light one is derived from the same palette. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class ThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private class Drawn {
        lateinit var scheme: ColorScheme
        lateinit var warning: WarningPalette
        lateinit var tiles: List<Pair<Color, Color>>
    }

    /** Both schemes in one composition: a rule sets its content once. */
    private fun schemes(): Pair<Drawn, Drawn> {
        val dark = Drawn()
        val light = Drawn()
        compose.setContent {
            ArdoiseTheme(darkTheme = true) {
                dark.scheme = MaterialTheme.colorScheme
                dark.warning = WarningColors.current
                dark.tiles = groupTiles
            }
            ArdoiseTheme(darkTheme = false) {
                light.scheme = MaterialTheme.colorScheme
                light.warning = WarningColors.current
                light.tiles = groupTiles
            }
        }
        return dark to light
    }

    @Test
    fun `dark is the scheme the app ships`() {
        val (dark, _) = schemes()

        // The fixed values, which no derivation is allowed to move.
        assertEquals(Color(0xFF53DBC5), dark.scheme.primary)
        assertEquals(Color(0xFF0E1513), dark.scheme.surface)
        assertEquals(Color(0xFFDEE4E1), dark.scheme.onSurface)
    }

    @Test
    fun `light is light, and dark is dark`() {
        val (dark, light) = schemes()

        assertTrue(light.scheme.surface.luminance() > 0.8f)
        assertTrue(light.scheme.onSurface.luminance() < 0.1f)
        assertTrue(dark.scheme.surface.luminance() < 0.05f)
        assertTrue(dark.scheme.onSurface.luminance() > 0.6f)
    }

    @Test
    fun `light keeps the palette it was derived from`() {
        val (_, light) = schemes()

        // P40 and P80 are one palette read at two tones, so the light scheme's
        // primary is the dark scheme's inversePrimary and the other way about.
        assertEquals(Color(0xFF006A5E), light.scheme.primary)
        assertEquals(Color(0xFF53DBC5), light.scheme.inversePrimary)
    }

    @Test
    fun `the warning pair turns over with the scheme`() {
        val (dark, light) = schemes()

        // Material 3 has no warning role, so this pair is carried beside the
        // scheme, and it has to turn over with it, or the one amber card in
        // the app is the only dark thing on a light screen.
        assertNotEquals(dark.warning.container, light.warning.container)
        assertTrue(dark.warning.container.luminance() < 0.1f)
        assertTrue(light.warning.container.luminance() > 0.6f)
    }

    @Test
    fun `the group tiles come from the scheme, not from a fixed list`() {
        val (dark, light) = schemes()

        assertEquals(3, light.tiles.size)
        assertNotEquals(dark.tiles, light.tiles)
    }

    @Test
    fun `a theme decides what dark means from what the system says`() {
        assertEquals(true, AppTheme.System.isDark(systemIsDark = true))
        assertEquals(false, AppTheme.System.isDark(systemIsDark = false))
        // A choice is a choice: the system does not get to override it.
        assertEquals(false, AppTheme.Light.isDark(systemIsDark = true))
        assertEquals(true, AppTheme.Dark.isDark(systemIsDark = false))
    }
}
