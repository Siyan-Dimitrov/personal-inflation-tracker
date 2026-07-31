package com.siyandimitrov.pocketindex.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.await
import com.siyandimitrov.pocketindex.data.local.LocalDataLock
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.preferences.InflationPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DataSettingsUiState(
    val isResetting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class DataSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: PocketIndexDatabase,
    private val preferences: InflationPreferences,
    private val localDataLock: LocalDataLock,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DataSettingsUiState())
    val uiState: StateFlow<DataSettingsUiState> = mutableUiState.asStateFlow()

    /**
     * Returns the app to a first-launch state: no receipts, products, observations, bills, or
     * settings, and no demo history restored on the next launch.
     */
    fun resetData() {
        if (mutableUiState.value.isResetting) return
        mutableUiState.value = DataSettingsUiState(isResetting = true)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // Cancelled before the lock is taken so an extraction still running its OCR
                    // stops at its next suspension point instead of making the user wait.
                    WorkManager.getInstance(context).cancelAllWork().await()
                    localDataLock.withLock {
                        // Blocked first, and durably: if any later step fails or the process
                        // dies, the next debug launch must still not restore the demo history.
                        preferences.blockDemoSeed()
                        database.clearAllTables()
                        File(context.filesDir, RECEIPT_DIRECTORY).let { directory ->
                            check(!directory.exists() || directory.deleteRecursively()) {
                                "The private receipt images could not be removed."
                            }
                        }
                        // The scanner leaves a full-resolution copy of every page in the cache.
                        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        preferences.resetToDefaults()
                    }
                }
            }.onSuccess {
                mutableUiState.value = DataSettingsUiState(message = "All local data reset.")
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mutableUiState.value = DataSettingsUiState(
                    message = error.message ?: "The data could not be reset.",
                    isError = true,
                )
            }
        }
    }

    fun dismissMessage() {
        mutableUiState.update { it.copy(message = null, isError = false) }
    }

    private companion object {
        const val RECEIPT_DIRECTORY = "receipts"
    }
}
