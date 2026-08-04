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
    fun `coupons price cuts and refunds default to excluded from the index`() {
        assertTrue(isNonProductLine("15% off coupon -1.85", -185L))
        assertTrue(isNonProductLine("Price Cut -0.60", -60L))
        assertTrue(isNonProductLine("Lidl Plus voucher", -100L))
        assertTrue(isNonProductLine("DEPOSIT REFUND", -20L))
        assertFalse(isNonProductLine("Gouda Slices 2.69 A", 269L))
        assertFalse(isNonProductLine("Nectarines 1kg 0080388 2.39 A", 239L))
    }

    @Test
    fun `date validation rejects impossible calendar dates`() {
        assertTrue("2026-07-31".isValidIsoDate())
        assertFalse("2026-02-30".isValidIsoDate())
        assertFalse("31/07/2026".isValidIsoDate())
    }
}
