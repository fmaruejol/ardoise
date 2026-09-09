package io.github.fmaruejol.ardoise.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
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
     * Whether the platform reports no usable network. Read **only** to put the
     * offline banner up, never as a gate: the platform saying "no" is
     * reliable, the platform saying "yes" is not.
     */
    fun offline(): Flow<Boolean>
}

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

        fun report() {
            trySend(!manager.hasUsableNetwork())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = report()

            override fun onLost(network: Network) = report()

            // A network that has not validated, a captive portal, a wi-fi
            // still handshaking, carries nothing.
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = report()
        }

        // The callback only reports changes, so a screen opened in flight mode
        // would otherwise never hear anything.
        report()
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun ConnectivityManager.hasUsableNetwork(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
