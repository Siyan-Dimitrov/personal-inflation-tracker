package com.siyandimitrov.pocketindex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Query(
        """
        SELECT r.id, r.purchased_at, r.total_minor, r.currency, r.status,
               m.name AS merchant_name, COUNT(li.id) AS line_item_count
        FROM receipts r
        LEFT JOIN merchants m ON m.id = r.merchant_id
        LEFT JOIN line_items li ON li.receipt_id = r.id
        GROUP BY r.id
        ORDER BY r.purchased_at DESC, r.created_at DESC
        """,
    )
    fun observeReceiptList(): Flow<List<ReceiptListItem>>

    @Query(
        """
        SELECT r.id, r.purchased_at, r.total_minor, r.currency, r.status,
               m.name AS merchant_name, COUNT(li.id) AS line_item_count
        FROM receipts r
        LEFT JOIN merchants m ON m.id = r.merchant_id
        LEFT JOIN line_items li ON li.receipt_id = r.id
        WHERE r.status IN ('PENDING', 'NEEDS_REVIEW')
        GROUP BY r.id
        ORDER BY r.created_at ASC
        """,
    )
    fun observeReviewQueue(): Flow<List<ReceiptListItem>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<ReceiptWithDetails?>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getWithDetails(id: Long): ReceiptWithDetails?

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getById(id: Long): ReceiptEntity?

    @Insert
    suspend fun insert(receipt: ReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLineItems(items: List<LineItemEntity>): List<Long>

    @Query("DELETE FROM line_items WHERE receipt_id = :receiptId AND user_confirmed = 0")
    suspend fun deleteUnconfirmedLineItems(receiptId: Long)

    @Query("DELETE FROM line_items WHERE id = :lineItemId AND receipt_id = :receiptId")
    suspend fun deleteLineItem(receiptId: Long, lineItemId: Long): Int

    @Query(
        """
        UPDATE receipts
        SET merchant_id = :merchantId,
            purchased_at = COALESCE(:purchasedAt, purchased_at),
            subtotal_minor = :subtotalMinor,
            tax_minor = :taxMinor,
            total_minor = :totalMinor,
            ocr_text = :ocrText,
            status = :status
        WHERE id = :receiptId
        """,
    )
    suspend fun applyExtraction(
        receiptId: Long,
        merchantId: Long?,
        purchasedAt: String?,
        subtotalMinor: Long?,
        taxMinor: Long?,
        totalMinor: Long,
        ocrText: String,
        status: ReceiptStatus,
    )

    @Query("UPDATE receipts SET status = :status WHERE id = :receiptId")
    suspend fun updateStatus(receiptId: Long, status: ReceiptStatus)

    @Query(
        """
        UPDATE receipts
        SET merchant_id = :merchantId,
            purchased_at = :purchasedAt,
            subtotal_minor = :subtotalMinor,
            tax_minor = :taxMinor,
            total_minor = :totalMinor
        WHERE id = :receiptId
        """,
    )
    suspend fun updateCorrectableDetails(
        receiptId: Long,
        merchantId: Long?,
        purchasedAt: String,
        subtotalMinor: Long?,
        taxMinor: Long?,
        totalMinor: Long,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLineItem(item: LineItemEntity): Long

    @Query(
        """
        UPDATE line_items
        SET product_id = :productId,
            quantity = :quantity,
            unit_price_minor = :unitPriceMinor,
            line_total_minor = :lineTotalMinor,
            match_confidence = :matchConfidence,
            user_confirmed = :userConfirmed
        WHERE id = :lineItemId
        """,
    )
    suspend fun updateLineItemMatch(
        lineItemId: Long,
        productId: Long?,
        quantity: Double,
        unitPriceMinor: Long,
        lineTotalMinor: Long,
        matchConfidence: Double?,
        userConfirmed: Boolean,
    )

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM receipts WHERE status = :status")
    fun observeCountByStatus(status: ReceiptStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM receipts")
    suspend fun count(): Int

    @Query("UPDATE line_items SET product_id = :targetProductId WHERE product_id = :sourceProductId")
    suspend fun reassignLineItems(sourceProductId: Long, targetProductId: Long): Int

    @Query(
        """
        SELECT c.id AS category_id, c.name AS category_name,
               COALESCE(SUM(li.line_total_minor), 0) AS total_minor
        FROM categories c
        JOIN products p ON p.category_id = c.id
        JOIN line_items li ON li.product_id = p.id
        JOIN receipts r ON r.id = li.receipt_id
        WHERE r.status = 'CONFIRMED'
        GROUP BY c.id
        ORDER BY total_minor DESC
        """,
    )
    fun observeConfirmedSpendByCategory(): Flow<List<CategorySpend>>
}
