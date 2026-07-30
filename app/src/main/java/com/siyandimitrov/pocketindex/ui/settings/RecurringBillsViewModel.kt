package com.siyandimitrov.pocketindex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.local.ProductEntity
import com.siyandimitrov.pocketindex.data.local.RecurringCadence
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringBillUi(
    val recurringItemId: Long,
    val productId: Long,
    val name: String,
    val priceMinor: Long,
    val cadence: RecurringCadence,
    val lastUpdated: String,
)

data class RecurringBillsUiState(
    val bills: List<RecurringBillUi> = emptyList(),
) {
    val monthlyTotalMinor: Long
        get() = bills.sumOf { bill ->
            when (bill.cadence) {
                RecurringCadence.WEEKLY -> bill.priceMinor * 52.0 / 12.0
                RecurringCadence.MONTHLY -> bill.priceMinor.toDouble()
                RecurringCadence.QUARTERLY -> bill.priceMinor / 3.0
                RecurringCadence.ANNUAL -> bill.priceMinor / 12.0
            }.roundToLong()
        }
}

@HiltViewModel
class RecurringBillsViewModel @Inject constructor(
    private val recurringRepository: RecurringRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    val uiState = recurringRepository.observeActive()
        .map { recurringItems ->
            RecurringBillsUiState(
                bills = recurringItems.map { item ->
                    RecurringBillUi(
                        recurringItemId = item.recurringItem.id,
                        productId = item.product.id,
                        name = item.product.canonicalName,
                        priceMinor = item.recurringItem.currentPriceMinor,
                        cadence = item.recurringItem.cadence,
                        lastUpdated = item.recurringItem.lastUpdated,
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecurringBillsUiState(),
        )

    fun saveBill(
        existingBill: RecurringBillUi?,
        name: String,
        poundsText: String,
        cadence: RecurringCadence,
    ) {
        val billName = name.trim()
        val priceMinor = poundsText.toMinorUnitsOrNull() ?: return
        if (billName.isBlank()) return

        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
            if (existingBill != null) {
                recurringRepository.updateRecurringBill(
                    recurringItemId = existingBill.recurringItemId,
                    name = billName,
                    cadence = cadence,
                    priceMinor = priceMinor,
                    observedAt = today,
                )
                return@launch
            }
            val categories = catalogRepository.observeCategories().first()
            val categoryId = categories
                .firstOrNull { it.name.equals(HOUSEHOLD_CATEGORY, ignoreCase = true) }
                ?.id
                ?: catalogRepository.addCategory(CategoryEntity(name = HOUSEHOLD_CATEGORY))
            val products = catalogRepository.observeProducts().first()
            val productId = products
                .firstOrNull {
                    it.product.canonicalName.equals(billName, ignoreCase = true) &&
                        it.product.unitType == UnitType.SERVICE
                }
                ?.product
                ?.id
                ?: catalogRepository.addProduct(
                    ProductEntity(
                        canonicalName = billName,
                        categoryId = categoryId,
                        unitType = UnitType.SERVICE,
                        packSize = 1.0,
                    ),
                )
            recurringRepository.recordPrice(
                productId = productId,
                cadence = cadence,
                priceMinor = priceMinor,
                observedAt = today,
            )
        }
    }

    fun removeBill(bill: RecurringBillUi) {
        viewModelScope.launch {
            recurringRepository.removeRecurringBill(bill.recurringItemId)
        }
    }

    private companion object {
        const val HOUSEHOLD_CATEGORY = "Household bills"
    }
}

internal fun String.toMinorUnitsOrNull(): Long? {
    val normalised = trim().removePrefix("£").trim()
    if (normalised.isEmpty()) return null
    return runCatching {
        normalised
            .toBigDecimal()
            .also { require(it.signum() > 0) }
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()
}
