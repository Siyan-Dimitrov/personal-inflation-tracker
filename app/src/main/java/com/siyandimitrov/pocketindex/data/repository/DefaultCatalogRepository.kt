package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.CategoryDao
import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.local.MerchantDao
import com.siyandimitrov.pocketindex.data.local.MerchantEntity
import com.siyandimitrov.pocketindex.data.local.ProductDao
import com.siyandimitrov.pocketindex.data.local.ProductEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCatalogRepository @Inject constructor(
    private val categories: CategoryDao,
    private val merchants: MerchantDao,
    private val products: ProductDao,
) : CatalogRepository {
    override fun observeCategories() = categories.observeAll()
    override fun observeMerchants() = merchants.observeAll()
    override fun observeProducts() = products.observeAllWithCategory()
    override fun observeProducts(categoryId: Long) = products.observeByCategory(categoryId)
    override suspend fun addCategory(category: CategoryEntity) = categories.insert(category)
    override suspend fun addMerchant(merchant: MerchantEntity) = merchants.insert(merchant)
    override suspend fun addProduct(product: ProductEntity) = products.insert(product)
    override suspend fun updateCategory(category: CategoryEntity) = categories.update(category)
    override suspend fun updateMerchant(merchant: MerchantEntity) = merchants.update(merchant)
    override suspend fun updateProduct(product: ProductEntity) = products.update(product)
}
