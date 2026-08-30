package com.siyandimitrov.pocketindex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.preferences.InflationPreferences
import com.siyandimitrov.pocketindex.data.preferences.VisionSettings
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryWeightUi(
    val id: Long,
    val name: String,
    val overridePercent: Double?,
)

data class InflationSettingsUiState(
    val baseWindowDays: Long = 56,
    val categories: List<CategoryWeightUi> = emptyList(),
    val validationMessage: String? = null,
    val vision: VisionSettings = VisionSettings(),
)

@HiltViewModel
class InflationSettingsViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val preferences: InflationPreferences,
) : ViewModel() {
    private val validationMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        catalogRepository.observeCategories(),
        preferences.baseWindowDays,
        validationMessage,
        preferences.visionSettingsFlow,
    ) { categories, baseWindowDays, message, vision ->
        InflationSettingsUiState(
            baseWindowDays = baseWindowDays,
            categories = categories.map {
                CategoryWeightUi(
                    id = it.id,
                    name = it.name,
                    overridePercent = it.expenditureWeight?.times(100.0),
                )
            },
            validationMessage = message,
            vision = vision,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InflationSettingsUiState(),
    )

    fun setBaseWindowDays(days: Long) {
        preferences.setBaseWindowDays(days)
        validationMessage.value = null
    }

    /** Returns a message when the address is unusable; an empty address switches AI reading off. */
    fun saveVisionSettings(serverUrl: String, apiKey: String, model: String): String? {
        val url = serverUrl.trim()
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
            return "The server address must start with http:// or https://."
        }
        preferences.setVisionSettings(
            VisionSettings(
                serverUrl = url,
                apiKey = apiKey,
                model = model.ifBlank { VisionSettings.DEFAULT_MODEL },
            ),
        )
        return null
    }

    fun setCategoryWeight(categoryId: Long, percentText: String) {
        val categories = uiState.value.categories
        val selected = categories.firstOrNull { it.id == categoryId } ?: return
        val percent = if (percentText.isBlank()) {
            null
        } else {
            percentText.trim().toDoubleOrNull()
        }
        if (percentText.isNotBlank() && (percent == null || percent !in 0.0..100.0)) {
            validationMessage.value = "Enter a percentage from 0 to 100, or clear it for automatic."
            return
        }
        val proposed = categories.map {
            if (it.id == categoryId) percent else it.overridePercent
        }
        val total = proposed.filterNotNull().sum()
        if (total > 100.0 + WEIGHT_TOLERANCE) {
            validationMessage.value = "Category overrides cannot add up to more than 100%."
            return
        }
        if (proposed.all { it != null } && abs(total - 100.0) > WEIGHT_TOLERANCE) {
            validationMessage.value =
                "When every category is overridden, the weights must add up to 100%."
            return
        }

        viewModelScope.launch {
            val entity = CategoryEntity(
                id = selected.id,
                name = selected.name,
                expenditureWeight = percent?.div(100.0),
            )
            runCatching { catalogRepository.updateCategory(entity) }
                .onSuccess { validationMessage.value = null }
                .onFailure {
                    validationMessage.value = it.message ?: "The category weight could not be saved."
                }
        }
    }

    private companion object {
        const val WEIGHT_TOLERANCE = 1e-7
    }
}
