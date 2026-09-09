package io.github.fmaruejol.ardoise.core.currency

import java.math.BigDecimal
import java.util.Locale

/*
 * An expense paid in a currency the group is not counted in.
 *
 * The rate reads "1 original = rate group", upstream's direction
 * (`originalAmount * rate`), and the one the stored value is in.
 */

/**
 * The amount in the group's currency, or null when [rate] cannot convert.
 * Exact decimals: the two currencies can have different scales.
 */
fun convertToGroupCurrency(
    originalAmount: Long,
    originalCurrency: GroupCurrency,
    rate: BigDecimal,
    groupCurrency: GroupCurrency,
): Long? {
    if (rate.signum() <= 0) return null
    val major = originalCurrency.toMajorUnits(originalAmount).multiply(rate)
    return groupCurrency.toMinorUnits(major)
}

/** A rate as typed in [locale], or null unless it is a positive decimal. */
fun parseConversionRate(text: String, locale: Locale = Locale.getDefault()): BigDecimal? =
    parseDecimal(text, locale)?.takeIf { it.signum() > 0 }

/** The rate as the server stores it: a plain decimal, since it goes out as a JSON number. */
fun BigDecimal.asStoredRate(): String = stripTrailingZeros().toPlainString()
