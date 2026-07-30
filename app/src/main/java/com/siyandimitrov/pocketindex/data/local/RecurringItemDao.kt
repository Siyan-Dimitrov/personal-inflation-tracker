package com.siyandimitrov.pocketindex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringItemDao {
    @Transaction
    @Query("SELECT * FROM recurring_items ORDER BY active DESC, last_updated DESC")
    fun observeAllWithProduct(): Flow<List<RecurringItemWithProduct>>

    @Transaction
    @Query("SELECT * FROM recurring_items WHERE active = 1 ORDER BY last_updated DESC")
    fun observeActiveWithProduct(): Flow<List<RecurringItemWithProduct>>

    @Query("SELECT * FROM recurring_items WHERE id = :id")
    suspend fun getById(id: Long): RecurringItemEntity?

    @Query("SELECT * FROM recurring_items WHERE product_id = :productId LIMIT 1")
    suspend fun getByProductId(productId: Long): RecurringItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: RecurringItemEntity): Long

    @Update
    suspend fun update(item: RecurringItemEntity)

    @Query("UPDATE recurring_items SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
