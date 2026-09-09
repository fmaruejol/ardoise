package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Light, dark, or whichever the phone is set to.
 *
 * [Dark] is what the app looks like; [Light] is the same palette read the
 * other way up (see `ui/theme/Color.kt`). [System] is the default: a phone
 * that switches at sunset is asking every app to.
 */
enum class AppTheme {
    System,
    Light,
    Dark,
    ;

    /** Whether to draw dark, given what the system currently says. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        System -> systemIsDark
        Light -> false
        Dark -> true
    }

    companion object {
        /** An unknown name means the system: it is what an absent one means too. */
        fun of(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: System
    }
}

/**
 * Which theme the app draws in.
 *
 * Device-local and un-refetchable, like the language and the base URL, so it
 * lives in DataStore beside them.
 */
class ThemeRepository(private val preferences: GroupPreferences) {
    val theme: Flow<AppTheme> = preferences.themeName.map(AppTheme::of)

    /**
     * Saved the moment it is chosen, like the language and for the same
     * reason: it cannot fail and there is nothing to roll back.
     */
    suspend fun setTheme(theme: AppTheme) {
        preferences.setThemeName(theme.takeUnless { it == AppTheme.System }?.name)
    }
}
