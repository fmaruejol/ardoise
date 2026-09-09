package io.github.fmaruejol.ardoise.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanGateTest {
    @Test
    fun `reports the first readable code`() {
        val gate = ScanGate()

        assertEquals("https://spliit.app/groups/abc123", gate.accept(listOf("https://spliit.app/groups/abc123")))
    }

    @Test
    fun `ignores frames with nothing in them`() {
        val gate = ScanGate()

        assertNull(gate.accept(emptyList()))
        // A code the reader saw but could not read is not something to act on.
        assertNull(gate.accept(listOf("")))
        assertEquals(false, gate.isClosed)
    }

    @Test
    fun `reports a code once however long it stays in view`() {
        val gate = ScanGate()
        val code = "https://spliit.app/groups/abc123"

        assertEquals(code, gate.accept(listOf(code)))
        // The camera keeps handing over frames of the same code. Acting on
        // them all would add the group over and over.
        assertNull(gate.accept(listOf(code)))
        assertNull(gate.accept(listOf(code)))
        assertEquals(true, gate.isClosed)
    }
}
