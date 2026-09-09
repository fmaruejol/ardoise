package io.github.fmaruejol.ardoise.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Whether Android will actually let this app use the network. On stock Android
 * the answer is always yes; GrapheneOS lets the user revoke `INTERNET` per app,
 * which fails exactly like being offline and needs the opposite advice.
 */
fun interface NetworkAccess {
    suspend fun isAllowed(): Boolean
}

/**
 * Answers by trying to open a socket, because asking does not work. Measured
 * on a Pixel with network access off:
 *
 * ```
 * checkSelfPermission(INTERNET) = 0   // PERMISSION_GRANTED
 * dumpsys package:  INTERNET: granted=false
 * new Socket():     SocketException: socket failed: EPERM
 * ```
 *
 * A real request is no use either: DNS is blocked too, so it fails as
 * `UnknownHostException`, exactly like having no connection.
 */
class AndroidNetworkAccess(
    private val context: Context,
    /** Injected so a test drives the probe on its own scheduler. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : NetworkAccess {
    override suspend fun isAllowed(): Boolean = withContext(io) {
        // Correct wherever the permission API is honest; hence the probe below.
        if (context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            return@withContext false
        }
        canOpenSocket()
    }

    /**
     * Opens a socket to loopback and throws it away: nothing leaves the device
     * and no name is resolved. A refused connection is the success case,
     * because it means the socket was created.
     */
    private fun canOpenSocket(): Boolean = try {
        Socket().use {
            it.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), DISCARD_PORT), PROBE_TIMEOUT_MS)
        }
        true
    } catch (e: SecurityException) {
        false
    } catch (e: IOException) {
        !e.isNetworkPermissionDenial()
    }

    private companion object {
        /** Discard. Nothing listens on it, so the attempt fails immediately. */
        const val DISCARD_PORT = 9
        const val PROBE_TIMEOUT_MS = 200
    }
}

/**
 * Whether a socket failure was Android refusing the network. The observed one
 * is a plain `SocketException` with the errno only in the message; the typed
 * cause is checked first for the layers that keep it.
 */
internal fun IOException.isNetworkPermissionDenial(): Boolean {
    val errno = (cause as? ErrnoException)?.errno
    if (errno == OsConstants.EPERM || errno == OsConstants.EACCES) return true
    return message.orEmpty().let { it.contains("EPERM") || it.contains("EACCES") }
}
