package com.siyandimitrov.pocketindex.ui.receipts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptInputTest {
    @Test
    fun `formats signed minor units as editable money`() {
        assertEquals("-0.50", (-50L).toMoneyInput())
        assertEquals("-12.05", (-1_205L).toMoneyInput())
        assertEquals("7.65", 765L.toMoneyInput())
    }

    @Test
    fun `money input converts exactly to minor units`() {
        assertEquals(0L, "0".toMinorUnits())
        assertEquals(5L, "0.05".toMinorUnits())
        assertEquals(120L, "1.2".toMinorUnits())
        assertEquals(123L, "1,23".toMinorUnits())
        assertNull("1.234".toMinorUnits())
        assertNull("-1.00".toMinorUnits())
    }

    @Test
    fun `date validation rejects impossible calendar dates`() {
        assertTrue("2026-07-31".isValidIsoDate())
        assertFalse("2026-02-30".isValidIsoDate())
        assertFalse("31/07/2026".isValidIsoDate())
    }
}
