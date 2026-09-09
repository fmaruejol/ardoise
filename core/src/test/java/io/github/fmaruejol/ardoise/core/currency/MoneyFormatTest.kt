package io.github.fmaruejol.ardoise.core.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

class MoneyFormatTest {
    private val us = Locale.US
    private val de = Locale.GERMANY

    /** Non-breaking space, which is what both CLDR and `Intl` put before a trailing symbol. */
    private val nbsp = "\u00A0"

    private val custom = GroupCurrency.of(currencyCode = null, currencySymbol = "CUR")
    private val euro = GroupCurrency.of(currencyCode = "EUR", currencySymbol = "€")
    private val yen = GroupCurrency.of(currencyCode = "JPY", currencySymbol = "¥")
    private val forint = GroupCurrency.of(currencyCode = "HUF", currencySymbol = "Ft")
    private val dollar = GroupCurrency.of(currencyCode = "USD", currencySymbol = "$")

    // --- scale -------------------------------------------------------------

    @Test
    fun `resolves the scale from the ISO code, not from the symbol`() {
        assertEquals(2, GroupCurrency.of("EUR", "€").decimalDigits)
        assertEquals(0, GroupCurrency.of("JPY", "€").decimalDigits)
    }

    @Test
    fun `follows Spliit's currency data where it disagrees with the JDK`() {
        // The JDK says these have two decimals.
        for (code in listOf("HUF", "COP", "IDR")) {
            assertEquals(code, 2, Currency.getInstance(code).defaultFractionDigits)
            assertEquals(code, 0, GroupCurrency.decimalDigitsFor(code))
        }
    }

    @Test
    fun `treats every zero-decimal currency as having no minor unit`() {
        for (code in GroupCurrency.ZERO_DECIMAL_CODES) {
            assertEquals(code, 0, GroupCurrency.decimalDigitsFor(code))
        }
        val twoDecimalCodes = GroupCurrency.SUPPORTED_CODES - GroupCurrency.ZERO_DECIMAL_CODES
        for (code in twoDecimalCodes) {
            assertEquals(code, 2, GroupCurrency.decimalDigitsFor(code))
        }
        assertEquals(34, GroupCurrency.SUPPORTED_CODES.size)
    }

    @Test
    fun `falls back to two decimals for a missing or unknown code`() {
        assertEquals(2, GroupCurrency.decimalDigitsFor(null))
        assertEquals(2, GroupCurrency.decimalDigitsFor(""))
        assertEquals(2, GroupCurrency.decimalDigitsFor("   "))
        // A code a self-hosted instance might store. The web renders unknown
        // codes at two decimals, so this has to as well.
        assertEquals(2, GroupCurrency.decimalDigitsFor("XYZ"))
    }

    @Test
    fun `normalises the code`() {
        assertEquals("EUR", GroupCurrency.of(" eur ", "€").code)
        assertNull(GroupCurrency.of("", "$").code)
    }

    // --- format, custom symbol ---------------------------------------------

    /** Ported from `formatCurrency` in `src/lib/utils.test.ts`, expected values included. */
    @Test
    fun `formats a custom symbol where the locale puts a currency symbol`() {
        assertEquals("CUR1.23", custom.format(123, us))
        assertEquals("CUR1.00", custom.format(100, us))
        assertEquals("CUR10,000.00", custom.format(1_000_000, us))

        assertEquals("1,23${nbsp}CUR", custom.format(123, de))
        assertEquals("1,00${nbsp}CUR", custom.format(100, de))
        assertEquals("10.000,00${nbsp}CUR", custom.format(1_000_000, de))
    }

    // --- format, ISO code --------------------------------------------------

    @Test
    fun `formats a currency with an ISO code`() {
        assertEquals("€1.23", euro.format(123, us))
        assertEquals("1,23$nbsp€", euro.format(123, de))
    }

    @Test
    fun `formats a currency with no minor unit without decimals`() {
        assertEquals("¥1,234", yen.format(1234, us))
    }

    @Test
    fun `formats forint with no decimals despite the JDK default`() {
        // Would be "HUF12.34" if the JDK's own fraction digits won.
        assertEquals("HUF1,234", forint.format(1234, us))
    }

    @Test
    fun `formats a negative amount`() {
        assertEquals("-€1.23", euro.format(-123, us))
    }

    @Test
    fun `formats zero`() {
        assertEquals("€0.00", euro.format(0, us))
        assertEquals("¥0", yen.format(0, us))
    }

    // --- major and minor units ---------------------------------------------

    @Test
    fun `converts to major units exactly`() {
        assertEquals(BigDecimal("12.34"), euro.toMajorUnits(1234))
        assertEquals(BigDecimal("-0.05"), euro.toMajorUnits(-5))
        assertEquals(BigDecimal("1234"), yen.toMajorUnits(1234))
    }

    @Test
    fun `converts back to minor units`() {
        assertEquals(1234L, euro.toMinorUnits(BigDecimal("12.34")))
        assertEquals(1234L, yen.toMinorUnits(BigDecimal("1234")))
        // Rounds to the currency's scale rather than truncating.
        assertEquals(1235L, euro.toMinorUnits(BigDecimal("12.345")))
    }

    @Test
    fun `round-trips every amount through major units`() {
        for (currency in listOf(euro, yen, custom)) {
            for (amount in listOf(0L, 1L, -1L, 5L, 99L, 100L, -1234L, 1_000_000_00L)) {
                assertEquals(
                    "$currency $amount",
                    amount,
                    currency.toMinorUnits(currency.toMajorUnits(amount)),
                )
            }
        }
    }

    @Test
    fun `formats a plain decimal for a text field`() {
        assertEquals("12.34", euro.formatPlain(1234))
        assertEquals("-0.05", euro.formatPlain(-5))
        assertEquals("0.00", euro.formatPlain(0))
        assertEquals("1234", yen.formatPlain(1234))
    }

    // --- parse -------------------------------------------------------------

    @Test
    fun `parses an amount in the locale the user is typing in`() {
        assertEquals(1234L, euro.parse("12.34", us))
        assertEquals(1234L, euro.parse("12,34", de))
        assertEquals(1_000_000L, euro.parse("10,000.00", us))
        assertEquals(1_000_000L, euro.parse("10.000,00", de))
    }

    @Test
    fun `parses into a currency with no minor unit`() {
        assertEquals(1234L, yen.parse("1,234", us))
        // Anything finer than the currency's scale rounds to it.
        assertEquals(1235L, yen.parse("1234.6", us))
    }

    @Test
    fun `parses a negative amount and surrounding whitespace`() {
        assertEquals(-1234L, euro.parse("-12.34", us))
        assertEquals(1234L, euro.parse("  12.34  ", us))
    }

    @Test
    fun `refuses anything that is not just a number`() {
        assertNull(euro.parse("", us))
        assertNull(euro.parse("   ", us))
        assertNull(euro.parse("abc", us))
        // Must not silently keep the leading digits.
        assertNull(euro.parse("12.34x", us))
        assertNull(euro.parse("12.34 euros", us))
    }

    @Test
    fun `refuses an amount too large to hold`() {
        assertNull(euro.parse("999999999999999999999", us))
    }

    @Test
    fun `reads a separator the way the given locale writes it`() {
        // The same text means different amounts in the two locales, which is
        // exactly why parse takes one rather than guessing.
        assertEquals(123_400L, euro.parse("1,234", us))
        assertEquals(123L, euro.parse("1,234", de))
    }

    @Test
    fun `round-trips through the plain form`() {
        for (amount in listOf(0L, 1L, -1L, 1234L, -98765L)) {
            assertEquals(amount, euro.parse(euro.formatPlain(amount), us))
        }
    }

    // --- the signed form the expense feed uses -----------------------------

    @Test
    fun `signs a gain and a loss explicitly`() {
        assertEquals("+$4.20", dollar.formatSigned(420, us))
        assertEquals("\u2212$4.20", dollar.formatSigned(-420, us))
    }

    @Test
    fun `leaves zero unsigned`() {
        assertEquals("$0.00", dollar.formatSigned(0, us))
    }

    @Test
    fun `uses a minus sign rather than whatever the locale would do`() {
        // Some locales bracket a negative currency amount and others move the
        // sign about; the feed needs one glyph at the start of the line.
        val formatted = euro.formatSigned(-3500, de)
        assertEquals(true, formatted.startsWith("\u2212"))
        assertEquals(false, formatted.contains("("))
        assertEquals(euro.format(3500, de), formatted.removePrefix("\u2212"))
    }

    @Test
    fun `signs a currency with no minor unit`() {
        assertEquals("\u2212" + yen.format(1234, us), yen.formatSigned(-1234, us))
    }

    // --- what the user types, in their own notation ------------------------

    @Test
    fun `seeds a field in the notation that locale parses`() {
        // The form fills a field in and reads it back. If the two disagree,
        // the app cannot save an expense it opened itself.
        listOf(Locale.US, Locale.FRANCE, Locale.GERMANY, Locale.JAPAN).forEach { locale ->
            val text = euro.formatPlain(1234, locale)
            assertEquals("round trip in $locale (was \"$text\")", 1234L, euro.parse(text, locale))
        }
    }

    @Test
    fun `writes the decimal separator the locale uses`() {
        assertEquals("12.34", euro.formatPlain(1234, Locale.US))
        assertEquals("12,34", euro.formatPlain(1234, Locale.FRANCE))
        assertEquals("12,34", euro.formatPlain(1234, Locale.GERMANY))
    }

    @Test
    fun `groups nothing, whatever the locale would do to a number that size`() {
        // A field the user edits is not a place for thousands separators: the
        // next parse has to take back exactly what was put in.
        listOf(Locale.US, Locale.FRANCE, Locale.GERMANY).forEach { locale ->
            val text = euro.formatPlain(123456789, locale)
            assertEquals("no grouping in $locale", 123456789L, euro.parse(text, locale))
        }
    }
}
