package com.siyandimitrov.pocketindex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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

        /** 2026-09-07: receipts record how their text was read. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN read_by TEXT")
            }
        }
    }
}
