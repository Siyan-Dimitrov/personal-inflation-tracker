package com.siyandimitrov.pocketindex.di

import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.DefaultCatalogRepository
import com.siyandimitrov.pocketindex.data.repository.DefaultObservationRepository
import com.siyandimitrov.pocketindex.data.repository.DefaultReceiptRepository
import com.siyandimitrov.pocketindex.data.repository.DefaultRecurringRepository
import com.siyandimitrov.pocketindex.data.repository.ObservationRepository
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.data.repository.RecurringRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindReceiptRepository(implementation: DefaultReceiptRepository): ReceiptRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(implementation: DefaultCatalogRepository): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindObservationRepository(
        implementation: DefaultObservationRepository,
    ): ObservationRepository

    @Binds
    @Singleton
    abstract fun bindRecurringRepository(
        implementation: DefaultRecurringRepository,
    ): RecurringRepository
}
