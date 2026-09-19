package io.github.fmaruejol.ardoise.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Says when a network turns up, only the "it is back" edge, because that is
 * the only moment anything acts on it: the outbox is sent then.
 *
 * Never an "are we online" flag. On GrapheneOS the system can report a good
 * network while the app's own sockets are refused, so the platform's answer is
 * a hint to try again and the try is what proves it.
 */
interface Connectivity {
    fun available(): Flow<Unit>

    /**
     * Read **only** to put the offline banner up, never as a gate: neither
     * answer is reliable, and the read is what settles it.
     */
    fun offline(): Flow<Boolean>
}

@OptIn(FlowPreview::class)
class AndroidConnectivity(private val context: Context) : Connectivity {
    override fun available(): Flow<Unit> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        if (manager == null) {
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }
        }

        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        // A device moving from mobile to wi-fi announces both.
        .conflate()

    override fun offline(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        /**
         * Tracked rather than asked of the manager, which inside `onLost`
         * still answers with the network being torn down — so flight mode
         * called itself online and no banner ever went up.
         */
        var default: Network? = null

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                default = network
                trySend(false)
            }

            // A replacement announced first leaves this naming the old
            // network, and taking that as an outage would strand the banner.
            override fun onLost(network: Network) {
                if (network != default) return
                default = null
                trySend(true)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                if (network != default) return
                trySend(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        // The callback only reports changes, so a screen opened in flight mode
        // would otherwise never hear anything.
        trySend(!manager.hasUsableNetwork())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        // Wi-fi to mobile loses the old network ~50ms before the new one
        // arrives, measured, and a banner for that is a red flash over a phone
        // connected throughout. Coming back is reported at once.
        .debounce { if (it) SETTLE_MS else 0L }
        .distinctUntilChanged()

    /**
     * **Deliberately not `NET_CAPABILITY_VALIDATED`.** That comes from a
     * connectivity check the user can turn off — GrapheneOS ships a toggle —
     * and a working network is then never marked validated. What it bought is
     * covered anyway: a captive portal fails the read, and the read says so.
     */
    private companion object {
        const val SETTLE_MS = 1_000L
    }

    private fun ConnectivityManager.hasUsableNetwork(): Boolean =
        getNetworkCapabilities(activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
