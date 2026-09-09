package io.github.fmaruejol.ardoise.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.fmaruejol.ardoise.data.AppLanguage
import java.util.Locale

/**
 * Draws [content] in the language the user chose. Android's per-app language
 * is API 33 and this app runs from 26, so it is applied by hand, in two halves:
 *
 * - **The composition reads localised resources**, a [ContextWrapper] around
 *   the activity, not the bare context `createConfigurationContext` returns,
 *   because screens start activities and bind a camera through this one.
 * - **The process default locale follows**, because `:core` formats money with
 *   `Locale.getDefault()`. Without it the app reads French and writes `€30.00`.
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = language.locale()

    // Keyed on the configuration too, so a rotation is not frozen at whatever
    // the first composition saw.
    val localized = remember(context, configuration, locale) {
        // Before the content composes, not in a `SideEffect` after it: the
        // formatters read this while they are being built.
        Locale.setDefault(locale)
        LocalizedContext(context, Configuration(configuration).apply { setLocale(locale) })
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}

/** The locale to draw in: the chosen one, or the device's own. */
private fun AppLanguage.locale(): Locale = when (val tag = tag) {
    // The system's configuration, not `Locale.getDefault()`, which this file
    // has itself been changing.
    null -> Resources.getSystem().configuration.locales[0]

    else -> Locale.forLanguageTag(tag)
}

/**
 * The activity, with resources in another language, and still the activity
 * underneath, which `startActivity` and the permission requests reach by
 * walking the wrappers.
 */
private class LocalizedContext(base: Context, configuration: Configuration) : ContextWrapper(base) {
    private val localized = base.createConfigurationContext(configuration).resources

    override fun getResources(): Resources = localized
}
