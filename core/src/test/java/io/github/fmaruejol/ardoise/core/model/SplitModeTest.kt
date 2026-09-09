package io.github.fmaruejol.ardoise.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The share representation table, which used to be only a comment on [PaidFor]
 * and was re-derived by every caller.
 */
class SplitModeTest {
    @Test
    fun `shares and percentages are stored scaled by a hundred`() {
        assertEquals(100L, SplitMode.BY_SHARES.shareScale)
        assertEquals(100L, SplitMode.BY_PERCENTAGE.shareScale)
    }

    @Test
    fun `amounts are minor units already, and evenly ignores the value`() {
        assertEquals(1L, SplitMode.BY_AMOUNT.shareScale)
        assertEquals(1L, SplitMode.EVENLY.shareScale)
    }

    @Test
    fun `by amount must add up to the expense`() {
        assertEquals(4_250L, SplitMode.BY_AMOUNT.requiredTotal(4_250))
    }

    @Test
    fun `by percentage must add up to a hundred percent, whatever the amount`() {
        assertEquals(10_000L, SplitMode.BY_PERCENTAGE.requiredTotal(4_250))
        assertEquals(10_000L, SplitMode.BY_PERCENTAGE.requiredTotal(1))
        assertEquals(SplitMode.PERCENT_TOTAL, SplitMode.BY_PERCENTAGE.requiredTotal(0))
    }

    @Test
    fun `evenly and by shares constrain nothing`() {
        // Any share counts are valid, and EVENLY ignores the values entirely,
        // so there is no sum for a form to check against.
        assertNull(SplitMode.EVENLY.requiredTotal(4_250))
        assertNull(SplitMode.BY_SHARES.requiredTotal(4_250))
    }

    // --- the decoder that must not guess ------------------------------------

    @Test
    fun `every mode round-trips through its wire name`() {
        SplitMode.entries.forEach { assertEquals(it, SplitMode.fromWire(it.name)) }
    }

    /**
     * The whole point of the null: a mode this build has never heard of is a
     * mode whose share scale is unknown, so splitting it any particular way is
     * a guess at somebody's money.
     */
    @Test
    fun `an unknown mode is null rather than a guess`() {
        assertNull(SplitMode.fromWire("BY_ADJUSTMENT"))
        assertNull(SplitMode.fromWire(""))
        assertNull(SplitMode.fromWire("evenly"))
    }

    @Test
    fun `an unknown recurrence rule degrades to none, because it only affects display`() {
        assertEquals(RecurrenceRule.NONE, RecurrenceRule.fromWire("HOURLY"))
        assertEquals(RecurrenceRule.NONE, RecurrenceRule.fromWire(null))
        assertEquals(RecurrenceRule.WEEKLY, RecurrenceRule.fromWire("WEEKLY"))
    }
}
