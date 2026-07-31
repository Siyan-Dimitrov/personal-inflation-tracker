package com.siyandimitrov.pocketindex.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptReconciliationTest {
    @Test
    fun `accepts differences up to two pence`() {
        assertTrue(
            receiptReconciles(
                reviewedSubtotal = 998,
                subtotalMinor = 1_000,
                taxMinor = 200,
                totalMinor = 1_200,
            ),
        )
        assertTrue(
            receiptReconciles(
                reviewedSubtotal = 1_002,
                subtotalMinor = 1_000,
                taxMinor = 200,
                totalMinor = 1_202,
            ),
        )
    }

    @Test
    fun `rejects line or header differences over two pence`() {
        assertFalse(
            receiptReconciles(
                reviewedSubtotal = 997,
                subtotalMinor = 1_000,
                taxMinor = 200,
                totalMinor = 1_200,
            ),
        )
        assertFalse(
            receiptReconciles(
                reviewedSubtotal = 1_000,
                subtotalMinor = 1_000,
                taxMinor = 200,
                totalMinor = 1_203,
            ),
        )
    }

    @Test
    fun `derives subtotal when it is not printed`() {
        assertTrue(
            receiptReconciles(
                reviewedSubtotal = 850,
                subtotalMinor = null,
                taxMinor = 150,
                totalMinor = 1_000,
            ),
        )
    }
}
