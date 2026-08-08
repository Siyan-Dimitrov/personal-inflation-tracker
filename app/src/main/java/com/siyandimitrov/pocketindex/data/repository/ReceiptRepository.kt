package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.CategorySpend
import com.siyandimitrov.pocketindex.data.local.ReceiptListItem
import com.siyandimitrov.pocketindex.data.local.ReceiptWithDetails
import kotlinx.coroutines.flow.Flow

interface ReceiptRepository {
    fun observeReceipts(): Flow<List<ReceiptListItem>>
    fun observeReviewQueue(): Flow<List<ReceiptListItem>>
    fun observeReceipt(receiptId: Long): Flow<ReceiptWithDetails?>
    fun observeConfirmedSpendByCategory(): Flow<List<CategorySpend>>
    suspend fun createPendingReceipt(receipt: NewReceipt): Long
    suspend fun createManualReceipt(receipt: NewManualReceipt): Long
    suspend fun addManualLineItem(receiptId: Long, item: NewManualLineItem): Long
    suspend fun saveExtraction(receiptId: Long, extraction: ReceiptExtraction)
    suspend fun confirmReceipt(
        receiptId: Long,
        correction: ReceiptCorrection,
        items: List<ConfirmedLineItem>,
    )

    /**
     * Deletes a receipt together with its line items and any price observations they produced.
     *
     * Returns the stored image path so the caller can remove the private image file, or null
     * when the receipt did not exist.
     */
    suspend fun deleteReceipt(receiptId: Long): String?
}
