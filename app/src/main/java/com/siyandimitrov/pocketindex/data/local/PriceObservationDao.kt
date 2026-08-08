package com.siyandimitrov.pocketindex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceObservationDao {
    @Query("SELECT * FROM price_observations ORDER BY observed_at ASC, id ASC")
    fun observeAll(): Flow<List<PriceObservationEntity>>

    @Query(
        """
        SELECT price_observations.*, line_items.quantity AS receipt_quantity
        FROM price_observations
        LEFT JOIN line_items
            ON line_items.id = price_observations.receipt_line_item_id
        ORDER BY price_observations.observed_at ASC, price_observations.id ASC
        """,
    )
    fun observeAllForInflation(): Flow<List<AnalyticalPriceObservation>>

    @Query(
        """
        SELECT * FROM price_observations
        WHERE product_id = :productId
        ORDER BY observed_at ASC, id ASC
        """,
    )
    fun observeForProduct(productId: Long): Flow<List<PriceObservationEntity>>

    @Query(
        """
        SELECT * FROM price_observations
        WHERE product_id = :productId AND observed_at <= :onOrBefore
        ORDER BY observed_at DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun latestForProduct(productId: Long, onOrBefore: String): PriceObservationEntity?

    @Query(
        """
        SELECT * FROM price_observations
        WHERE observed_at BETWEEN :startDate AND :endDate
        ORDER BY observed_at ASC, id ASC
        """,
    )
    suspend fun getBetween(startDate: String, endDate: String): List<PriceObservationEntity>

    @Query(
        """
        SELECT * FROM price_observations
        WHERE product_id = :productId AND observed_at BETWEEN :startDate AND :endDate
        ORDER BY observed_at ASC, id ASC
        """,
    )
    suspend fun getForProductBetween(
        productId: Long,
        startDate: String,
        endDate: String,
    ): List<PriceObservationEntity>

    @Query(
        """
        SELECT product_id, COUNT(*) AS observation_count
        FROM price_observations
        GROUP BY product_id
        """,
    )
    fun observeCountsByProduct(): Flow<List<ProductObservationCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: PriceObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(observations: List<PriceObservationEntity>): List<Long>

    @Query("DELETE FROM price_observations WHERE receipt_line_item_id = :lineItemId")
    suspend fun deleteForLineItem(lineItemId: Long)

    @Query(
        """
        DELETE FROM price_observations
        WHERE receipt_line_item_id IN
            (SELECT id FROM line_items WHERE receipt_id = :receiptId)
        """,
    )
    suspend fun deleteForReceipt(receiptId: Long)

    @Query("UPDATE price_observations SET product_id = :targetProductId WHERE product_id = :sourceProductId")
    suspend fun reassignProduct(sourceProductId: Long, targetProductId: Long): Int
}
