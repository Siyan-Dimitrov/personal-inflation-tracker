package com.siyandimitrov.pocketindex.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReceiptExtractionWorkerTest {
    @Test
    fun `work names are stable per receipt and distinct across receipts`() {
        assertEquals(
            ReceiptExtractionWorker.uniqueName(42),
            ReceiptExtractionWorker.uniqueName(42),
        )
        assertNotEquals(
            ReceiptExtractionWorker.uniqueName(42),
            ReceiptExtractionWorker.uniqueName(43),
        )
    }
}
