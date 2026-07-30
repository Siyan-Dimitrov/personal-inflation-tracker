package com.siyandimitrov.pocketindex.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation

data class ProductWithCategory(
    @Embedded
    val product: ProductEntity,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "id",
    )
    val category: CategoryEntity,
)

data class ReceiptWithDetails(
    @Embedded
    val receipt: ReceiptEntity,
    @Relation(
        parentColumn = "merchant_id",
        entityColumn = "id",
    )
    val merchant: MerchantEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "receipt_id",
    )
    val lineItems: List<LineItemEntity>,
)

data class RecurringItemWithProduct(
    @Embedded
    val recurringItem: RecurringItemEntity,
    @Relation(
        parentColumn = "product_id",
        entityColumn = "id",
    )
    val product: ProductEntity,
)

data class ProductObservationCount(
    @ColumnInfo(name = "product_id")
    val productId: Long,
    @ColumnInfo(name = "observation_count")
    val observationCount: Int,
)

data class ReceiptListItem(
    val id: Long,
    @ColumnInfo(name = "purchased_at")
    val purchasedAt: String,
    @ColumnInfo(name = "total_minor")
    val totalMinor: Long,
    val currency: String,
    val status: ReceiptStatus,
    @ColumnInfo(name = "merchant_name")
    val merchantName: String?,
    @ColumnInfo(name = "line_item_count")
    val lineItemCount: Int,
)

data class CategorySpend(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "total_minor")
    val totalMinor: Long,
)
