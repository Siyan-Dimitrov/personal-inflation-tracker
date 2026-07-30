package com.siyandimitrov.pocketindex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE id = :id")
    suspend fun getById(id: Long): MerchantEntity?

    @Query("SELECT * FROM merchants WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): MerchantEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(merchant: MerchantEntity): Long

    @Update
    suspend fun update(merchant: MerchantEntity)

    @Query("SELECT COUNT(*) FROM merchants")
    suspend fun count(): Int
}
