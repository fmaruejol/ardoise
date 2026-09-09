package io.github.fmaruejol.ardoise.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale

/**
 * The locale the app is read in. Compose's own rather than
 * `Locale.getDefault()`, because this one recomposes what was drawn from it.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
