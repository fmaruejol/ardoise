package io.github.fmaruejol.ardoise.core.currency

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** The symbol a new group stores, which every member is then shown. */
class GroupSymbolTest {
    private val original = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(original)

    @Test
    fun `is upstream's en-US symbol, whatever the device's language`() {
        // The JDK's French symbol for the dollar is "$US".
        Locale.setDefault(Locale.FRANCE)

        assertEquals("$", GroupCurrency.symbolFor("USD"))
        assertEquals("CA$", GroupCurrency.symbolFor("CAD"))
        assertEquals("€", GroupCurrency.symbolFor("EUR"))
    }

    @Test
    fun `is the code where upstream's symbol is the code`() {
        assertEquals("CHF", GroupCurrency.symbolFor("CHF"))
        assertEquals("BGN", GroupCurrency.symbolFor("BGN"))
    }

    @Test
    fun `every supported currency has one the server accepts`() {
        // `groupFormSchema` takes a symbol of 1 to 5 characters.
        GroupCurrency.SUPPORTED_CODES.forEach { code ->
            assertTrue(code, GroupCurrency.symbolFor(code).length in 1..5)
        }
        assertEquals(34, GroupCurrency.SUPPORTED_CODES.size)
    }

    @Test
    fun `an unlisted code stands for itself`() {
        assertEquals("XYZ", GroupCurrency.symbolFor("XYZ"))
    }
}
