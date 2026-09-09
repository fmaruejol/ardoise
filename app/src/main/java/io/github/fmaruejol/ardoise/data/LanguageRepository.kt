package io.github.fmaruejol.ardoise.data

import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A language the app is actually translated into, plus following the device:
 * an option with no strings behind it would be a switch that changes nothing.
 */
enum class AppLanguage(
    /** BCP-47, or null for [System], the device's own language. */
    val tag: String?,
) {
    /**
     * Whatever the phone is set to, the default, and where an untranslated
     * language lands: Android resolves `values` when it cannot resolve
     * `values-fr`, so the fallback is the platform's, not ours.
     */
    System(null),
    English("en"),
    French("fr"),
    ;

    companion object {
        /** An unknown tag means the device: it is what an absent one means too. */
        fun of(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: System
    }
}

/**
 * Which language the app is read in. Device-local and un-refetchable, so it
 * lives in DataStore beside the base URL. The server is never told.
 */
class LanguageRepository(private val preferences: GroupPreferences) {
    val language: Flow<AppLanguage> = preferences.languageTag.map(AppLanguage::of)

    /** Saved the moment it is chosen: it cannot fail and there is nothing to roll back. */
    suspend fun setLanguage(language: AppLanguage) {
        preferences.setLanguageTag(language.tag)
    }
}
