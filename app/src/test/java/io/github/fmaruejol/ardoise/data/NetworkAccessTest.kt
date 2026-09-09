package io.github.fmaruejol.ardoise.data

import android.app.Application
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

/** Pins how a blocked socket is told apart from an ordinary one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NetworkAccessTest {
    @Test
    fun `recognises the failure GrapheneOS actually produces`() {
        val observed = SocketException("socket failed: EPERM (Operation not permitted)")

        assertTrue(observed.isNetworkPermissionDenial())
    }

    @Test
    fun `recognises a typed errno cause`() {
        assertTrue(IOException("failed", ErrnoException("socket", OsConstants.EPERM)).isNetworkPermissionDenial())
        assertTrue(IOException("failed", ErrnoException("socket", OsConstants.EACCES)).isNetworkPermissionDenial())
    }

    @Test
    fun `leaves the expected refusal alone`() {
        // Nothing listens on the probe port, so this is the success case: the
        // socket was created, which is all the probe wanted to know.
        assertFalse(ConnectException("Connection refused").isNetworkPermissionDenial())
    }

    @Test
    fun `leaves ordinary connection failures alone`() {
        assertFalse(SocketTimeoutException("timeout").isNetworkPermissionDenial())
        assertFalse(SocketException("Connection reset").isNetworkPermissionDenial())
        val refused = ErrnoException("connect", OsConstants.ECONNREFUSED)
        assertFalse(IOException("failed", refused).isNetworkPermissionDenial())
        assertFalse(IOException(null as String?).isNetworkPermissionDenial())
    }

    @Test
    fun `the probe runs on the dispatcher it was given`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val access = AndroidNetworkAccess(
            ApplicationProvider.getApplicationContext<Application>(),
            io = dispatcher,
        )

        var answer: Boolean? = null
        launch { answer = access.isAllowed() }

        // Nothing has run: the probe is queued on the injected dispatcher
        // rather than jumping to a real IO thread of its own.
        assertNull(answer)
        advanceUntilIdle()
        assertNotNull(answer)
    }
}
