package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.local.MerchantEntity
import com.siyandimitrov.pocketindex.data.local.ProductEntity
import com.siyandimitrov.pocketindex.data.local.ProductWithCategory
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeCategories(): Flow<List<CategoryEntity>>
    fun observeMerchants(): Flow<List<MerchantEntity>>
    fun observeProducts(): Flow<List<ProductWithCategory>>
    fun observeProducts(categoryId: Long): Flow<List<ProductWithCategory>>
    suspend fun addCategory(category: CategoryEntity): Long
    suspend fun addMerchant(merchant: MerchantEntity): Long
    suspend fun addProduct(product: ProductEntity): Long
    suspend fun updateCategory(category: CategoryEntity)
    suspend fun updateMerchant(merchant: MerchantEntity)
    suspend fun updateProduct(product: ProductEntity)
}
