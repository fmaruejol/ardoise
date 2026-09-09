package io.github.fmaruejol.ardoise.core.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlTest {
    private fun valid(input: String): String =
        (BaseUrl.normalise(input) as BaseUrl.Result.Valid).baseUrl

    private fun reason(input: String): BaseUrl.Reason =
        (BaseUrl.normalise(input) as BaseUrl.Result.Invalid).reason

    @Test
    fun `assumes https when no scheme is typed`() {
        assertEquals("https://spliit.app/", valid("spliit.app"))
    }

    @Test
    fun `always ends in a trailing slash`() {
        // The API layer appends "api/trpc" to this, so a missing slash would
        // produce "…appapi/trpc".
        assertEquals("https://spliit.app/", valid("https://spliit.app"))
        assertEquals("https://spliit.app/", valid("https://spliit.app/"))
        assertEquals("https://spliit.app/", valid("https://spliit.app///"))
    }

    @Test
    fun `lowercases the scheme and host but keeps the path`() {
        assertEquals("https://spliit.example.com/Spliit/", valid("HTTPS://Spliit.Example.COM/Spliit"))
    }

    @Test
    fun `keeps a port and a sub-path`() {
        assertEquals("http://192.168.1.10:3000/", valid("http://192.168.1.10:3000"))
        assertEquals("https://example.com/apps/spliit/", valid("example.com/apps/spliit/"))
    }

    @Test
    fun `allows http for a self-hosted instance without a certificate`() {
        assertEquals("http://spliit.local/", valid("http://spliit.local"))
    }

    @Test
    fun `drops a query and a fragment`() {
        assertEquals("https://spliit.app/", valid("https://spliit.app/?utm=x"))
        assertEquals("https://spliit.app/", valid("https://spliit.app/#top"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("https://spliit.app/", valid("  https://spliit.app  "))
    }

    @Test
    fun `rejects nothing at all`() {
        assertEquals(BaseUrl.Reason.Empty, reason(""))
        assertEquals(BaseUrl.Reason.Empty, reason("   "))
    }

    @Test
    fun `rejects an address with nothing to connect to`() {
        assertEquals(BaseUrl.Reason.MissingHost, reason("https://"))
        assertEquals(BaseUrl.Reason.MissingHost, reason("https:///path"))
    }

    @Test
    fun `rejects a scheme that is not http`() {
        assertEquals(BaseUrl.Reason.UnsupportedScheme, reason("ftp://spliit.app"))
        assertEquals(BaseUrl.Reason.UnsupportedScheme, reason("file:///etc/passwd"))
    }

    @Test
    fun `rejects something that is not a url`() {
        assertEquals(BaseUrl.Reason.Malformed, reason("my spliit server"))
        assertEquals(BaseUrl.Reason.Malformed, reason("https://spl iit.app"))
    }

    @Test
    fun `recognises the project's own instance however it was typed`() {
        assertTrue(BaseUrl.isCloud("spliit.app"))
        assertTrue(BaseUrl.isCloud("https://spliit.app"))
        assertTrue(BaseUrl.isCloud("HTTPS://Spliit.App/"))
        assertFalse(BaseUrl.isCloud("https://spliit.example.com/"))
        assertFalse(BaseUrl.isCloud("not a url"))
    }

    @Test
    fun `normalising an already normalised url changes nothing`() {
        assertEquals(BaseUrl.CLOUD, valid(BaseUrl.CLOUD))
    }
}
