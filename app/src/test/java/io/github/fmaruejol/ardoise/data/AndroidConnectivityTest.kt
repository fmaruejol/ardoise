package io.github.fmaruejol.ardoise.data

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidConnectivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val connectivity = AndroidConnectivity(context)

    private fun network(internet: Boolean, validated: Boolean) {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).apply {
            removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (internet) addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
    }

    @Test
    fun `a network the system never validated is still a network`() = runTest {
        network(internet = true, validated = false)

        // GrapheneOS lets the connectivity check be turned off, and then
        // nothing is ever marked validated.
        connectivity.offline().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a validated network is online`() = runTest {
        network(internet = true, validated = true)

        connectivity.offline().test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no network at all is offline`() = runTest {
        network(internet = false, validated = false)

        connectivity.offline().test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a handover is not an outage`() = runTest {
        network(internet = true, validated = true)

        connectivity.offline().test {
            assertFalse(awaitItem())

            // The arriving network, while `activeNetwork` still points at
            // the one being replaced.
            shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, null)
            val arriving = ShadowNetworkCapabilities.newInstance()
            shadowOf(arriving).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowOf(manager).networkCallbacks.forEach {
                it.onCapabilitiesChanged(manager.activeNetwork!!, arriving)
            }

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `losing the network is offline, whatever the manager still answers`() = runTest {
        network(internet = true, validated = true)

        connectivity.offline().test {
            assertFalse(awaitItem())

            val net = manager.activeNetwork!!
            shadowOf(manager).networkCallbacks.forEach {
                it.onAvailable(net)
                // The manager goes on reporting this network as usable
                // while it is torn down, so asking it here said "online".
                it.onLost(net)
            }

            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a handover to another network never reads as offline`() = runTest {
        network(internet = true, validated = true)

        connectivity.offline().test {
            assertFalse(awaitItem())

            val old = manager.activeNetwork!!
            shadowOf(manager).networkCallbacks.forEach { it.onAvailable(old) }

            // Wi-fi to mobile drops the old network ~50ms before the new
            // one arrives, measured on an emulator.
            shadowOf(manager).networkCallbacks.forEach {
                it.onLost(old)
                it.onAvailable(old)
            }

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
