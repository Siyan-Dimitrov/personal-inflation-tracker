package com.siyandimitrov.pocketindex.ui.receipts

import com.siyandimitrov.pocketindex.data.local.ReceiptListItem
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptInboxUiStateTest {
    @Test
    fun `every stored status is discoverable in exactly one inbox section`() {
        val receipts = ReceiptStatus.entries.mapIndexed { index, status ->
            ReceiptListItem(
                id = index.toLong(),
                purchasedAt = "2026-07-${20 + index}",
                totalMinor = 100L + index,
                currency = "GBP",
                status = status,
                readBy = null,
                merchantName = "Merchant $index",
                lineItemCount = index,
            )
        }

        val state = ReceiptInboxUiState(receipts = receipts, isLoading = false)
        val visibleIds = buildList {
            addAll(state.processing.map(ReceiptListItem::id))
            addAll(state.needsReview.map(ReceiptListItem::id))
            addAll(state.confirmed.map(ReceiptListItem::id))
            addAll(state.failed.map(ReceiptListItem::id))
        }

        assertEquals(receipts.map(ReceiptListItem::id).sorted(), visibleIds.sorted())
        assertEquals(3, state.queueCount)
    }
}
