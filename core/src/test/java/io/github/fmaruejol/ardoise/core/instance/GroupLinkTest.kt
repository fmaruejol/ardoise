package io.github.fmaruejol.ardoise.core.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupLinkTest {
    @Test
    fun `reads the id from a group link`() {
        assertEquals("V1StGXR8_Z5jdHi6B", GroupLink.parse("https://spliit.app/groups/V1StGXR8_Z5jdHi6B"))
    }

    @Test
    fun `reads the id from a link to a page inside the group`() {
        // What someone actually copies is usually the page they were looking at.
        assertEquals("abc123", GroupLink.parse("https://spliit.app/groups/abc123/expenses"))
        assertEquals("abc123", GroupLink.parse("https://spliit.app/groups/abc123/balances"))
        assertEquals("abc123", GroupLink.parse("https://spliit.app/groups/abc123/expenses/e1/edit"))
    }

    @Test
    fun `reads the id from a self-hosted link`() {
        assertEquals("abc123", GroupLink.parse("https://spliit.example.com/groups/abc123"))
        assertEquals("abc123", GroupLink.parse("http://192.168.1.10:3000/groups/abc123"))
    }

    @Test
    fun `reads a link with no scheme`() {
        assertEquals("abc123", GroupLink.parse("spliit.app/groups/abc123"))
        assertEquals("abc123", GroupLink.parse("/groups/abc123"))
    }

    @Test
    fun `ignores a query and a fragment`() {
        assertEquals("abc123", GroupLink.parse("https://spliit.app/groups/abc123?utm=x"))
        assertEquals("abc123", GroupLink.parse("https://spliit.app/groups/abc123#top"))
    }

    @Test
    fun `accepts a bare id`() {
        assertEquals("V1StGXR8_Z5jdHi6B-my", GroupLink.parse("V1StGXR8_Z5jdHi6B-my"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        // Pasting from a chat app brings spaces and newlines along.
        assertEquals("abc123", GroupLink.parse("  https://spliit.app/groups/abc123  "))
        assertEquals("abc123", GroupLink.parse("\nabc123\n"))
    }

    @Test
    fun `refuses the group creation page`() {
        // A real URL someone might paste, and "create" is not an id.
        assertNull(GroupLink.parse("https://spliit.app/groups/create"))
    }

    @Test
    fun `refuses a link with no group in it`() {
        assertNull(GroupLink.parse("https://spliit.app/"))
        assertNull(GroupLink.parse("https://spliit.app/groups"))
        assertNull(GroupLink.parse("https://spliit.app/groups/"))
        // Reading the last segment of an unrecognised URL as an id would turn
        // a wrong link into a group that never loads.
        assertNull(GroupLink.parse("https://example.com/something/abc123"))
    }

    @Test
    fun `refuses nothing at all`() {
        assertNull(GroupLink.parse(""))
        assertNull(GroupLink.parse("   "))
    }

    @Test
    fun `refuses text that cannot be an id`() {
        assertNull(GroupLink.parse("my holiday group"))
        assertNull(GroupLink.parse("abc!123"))
        assertNull(GroupLink.parse("a".repeat(65)))
    }
}
