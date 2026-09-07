package com.siyandimitrov.pocketindex

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.siyandimitrov.pocketindex.data.demo.DemoDataSeeder
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.preferences.InflationPreferences
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.diagnostics.CrashLog
import com.siyandimitrov.pocketindex.extraction.ReceiptExtractionScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class PocketIndexApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var demoDataSeeder: DemoDataSeeder

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var receiptRepository: ReceiptRepository

    @Inject
    lateinit var extractionScheduler: ReceiptExtractionScheduler

    @Inject
    lateinit var preferences: InflationPreferences

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                if (!preferences.isDemoSeedBlocked) {
                    demoDataSeeder.seedIfEmpty()
                }
            }
        }
        applicationScope.launch {
            receiptRepository.observeReceipts()
                .first()
                .filter { it.status == ReceiptStatus.PENDING }
                .forEach { receipt ->
                    runCatching { extractionScheduler.enqueue(receipt.id) }
                }
        }
    }
}
