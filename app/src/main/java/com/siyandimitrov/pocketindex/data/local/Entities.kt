package com.siyandimitrov.pocketindex.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReceiptStatus {
    PENDING,
    NEEDS_REVIEW,
    CONFIRMED,
}

enum class UnitType {
    MASS_G,
    VOLUME_ML,
    COUNT,
    SERVICE,
}

enum class ObservationSource {
    RECEIPT,
    BILL,
    MANUAL,
}

enum class RecurringCadence {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    ANNUAL,
}

@Entity(
    tableName = "merchants",
    indices = [Index(value = ["name"], unique = true)],
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    /** JSON array containing alternate names exactly as they appeared on receipts. */
    val aliases: String = "[]",
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    @ColumnInfo(name = "expenditure_weight")
    val expenditureWeight: Double? = null,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["canonical_name"], unique = true),
    ],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "canonical_name", collate = ColumnInfo.NOCASE)
    val canonicalName: String,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "unit_type")
    val unitType: UnitType,
    @ColumnInfo(name = "pack_size")
    val packSize: Double? = null,
    /** JSON array of raw receipt strings previously matched to this product. */
    val aliases: String = "[]",
)

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchant_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["merchant_id"]),
        Index(value = ["purchased_at"]),
        Index(value = ["status"]),
    ],
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "merchant_id")
    val merchantId: Long? = null,
    /** ISO-8601 calendar date, for example 2026-07-30. */
    @ColumnInfo(name = "purchased_at")
    val purchasedAt: String,
    @ColumnInfo(name = "total_minor")
    val totalMinor: Long,
    @ColumnInfo(name = "subtotal_minor")
    val subtotalMinor: Long? = null,
    @ColumnInfo(name = "tax_minor")
    val taxMinor: Long? = null,
    val currency: String = "GBP",
    @ColumnInfo(name = "image_path")
    val imagePath: String,
    @ColumnInfo(name = "ocr_text")
    val ocrText: String? = null,
    val status: ReceiptStatus = ReceiptStatus.PENDING,
    /** Unix epoch milliseconds. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "line_items",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receipt_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["receipt_id"]),
        Index(value = ["product_id"]),
    ],
)
data class LineItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "receipt_id")
    val receiptId: Long,
    /** Original receipt text. Repository APIs intentionally never update this field. */
    @ColumnInfo(name = "raw_text")
    val rawText: String,
    val quantity: Double,
    @ColumnInfo(name = "unit_price_minor")
    val unitPriceMinor: Long,
    @ColumnInfo(name = "line_total_minor")
    val lineTotalMinor: Long,
    @ColumnInfo(name = "product_id")
    val productId: Long? = null,
    @ColumnInfo(name = "match_confidence")
    val matchConfidence: Double? = null,
    @ColumnInfo(name = "user_confirmed")
    val userConfirmed: Boolean = false,
)

@Entity(
    tableName = "price_observations",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MerchantEntity::class,
            parentColumns = ["id"],
            childColumns = ["merchant_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = LineItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["receipt_line_item_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["product_id", "observed_at"]),
        Index(value = ["merchant_id"]),
        Index(value = ["receipt_line_item_id"], unique = true),
    ],
)
data class PriceObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "product_id")
    val productId: Long,
    /** ISO-8601 calendar date. */
    @ColumnInfo(name = "observed_at")
    val observedAt: String,
    /** Minor currency units per base unit, scaled by one million. */
    @ColumnInfo(name = "unit_price_micros")
    val unitPriceMicros: Long,
    @ColumnInfo(name = "shelf_price_minor")
    val shelfPriceMinor: Long,
    @ColumnInfo(name = "pack_size")
    val packSize: Double,
    @ColumnInfo(name = "merchant_id")
    val merchantId: Long? = null,
    val source: ObservationSource,
    @ColumnInfo(name = "receipt_line_item_id")
    val receiptLineItemId: Long? = null,
)

@Entity(
    tableName = "recurring_items",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["product_id"], unique = true),
        Index(value = ["active"]),
    ],
)
data class RecurringItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "product_id")
    val productId: Long,
    val cadence: RecurringCadence,
    @ColumnInfo(name = "current_price_minor")
    val currentPriceMinor: Long,
    /** ISO-8601 calendar date. */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: String,
    val active: Boolean = true,
)
