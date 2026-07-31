package com.siyandimitrov.pocketindex.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.BuildConfig
import com.siyandimitrov.pocketindex.data.demo.DemoDataSeeder
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
    private val demoDataSeeder: DemoDataSeeder,
    private val preferences: InflationPreferences,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DataSettingsUiState())
    val uiState: StateFlow<DataSettingsUiState> = mutableUiState.asStateFlow()

    fun resetData() {
        if (mutableUiState.value.isResetting) return
        mutableUiState.value = DataSettingsUiState(isResetting = true)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    database.clearAllTables()
                    File(context.filesDir, RECEIPT_DIRECTORY).let { directory ->
                        check(!directory.exists() || directory.deleteRecursively()) {
                            "The private receipt images could not be removed."
                        }
                    }
                    preferences.resetToDefaults()
                    if (BuildConfig.DEBUG) {
                        check(demoDataSeeder.seedIfEmpty()) { "The demo data could not be restored." }
                    }
                }
            }.onSuccess {
                mutableUiState.value = DataSettingsUiState(
                    message = if (BuildConfig.DEBUG) {
                        "Demo data restored."
                    } else {
                        "All local data reset."
                    },
                )
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
