package io.github.fmaruejol.ardoise

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fmaruejol.ardoise.ui.AppViewModel
import io.github.fmaruejol.ardoise.ui.ArdoiseApp
import io.github.fmaruejol.ardoise.ui.theme.ArdoiseTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AppViewModel = koinViewModel()

            // Nothing is drawn until the choice has been read: a frame of the
            // wrong one is a flash of white on a phone that asked for dark.
            val theme = viewModel.theme.collectAsStateWithLifecycle().value ?: return@setContent
            val dark = theme.isDark(isSystemInDarkTheme())

            // The bars follow the app, not the system: a light app on a dark
            // phone needs dark icons above it. `auto` takes the answer from us,
            // and the scrims protect the icons on API 26.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        LightScrim,
                        DarkScrim,
                    ) { dark },
                )
                onDispose {}
            }

            ArdoiseTheme(darkTheme = dark) {
                // The same instance the theme was read from: two would be two
                // outbox flushes on every network change.
                ArdoiseApp(viewModel = viewModel)
            }
        }
    }
}

/** androidx's own defaults, for the versions that cannot tint bar icons. */
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
