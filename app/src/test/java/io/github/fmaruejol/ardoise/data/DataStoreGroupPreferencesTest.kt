package io.github.fmaruejol.ardoise.data

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.github.fmaruejol.ardoise.core.currency.GroupCurrency
import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** The real preferences, on Robolectric because DataStore needs a file to write to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreGroupPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())
    private lateinit var file: java.io.File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferences: DataStoreGroupPreferences

    @Before
    fun setUp() {
        file = context.filesDir.resolve("test-${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        preferences = DataStoreGroupPreferences(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    /** Writes the pre-per-server key an already-installed app would have. */
    private suspend fun legacyList(vararg ids: String) {
        dataStore.edit { it[stringPreferencesKey("group_ids")] = ids.joinToString(separator = "\n") }
    }

    @Test
    fun `keeps a separate list per server`() = runTest {
        preferences.rememberGroup("cloud-group")

        preferences.setBaseUrl("https://spliit.example.com")

        // A group id only means anything to the instance holding the group, so
        // the new server starts empty rather than inheriting ids that cannot
        // resolve on it.
        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
        assertEquals(
            listOf("cloud-group"),
            preferences.knownGroupIds(GroupPreferences.DEFAULT_BASE_URL).first(),
        )
    }

    @Test
    fun `brings the old list back when the server comes back`() = runTest {
        preferences.rememberGroup("cloud-group")
        preferences.setBaseUrl("https://spliit.example.com")
        preferences.rememberGroup("self-hosted-group")

        preferences.setBaseUrl(GroupPreferences.DEFAULT_BASE_URL)

        assertEquals(listOf("cloud-group"), preferences.knownGroupIds.first())
    }

    @Test
    fun `keeps the most recently remembered group first`() = runTest {
        preferences.rememberGroup("a")
        preferences.rememberGroup("b")
        preferences.rememberGroup("a")

        // The list is the app's navigation; the order is the one the user last
        // used, not the order they were added.
        assertEquals(listOf("a", "b"), preferences.knownGroupIds.first())
    }

    @Test
    fun `forgetting a group drops it from that server only`() = runTest {
        preferences.rememberGroup("a")
        preferences.rememberGroup("b")

        preferences.forgetGroup("a")

        assertEquals(listOf("b"), preferences.knownGroupIds.first())
    }

    @Test
    fun `a trailing slash is not a different server`() = runTest {
        preferences.setBaseUrl("https://spliit.example.com")
        preferences.rememberGroup("g1")

        assertEquals(
            listOf("g1"),
            preferences.knownGroupIds("https://spliit.example.com").first(),
        )
    }

    // --- upgrading from the single pre-per-server list ---------------------

    @Test
    fun `an existing list is read as the current server's`() = runTest {
        legacyList("a", "b")

        // Anything already installed has ids under the old key, built against
        // whatever address was in use, which is the one still in use.
        assertEquals(listOf("a", "b"), preferences.knownGroupIds.first())
    }

    @Test
    fun `an existing list stays with the server it was built against`() = runTest {
        legacyList("a", "b")

        preferences.setBaseUrl("https://spliit.example.com")

        assertEquals(emptyList<String>(), preferences.knownGroupIds.first())
        assertEquals(
            listOf("a", "b"),
            preferences.knownGroupIds(GroupPreferences.DEFAULT_BASE_URL).first(),
        )
    }

    @Test
    fun `an existing list is not duplicated by the first write`() = runTest {
        legacyList("a", "b")

        preferences.rememberGroup("c")

        assertEquals(listOf("c", "a", "b"), preferences.knownGroupIds.first())
    }

    @Test
    fun `the default address is not a chosen one`() = runTest {
        assertEquals(false, preferences.hasChosenServer.first())

        preferences.setBaseUrl(GroupPreferences.DEFAULT_BASE_URL)

        assertEquals(true, preferences.hasChosenServer.first())
    }

    // --- the currency a new group starts on -------------------------------

    @Test
    fun `offers the device's own currency until the user says otherwise`() = runTest {
        // Nothing stored is not the same as nothing to say: the first group is
        // created before anyone has been near the settings screen.
        assertEquals(
            GroupCurrency.defaultCodeFor(),
            preferences.defaultCurrencyCode.first(),
        )
    }

    @Test
    fun `keeps the chosen currency`() = runTest {
        preferences.setDefaultCurrencyCode("JPY")

        assertEquals("JPY", preferences.defaultCurrencyCode.first())
    }

    @Test
    fun `keeps the currency across a change of server`() = runTest {
        preferences.setDefaultCurrencyCode("SEK")

        preferences.setBaseUrl("https://spliit.example.com")

        // Which currency someone tends to split in is about them, not about
        // the instance their groups happen to live on.
        assertEquals("SEK", preferences.defaultCurrencyCode.first())
    }
}
