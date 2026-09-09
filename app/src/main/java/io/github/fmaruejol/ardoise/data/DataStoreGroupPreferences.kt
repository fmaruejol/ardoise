package io.github.fmaruejol.ardoise.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.spliitSettings by preferencesDataStore(name = "spliit_settings")

/** The app's one preferences store. A test can point the class at another file. */
fun spliitPreferencesDataStore(context: Context): DataStore<Preferences> = context.spliitSettings

/**
 * DataStore-backed [GroupPreferences]. Ids are a newline-separated list
 * because their order is the user's and a preference Set would lose it.
 *
 * **Kept per server**: an id only means something to the instance holding the
 * group, so switching servers parks the current list rather than losing it.
 */
class DataStoreGroupPreferences(
    private val dataStore: DataStore<Preferences>,
) : GroupPreferences {
    override val baseUrl: Flow<String> = dataStore.data.map { it.baseUrl() }

    override val hasChosenServer: Flow<Boolean> =
        dataStore.data.map { it.contains(KEY_BASE_URL) }

    override val knownGroupIds: Flow<List<String>> =
        dataStore.data.map { it.groupIds(it.baseUrl()) }

    override fun knownGroupIds(baseUrl: String): Flow<List<String>> =
        dataStore.data.map { it.groupIds(baseUrl.withTrailingSlash()) }

    override fun activeParticipantId(groupId: String): Flow<String?> =
        dataStore.data.map { it[activeParticipantKey(groupId)] }

    override val defaultCurrencyCode: Flow<String> =
        dataStore.data.map { it[KEY_DEFAULT_CURRENCY] ?: GroupCurrency.defaultCodeFor() }

    override val languageTag: Flow<String?> = dataStore.data.map { it[KEY_LANGUAGE] }

    override val themeName: Flow<String?> = dataStore.data.map { it[KEY_THEME] }

    override suspend fun rememberGroup(groupId: String) {
        dataStore.edit { prefs ->
            val url = prefs.baseUrl()
            val ids = prefs.groupIds(url)
            prefs.setGroupIds(url, listOf(groupId) + ids.filterNot { it == groupId })
        }
    }

    override suspend fun forgetGroup(groupId: String) {
        dataStore.edit { prefs ->
            val url = prefs.baseUrl()
            prefs.setGroupIds(url, prefs.groupIds(url).filterNot { it == groupId })
            prefs.remove(activeParticipantKey(groupId))
        }
    }

    override suspend fun setActiveParticipantId(groupId: String, participantId: String?) {
        dataStore.edit { prefs ->
            val key = activeParticipantKey(groupId)
            if (participantId == null) prefs.remove(key) else prefs[key] = participantId
        }
    }

    override suspend fun setDefaultCurrencyCode(code: String) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_CURRENCY] = code }
    }

    override suspend fun setLanguageTag(tag: String?) {
        dataStore.edit { prefs ->
            if (tag == null) prefs.remove(KEY_LANGUAGE) else prefs[KEY_LANGUAGE] = tag
        }
    }

    override suspend fun setThemeName(name: String?) {
        dataStore.edit { prefs ->
            if (name == null) prefs.remove(KEY_THEME) else prefs[KEY_THEME] = name
        }
    }

    override suspend fun setBaseUrl(baseUrl: String) {
        dataStore.edit { prefs ->
            // Anchor the pre-per-server list to the server it was built
            // against, the one being left, before the address moves.
            val previous = prefs.baseUrl()
            prefs[KEY_LEGACY_GROUP_IDS]?.let { legacy ->
                prefs[groupIdsKey(previous)] = legacy
                prefs.remove(KEY_LEGACY_GROUP_IDS)
            }
            prefs[KEY_BASE_URL] = baseUrl.withTrailingSlash()
        }
    }

    private companion object {
        const val SEPARATOR = "\n"
        val KEY_BASE_URL = stringPreferencesKey("base_url")

        // Not per server: which currency someone splits in follows them.
        val KEY_DEFAULT_CURRENCY = stringPreferencesKey("default_currency")

        // Absent means "follow the device", hence removed rather than stored.
        val KEY_LANGUAGE = stringPreferencesKey("language")

        val KEY_THEME = stringPreferencesKey("theme")

        /** The single list from before group ids were kept per server. */
        val KEY_LEGACY_GROUP_IDS = stringPreferencesKey("group_ids")

        fun groupIdsKey(baseUrl: String) = stringPreferencesKey("group_ids@$baseUrl")

        fun activeParticipantKey(groupId: String) = stringPreferencesKey("active_participant_$groupId")

        fun Preferences.baseUrl(): String = this[KEY_BASE_URL] ?: GroupPreferences.DEFAULT_BASE_URL

        fun Preferences.groupIds(baseUrl: String): List<String> {
            val stored = this[groupIdsKey(baseUrl)]
            // The legacy list belongs to whatever server was current when it
            // was written, which is this one until a switch moves it.
            val raw = stored ?: this[KEY_LEGACY_GROUP_IDS].takeIf { baseUrl == baseUrl() }
            return raw.orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
        }

        fun MutablePreferences.setGroupIds(baseUrl: String, ids: List<String>) {
            this[groupIdsKey(baseUrl)] = ids.joinToString(SEPARATOR)
            remove(KEY_LEGACY_GROUP_IDS)
        }

        fun String.withTrailingSlash() = if (endsWith("/")) this else "$this/"
    }
}
