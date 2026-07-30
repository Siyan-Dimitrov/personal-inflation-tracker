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
    suspend fun saveExtraction(receiptId: Long, extraction: ReceiptExtraction)
    suspend fun confirmReceipt(receiptId: Long, items: List<ConfirmedLineItem>)
}
