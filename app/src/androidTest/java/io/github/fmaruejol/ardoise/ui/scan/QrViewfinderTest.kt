package io.github.fmaruejol.ardoise.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The viewfinder against a real camera.
 *
 * It is here because of what the build does: `camera-video` is excluded from
 * `camera-view`, so `CameraController` is present with its own dependencies
 * missing. Nothing on the JVM can vouch for that. `NoClassDefFoundError` is an
 * `Error`, so the viewfinder's own `catch (e: Exception)` does not turn it into
 * the "camera failed" message either: it reaches here as a crash, and only on
 * a device.
 *
 * The camera really binding is the other half. `onTorchAvailable` is the
 * signal, since it is called from the `also` on a returned `bindToLifecycle`
 * and so cannot run unless the whole path worked.
 *
 * **Deliberately not `createAndroidComposeRule`.** Its clock drives the
 * composition's effects, and the bind here is resumed by CameraX's own
 * executor rather than by anything the test advances, so `waitUntil` waits on
 * work its clock is not running. A real activity gives the composition the
 * frame clock the app has.
 */
@RunWith(AndroidJUnit4::class)
class QrViewfinderTest {
    @get:Rule(order = 0)
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val activity = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun cameraVideoIsNotOnTheClasspath() {
        // Pins the exclusion itself: without this a green suite would prove
        // only that the app works with the library still in it.
        val missing = try {
            Class.forName("androidx.camera.video.VideoCapture")
            false
        } catch (e: ClassNotFoundException) {
            true
        }

        assertTrue("camera-video is packaged; the exclusion is not in effect", missing)
    }

    @Test
    fun bindsACameraWithoutIt() {
        assumeTrue("no camera on this device", hasCamera())
        val bound = CountDownLatch(1)

        activity.scenario.onActivity {
            it.setContent {
                ArdoiseTheme(darkTheme = false) {
                    QrViewfinder(
                        onScan = {},
                        modifier = Modifier.fillMaxSize(),
                        onTorchAvailable = { bound.countDown() },
                    )
                }
            }
        }

        // Opening an emulated camera is slow, and this waits on the device
        // rather than on a clock, so it is generous.
        assertTrue(
            "the camera never bound; a missing class would have crashed instead",
            bound.await(30, TimeUnit.SECONDS),
        )
    }

    private fun hasCamera(): Boolean = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .packageManager
        .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}
