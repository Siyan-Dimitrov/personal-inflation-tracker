package com.siyandimitrov.pocketindex.data.repository

import androidx.room.withTransaction
import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.local.MerchantEntity
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.ProductEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCatalogRepository @Inject constructor(
    private val database: PocketIndexDatabase,
) : CatalogRepository {
    private val categories = database.categoryDao()
    private val merchants = database.merchantDao()
    private val products = database.productDao()

    override fun observeCategories() = categories.observeAll()
    override fun observeMerchants() = merchants.observeAll()
    override fun observeProducts() = products.observeAllWithCategory()
    override fun observeProducts(categoryId: Long) = products.observeByCategory(categoryId)
    override fun observeProduct(productId: Long) = products.observeWithCategory(productId)
    override suspend fun addCategory(category: CategoryEntity) = categories.insert(category)
    override suspend fun addMerchant(merchant: MerchantEntity) = merchants.insert(merchant)
    override suspend fun addProduct(product: ProductEntity) = products.insert(product)
    override suspend fun getOrCreateCategory(name: String): Long = database.withTransaction {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotEmpty()) { "Category name is required." }
        categories.findByName(cleanName)?.id
            ?: categories.insert(CategoryEntity(name = cleanName)).takeIf { it > 0 }
            ?: checkNotNull(categories.findByName(cleanName)?.id) {
                "The category could not be created."
            }
    }

    override suspend fun getOrCreateMerchant(name: String): Long = database.withTransaction {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotEmpty()) { "Merchant name is required." }
        merchants.findByName(cleanName)?.id
            ?: merchants.insert(MerchantEntity(name = cleanName)).takeIf { it > 0 }
            ?: checkNotNull(merchants.findByName(cleanName)?.id) {
                "The merchant could not be created."
            }
    }

    override suspend fun createProduct(product: NewCatalogProduct): Long =
        database.withTransaction {
            val cleanName = product.canonicalName.trim().take(120)
            require(cleanName.isNotEmpty()) { "Product name is required." }
            require(product.packSize == null || product.packSize > 0.0) {
                "Pack size must be greater than zero."
            }
            checkNotNull(categories.getById(product.categoryId)) {
                "Choose a valid category."
            }
            products.findByCanonicalName(cleanName)?.id
                ?: products.insert(
                    ProductEntity(
                        canonicalName = cleanName,
                        categoryId = product.categoryId,
                        unitType = product.unitType,
                        packSize = product.packSize,
                    ),
                ).takeIf { it > 0 }
                ?: checkNotNull(products.findByCanonicalName(cleanName)?.id) {
                    "The product could not be created."
                }
        }

    override suspend fun updateCategory(category: CategoryEntity) = categories.update(category)
    override suspend fun updateMerchant(merchant: MerchantEntity) = merchants.update(merchant)
    override suspend fun updateProduct(product: ProductEntity) = products.update(product)

    override suspend fun mergeProducts(sourceProductId: Long, targetProductId: Long) {
        require(sourceProductId != targetProductId) { "Choose two different products to merge." }
        database.withTransaction {
            val source = checkNotNull(products.getById(sourceProductId)) {
                "The duplicate product no longer exists."
            }
            val target = checkNotNull(products.getById(targetProductId)) {
                "The product to keep no longer exists."
            }
            val sourceRecurring = database.recurringItemDao().getByProductId(sourceProductId)
            val targetRecurring = database.recurringItemDao().getByProductId(targetProductId)

            products.updateAliases(
                targetProductId,
                encodeAliases(
                    decodeAliases(target.aliases) +
                        decodeAliases(source.aliases) +
                        source.canonicalName,
                ),
            )
            database.priceObservationDao().reassignProduct(sourceProductId, targetProductId)
            database.receiptDao().reassignLineItems(sourceProductId, targetProductId)

            when {
                sourceRecurring == null -> Unit
                targetRecurring == null -> {
                    database.recurringItemDao().reassignProduct(sourceProductId, targetProductId)
                }
                else -> {
                    val newest = listOf(sourceRecurring, targetRecurring)
                        .maxWith(compareBy({ it.lastUpdated }, { it.id }))
                    database.recurringItemDao().update(
                        targetRecurring.copy(
                            currentPriceMinor = newest.currentPriceMinor,
                            lastUpdated = newest.lastUpdated,
                            cadence = newest.cadence,
                            active = sourceRecurring.active || targetRecurring.active,
                        ),
                    )
                    database.recurringItemDao().deleteById(sourceRecurring.id)
                }
            }
            check(products.deleteById(sourceProductId) == 1) {
                "The duplicate product could not be removed."
            }
        }
    }

    /**
     * The schema does the rest: price observations and any recurring bill cascade away, while
     * receipt lines keep their text and merely lose the link to this product.
     */
    override suspend fun deleteProduct(productId: Long) {
        check(products.deleteById(productId) == 1) { "The product no longer exists." }
    }
}

internal fun decodeAliases(json: String): List<String> {
    val trimmed = json.trim()
    if (trimmed.length < 2 || trimmed.first() != '[' || trimmed.last() != ']') return emptyList()
    val result = mutableListOf<String>()
    var index = 1
    while (index < trimmed.lastIndex) {
        while (index < trimmed.lastIndex && (trimmed[index].isWhitespace() || trimmed[index] == ',')) {
            index++
        }
        if (index >= trimmed.lastIndex || trimmed[index] != '"') break
        index++
        val value = StringBuilder()
        while (index < trimmed.lastIndex) {
            val character = trimmed[index++]
            if (character == '"') break
            if (character != '\\' || index >= trimmed.lastIndex) {
                value.append(character)
                continue
            }
            when (val escaped = trimmed[index++]) {
                '"', '\\', '/' -> value.append(escaped)
                'b' -> value.append('\b')
                'f' -> value.append('\u000C')
                'n' -> value.append('\n')
                'r' -> value.append('\r')
                't' -> value.append('\t')
                'u' -> {
                    val end = (index + 4).coerceAtMost(trimmed.lastIndex)
                    val code = trimmed.substring(index, end).toIntOrNull(16)
                    if (code != null && end - index == 4) value.append(code.toChar())
                    index = end
                }
                else -> value.append(escaped)
            }
        }
        result += value.toString()
    }
    return result
}

internal fun encodeAliases(aliases: Iterable<String>): String =
    aliases
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase() }
        .joinToString(prefix = "[", postfix = "]") { alias ->
            buildString {
                append('"')
                alias.forEach { character ->
                    when (character) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> {
                            if (character.code < 0x20) {
                                append("\\u")
                                append(character.code.toString(16).padStart(4, '0'))
                            } else {
                                append(character)
                            }
                        }
                    }
                }
                append('"')
            }
        }
