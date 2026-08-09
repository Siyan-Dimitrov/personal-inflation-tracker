package com.siyandimitrov.pocketindex.di

import android.content.Context
import androidx.room.Room
import com.siyandimitrov.pocketindex.data.local.CategoryDao
import com.siyandimitrov.pocketindex.data.local.MerchantDao
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.PriceObservationDao
import com.siyandimitrov.pocketindex.data.local.ProductDao
import com.siyandimitrov.pocketindex.data.local.ReceiptDao
import com.siyandimitrov.pocketindex.data.local.RecurringItemDao
import com.siyandimitrov.pocketindex.BuildConfig
import com.siyandimitrov.pocketindex.extraction.MlKitReceiptOcrService
import com.siyandimitrov.pocketindex.extraction.ReceiptExtractor
import com.siyandimitrov.pocketindex.extraction.ReceiptOcrService
import com.siyandimitrov.pocketindex.extraction.RuleBasedReceiptExtractor
import com.siyandimitrov.pocketindex.suggestions.GeminiProductSuggestionService
import com.siyandimitrov.pocketindex.suggestions.ProductSuggestionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PocketIndexDatabase =
        Room.databaseBuilder(
            context,
            PocketIndexDatabase::class.java,
            PocketIndexDatabase.DATABASE_NAME,
        ).build()

    @Provides
    fun provideReceiptDao(database: PocketIndexDatabase): ReceiptDao = database.receiptDao()

    @Provides
    fun provideMerchantDao(database: PocketIndexDatabase): MerchantDao = database.merchantDao()

    @Provides
    fun provideCategoryDao(database: PocketIndexDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideProductDao(database: PocketIndexDatabase): ProductDao = database.productDao()

    @Provides
    fun providePriceObservationDao(
        database: PocketIndexDatabase,
    ): PriceObservationDao = database.priceObservationDao()

    @Provides
    fun provideRecurringItemDao(
        database: PocketIndexDatabase,
    ): RecurringItemDao = database.recurringItemDao()

    @Provides
    @Singleton
    fun provideReceiptOcrService(
        @ApplicationContext context: Context,
    ): ReceiptOcrService = MlKitReceiptOcrService(context)

    @Provides
    @Singleton
    fun provideReceiptExtractor(): ReceiptExtractor = RuleBasedReceiptExtractor()

    @Provides
    @Singleton
    fun provideProductSuggestionService(): ProductSuggestionService =
        GeminiProductSuggestionService(BuildConfig.GEMINI_API_KEY)
}
