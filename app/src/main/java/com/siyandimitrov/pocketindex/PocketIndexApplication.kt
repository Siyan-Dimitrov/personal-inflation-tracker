package com.siyandimitrov.pocketindex

import android.app.Application
import com.siyandimitrov.pocketindex.data.demo.DemoDataSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PocketIndexApplication : Application() {
    @Inject
    lateinit var demoDataSeeder: DemoDataSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                demoDataSeeder.seedIfEmpty()
            }
        }
    }
}

