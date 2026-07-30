package com.siyandimitrov.pocketindex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceObservationDao {
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
}
