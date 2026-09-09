package io.github.fmaruejol.ardoise.core.prefs

import kotlinx.coroutines.flow.Flow

/**
 * What has to outlive a reinstall of the data: the group ids, which are the
 * only credential Spliit has, and this device's own answers. Reads are [Flow].
 */
interface GroupPreferences {
    /** Base URL of the Spliit instance, always with a trailing slash. */
    val baseUrl: Flow<String>

    /**
     * Whether a server was actually chosen. [baseUrl] cannot say: its default
     * is the cloud, so a fresh install looks like someone who picked it.
     */
    val hasChosenServer: Flow<Boolean>

    /** Known group ids, most recently opened first. */
    val knownGroupIds: Flow<List<String>>

    /** The groups saved for a particular server, current or not. */
    fun knownGroupIds(baseUrl: String): Flow<List<String>>

    /** Which participant of [groupId] the user identifies as, if they picked one. */
    fun activeParticipantId(groupId: String): Flow<String?>

    /**
     * The currency the create-group form opens on: a preference about this
     * device, not about any group. Absent means the device's own currency.
     */
    val defaultCurrencyCode: Flow<String>

    /**
     * BCP-47 tag, or null to follow the device, which is an answer rather
     * than a missing value: Android already falls back to English.
     */
    val languageTag: Flow<String?>

    /** Theme name, or null to follow the device. */
    val themeName: Flow<String?>

    /** Adds [groupId], or moves it to the front if it is already known. */
    suspend fun rememberGroup(groupId: String)

    suspend fun forgetGroup(groupId: String)

    suspend fun setActiveParticipantId(groupId: String, participantId: String?)

    suspend fun setBaseUrl(baseUrl: String)

    suspend fun setDefaultCurrencyCode(code: String)

    /** Null goes back to following the device's own language. */
    suspend fun setLanguageTag(tag: String?)

    /** Null goes back to following the device's own light/dark setting. */
    suspend fun setThemeName(name: String?)

    companion object {
        const val DEFAULT_BASE_URL: String = "https://spliit.app/"
    }
}
