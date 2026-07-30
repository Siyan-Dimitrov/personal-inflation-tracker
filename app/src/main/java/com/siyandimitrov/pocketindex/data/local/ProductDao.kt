package com.siyandimitrov.pocketindex.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products ORDER BY canonical_name COLLATE NOCASE")
    fun observeAllWithCategory(): Flow<List<ProductWithCategory>>

    @Transaction
    @Query("SELECT * FROM products WHERE category_id = :categoryId ORDER BY canonical_name COLLATE NOCASE")
    fun observeByCategory(categoryId: Long): Flow<List<ProductWithCategory>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :id")
    fun observeWithCategory(id: Long): Flow<ProductWithCategory?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE canonical_name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByCanonicalName(name: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE products SET aliases = :aliases WHERE id = :productId")
    suspend fun updateAliases(productId: Long, aliases: String)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
}
