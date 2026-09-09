package io.github.fmaruejol.ardoise.core.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class ConversionTest {
    private val euro = GroupCurrency.of("EUR", "€")
    private val real = GroupCurrency.of("BRL", "R$")
    private val yen = GroupCurrency.of("JPY", "¥")

    @Test
    fun `converts the way upstream does`() {
        // 540.00 BRL at 1 BRL = 0.17857 EUR.
        val converted = convertToGroupCurrency(
            originalAmount = 54_000,
            originalCurrency = real,
            rate = BigDecimal("0.17857"),
            groupCurrency = euro,
        )

        assertEquals(9643L, converted)
    }

    @Test
    fun `crosses currencies with different scales`() {
        // ¥54,000 has no minor unit at all, so the trip through major units is
        // the whole of what makes the two amounts comparable.
        val converted = convertToGroupCurrency(
            originalAmount = 54_000,
            originalCurrency = yen,
            rate = BigDecimal("0.0058"),
            groupCurrency = euro,
        )

        assertEquals(31_320L, converted)
    }

    @Test
    fun `and the other way, into a currency with none`() {
        val converted = convertToGroupCurrency(
            originalAmount = 10_000,
            originalCurrency = euro,
            rate = BigDecimal("172.5"),
            groupCurrency = yen,
        )

        // 100.00 EUR at 172.5 yen each, rounded to whole yen.
        assertEquals(17_250L, converted)
    }

    @Test
    fun `rounds half away from zero, at the group's scale`() {
        // 1.005 EUR exactly: a halfway value at one digit finer than the
        // currency has, which is the only place the rounding rule shows.
        val converted = convertToGroupCurrency(
            originalAmount = 100,
            originalCurrency = GroupCurrency.of("USD", "$"),
            rate = BigDecimal("1.005"),
            groupCurrency = euro,
        )

        assertEquals(101L, converted)
    }

    @Test
    fun `a rate has to be a positive number`() {
        val rate = BigDecimal("-1.5")
        assertNull(convertToGroupCurrency(54_000, real, rate, euro))
        assertNull(convertToGroupCurrency(54_000, real, BigDecimal.ZERO, euro))
    }

    @Test
    fun `reads a rate in the reader's own notation`() {
        assertEquals(BigDecimal("0.17857"), parseConversionRate("0.17857", Locale.US))
        assertEquals(BigDecimal("0.17857"), parseConversionRate("0,17857", Locale.FRANCE))
        // German writes a decimal comma too, so a dot there is a grouping
        // separator: `0.17857` reads as seventeen thousand.
        assertEquals(BigDecimal("0.17857"), parseConversionRate("0,17857", Locale.GERMANY))
    }

    @Test
    fun `refuses what is not a rate`() {
        assertNull(parseConversionRate("", Locale.US))
        assertNull(parseConversionRate("abc", Locale.US))
        assertNull(parseConversionRate("0", Locale.US))
        assertNull(parseConversionRate("-2", Locale.US))
        assertNull(parseConversionRate("1.5x", Locale.US))
    }

    @Test
    fun `stores a rate as a plain decimal, whatever the reader types`() {
        val typed = parseConversionRate("5,5999", Locale.FRANCE)!!

        // It goes out as a JSON number: a comma would be a syntax error.
        assertEquals("5.5999", typed.asStoredRate())
    }

    @Test
    fun `a stored rate keeps its precision`() {
        val rate = BigDecimal("0.178574617")
        assertEquals("0.178574617", rate.asStoredRate())
        // And converting with it is exact rather than nearly.
        assertEquals(9643L, convertToGroupCurrency(54_000, real, rate, euro))
    }
}
