package io.github.fmaruejol.ardoise.core.currency

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Which currency a new group starts on before anyone has chosen one. */
class DefaultCurrencyTest {
    @Test
    fun `takes the currency of the device`() {
        assertEquals("EUR", GroupCurrency.defaultCodeFor(Locale.FRANCE))
        assertEquals("USD", GroupCurrency.defaultCodeFor(Locale.US))
        assertEquals("JPY", GroupCurrency.defaultCodeFor(Locale.JAPAN))
        assertEquals("GBP", GroupCurrency.defaultCodeFor(Locale.UK))
        assertEquals("CHF", GroupCurrency.defaultCodeFor(locale("de", "CH")))
    }

    @Test
    fun `falls back where Spliit has no such currency`() {
        // Argentina's peso is not in the web client's list, so opening the
        // picker on it would show a code that is not one of its options.
        assertEquals(GroupCurrency.FALLBACK_CODE, GroupCurrency.defaultCodeFor(locale("es", "AR")))
    }

    @Test
    fun `falls back where the locale names no country`() {
        // `Currency.getInstance` throws for a language-only locale rather than
        // guessing a country, and so would anything built on it.
        assertEquals(GroupCurrency.FALLBACK_CODE, GroupCurrency.defaultCodeFor(Locale.ENGLISH))
    }

    @Test
    fun `never offers a currency the picker does not list`() {
        // Whatever the device says, the form has to open on one of its own
        // options, and every option has a known scale.
        Locale.getAvailableLocales().forEach { locale ->
            val code = GroupCurrency.defaultCodeFor(locale)
            assertEquals(
                "$locale resolved to $code",
                true,
                code in GroupCurrency.SUPPORTED_CODES,
            )
        }
    }

    /**
     * `Locale.of` is Java 19 and these modules compile against 11, so the
     * builder is what stays true whatever the toolchain becomes.
     */
    private fun locale(language: String, country: String): Locale =
        Locale.Builder().setLanguage(language).setRegion(country).build()
}
