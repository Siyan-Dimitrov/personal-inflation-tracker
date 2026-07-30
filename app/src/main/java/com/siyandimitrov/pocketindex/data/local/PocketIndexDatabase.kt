package com.siyandimitrov.pocketindex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ReceiptEntity::class,
        LineItemEntity::class,
        MerchantEntity::class,
        ProductEntity::class,
        CategoryEntity::class,
        PriceObservationEntity::class,
        RecurringItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class PocketIndexDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun merchantDao(): MerchantDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun priceObservationDao(): PriceObservationDao
    abstract fun recurringItemDao(): RecurringItemDao

    companion object {
        const val DATABASE_NAME = "pocket-index.db"
    }
}
