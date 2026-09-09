package io.github.fmaruejol.ardoise.core.currency

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/*
 * Amounts cross this boundary as `Long` minor units and convert through
 * BigDecimal, never a `Double`.
 */

/** The amount in major units, exactly. */
fun GroupCurrency.toMajorUnits(amountMinorUnits: Long): BigDecimal =
    BigDecimal.valueOf(amountMinorUnits, decimalDigits)

/** Back to minor units, rounding half away from zero. */
fun GroupCurrency.toMinorUnits(majorUnits: BigDecimal): Long =
    majorUnits.movePointRight(decimalDigits).setScale(0, RoundingMode.HALF_UP).longValueExact()

/**
 * The amount as the locale writes money. A group with only a custom symbol is
 * formatted as euros and has the sign swapped, which is what upstream does.
 */
fun GroupCurrency.format(
    amountMinorUnits: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val isoCurrency = code?.let(::javaCurrencyOrNull)
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = isoCurrency ?: PLACEHOLDER_CURRENCY
    // Must follow the currency: setting that resets the fraction digits.
    format.minimumFractionDigits = decimalDigits
    format.maximumFractionDigits = decimalDigits

    val formatted = format.format(toMajorUnits(amountMinorUnits))
    if (isoCurrency != null) return formatted

    val substituted = formatted.replaceFirst(PLACEHOLDER_SYMBOL, symbol)
    // Some locales spell the placeholder out rather than using the sign.
    return if (substituted != formatted) substituted else formatted.replaceFirst(PLACEHOLDER_CODE, symbol)
}

/**
 * The amount with its sign, `+€6.24`, `−€35.00`. U+2212 rather than a
 * hyphen: it is the width of a digit, so a column of them lines up.
 */
fun GroupCurrency.formatSigned(
    amountMinorUnits: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val magnitude = format(abs(amountMinorUnits), locale)
    return when {
        amountMinorUnits > 0 -> "+$magnitude"
        amountMinorUnits < 0 -> "−$magnitude"
        else -> magnitude
    }
}

/**
 * The amount for a field the user edits: no symbol, no grouping, and this
 * locale's decimal separator, [parse] reads it back in the same locale.
 */
fun GroupCurrency.formatPlain(
    amountMinorUnits: Long,
    locale: Locale = Locale.getDefault(),
): String = formatForEditing(toMajorUnits(amountMinorUnits), decimalDigits, locale)

/**
 * A plain decimal as [locale] writes it. [scale] fixes the decimals shown;
 * null keeps whatever the value has.
 */
fun formatForEditing(
    value: BigDecimal,
    scale: Int? = null,
    locale: Locale = Locale.getDefault(),
): String {
    val format = NumberFormat.getNumberInstance(locale) as? DecimalFormat
        ?: return value.toPlainString()
    format.isGroupingUsed = false
    val shown = if (scale == null) value.stripTrailingZeros() else value
    format.minimumFractionDigits = scale ?: 0
    format.maximumFractionDigits = scale ?: shown.scale().coerceAtLeast(0)
    return format.format(shown)
}

/** Reads a decimal typed in [locale], or null if it is not one. */
fun parseDecimal(text: String, locale: Locale = Locale.getDefault()): BigDecimal? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    val format = NumberFormat.getNumberInstance(locale) as? DecimalFormat ?: return null
    format.isParseBigDecimal = true

    val position = ParsePosition(0)
    val parsed = format.parse(trimmed, position) as? BigDecimal ?: return null
    return parsed.takeIf { position.index == trimmed.length }
}

/**
 * Reads an amount typed in [locale], in minor units, or null. Strict about
 * separators: `1,234` is a thousand in `en-US` and 1.234 in `de-DE`.
 */
fun GroupCurrency.parse(text: String, locale: Locale = Locale.getDefault()): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    // Reject trailing junk: "12.34x" must not silently become 1234.
    val parsed = parseDecimal(trimmed, locale) ?: return null

    return try {
        toMinorUnits(parsed)
    } catch (e: ArithmeticException) {
        // Larger than a Long can hold; the server caps amounts far below this.
        null
    }
}

private fun javaCurrencyOrNull(code: String): Currency? = try {
    Currency.getInstance(code)
} catch (e: IllegalArgumentException) {
    // A code the JDK does not know. Fall back to symbol substitution rather
    // than failing to render an amount at all.
    null
}

/**
 * Euros stand in for a group that has no ISO code, so the locale decides where
 * the symbol goes; the sign is then replaced with the group's own symbol.
 */
private const val PLACEHOLDER_CODE = "EUR"
private const val PLACEHOLDER_SYMBOL = "€"
private val PLACEHOLDER_CURRENCY: Currency = Currency.getInstance(PLACEHOLDER_CODE)
