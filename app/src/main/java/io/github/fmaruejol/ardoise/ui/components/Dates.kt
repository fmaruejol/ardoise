package io.github.fmaruejol.ardoise.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.fmaruejol.ardoise.ui.currentLocale
import java.time.format.DateTimeFormatter

/**
 * A date formatter in the shape the reader's locale writes dates.
 *
 * It takes an ICU **skeleton**, which fields to show, never a pattern, which
 * is an order as well: `yMMMMd` gets `September 6, 2026` in `en-US` and
 * `2026年9月6日` in `ja-JP`, an order no pattern of ours could produce. `j` is
 * the hour, so the clock is the locale's choice too.
 *
 * Built per composition: a formatter in a static field would keep the language
 * the app started in.
 */
@Composable
fun rememberDateFormatter(skeleton: String): DateTimeFormatter {
    val locale = currentLocale()
    return remember(skeleton, locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

/** Day, month and year: `September 6, 2026` or `6 septembre 2026`. */
const val FullDate = "yMMMMd"

/** Day and month, for a date already known to be near: `Sep 6` or `6 sept.`. */
const val ShortDate = "MMMd"

/** A day heading in the feed: `Sunday, September 6` or `dimanche 6 septembre`. */
const val DayHeading = "EEEEMMMMd"

/** The time of day, on whichever clock the locale keeps: `4:25 PM` or `16:25`. */
const val TimeOfDay = "jm"
