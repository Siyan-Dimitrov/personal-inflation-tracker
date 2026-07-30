package com.siyandimitrov.pocketindex.domain

/**
 * A calendar day represented as the number of days from 1970-01-01.
 *
 * Keeping dates in this form makes the domain module usable on every supported Android API level
 * without requiring java.time desugaring.
 */
@JvmInline
value class EpochDay(val value: Long) : Comparable<EpochDay> {
    override fun compareTo(other: EpochDay): Int = value.compareTo(other.value)

    fun plusDays(days: Long): EpochDay = EpochDay(Math.addExact(value, days))

    fun daysSince(earlier: EpochDay): Long = Math.subtractExact(value, earlier.value)
}

@JvmInline
value class ProductId(val value: Long) {
    init {
        require(value > 0) { "Product id must be positive." }
    }
}

@JvmInline
value class CategoryId(val value: Long) {
    init {
        require(value > 0) { "Category id must be positive." }
    }
}

@JvmInline
value class MerchantId(val value: Long) {
    init {
        require(value > 0) { "Merchant id must be positive." }
    }
}

enum class UnitType {
    MASS_G,
    VOLUME_ML,
    COUNT,
    SERVICE,
}

enum class ObservationSource {
    RECEIPT,
    BILL,
    MANUAL,
}

data class Product(
    val id: ProductId,
    val categoryId: CategoryId,
    val unitType: UnitType,
) {
    val isGroceryType: Boolean
        get() = unitType != UnitType.SERVICE
}

/**
 * A price observation enriched with the quantity needed by the index domain.
 *
 * [purchasedQuantityBaseUnits] is normally receipt quantity multiplied by pack size. It is one for
 * a service/bill period. Room's analytical row does not contain this value, so the data layer should
 * obtain it by joining the source line item before constructing this model.
 */
data class PriceObservation(
    val observationId: Long,
    val productId: ProductId,
    val observedOn: EpochDay,
    val unitPrice: UnitPriceMicros,
    val purchasedQuantityBaseUnits: Double,
    val merchantId: MerchantId? = null,
    val source: ObservationSource,
    val shelfPriceMinor: Long? = null,
    val packSizeBaseUnits: Double? = null,
) {
    init {
        require(observationId >= 0) { "Observation id cannot be negative." }
        require(
            purchasedQuantityBaseUnits.isFinite() && purchasedQuantityBaseUnits > 0.0,
        ) {
            "Purchased quantity must be finite and positive."
        }
        require(shelfPriceMinor == null || shelfPriceMinor >= 0) {
            "Shelf price cannot be negative."
        }
        require(
            packSizeBaseUnits == null ||
                (packSizeBaseUnits.isFinite() && packSizeBaseUnits > 0.0),
        ) {
            "Pack size must be finite and positive."
        }
    }

    companion object {
        fun purchase(
            observationId: Long,
            productId: ProductId,
            observedOn: EpochDay,
            shelfPriceMinor: Long,
            packSizeBaseUnits: Double,
            packsPurchased: Double = 1.0,
            merchantId: MerchantId? = null,
            source: ObservationSource = ObservationSource.RECEIPT,
        ): PriceObservation {
            require(packsPurchased.isFinite() && packsPurchased > 0.0) {
                "Packs purchased must be finite and positive."
            }
            return PriceObservation(
                observationId = observationId,
                productId = productId,
                observedOn = observedOn,
                unitPrice = UnitPriceMicros.fromShelfPrice(
                    shelfPriceMinor = shelfPriceMinor,
                    packSizeBaseUnits = packSizeBaseUnits,
                ),
                purchasedQuantityBaseUnits = packSizeBaseUnits * packsPurchased,
                merchantId = merchantId,
                source = source,
                shelfPriceMinor = shelfPriceMinor,
                packSizeBaseUnits = packSizeBaseUnits,
            )
        }

        fun bill(
            observationId: Long,
            productId: ProductId,
            observedOn: EpochDay,
            periodPriceMinor: Long,
            merchantId: MerchantId? = null,
        ): PriceObservation = PriceObservation(
            observationId = observationId,
            productId = productId,
            observedOn = observedOn,
            unitPrice = UnitPriceMicros.fromShelfPrice(
                shelfPriceMinor = periodPriceMinor,
                packSizeBaseUnits = 1.0,
            ),
            purchasedQuantityBaseUnits = 1.0,
            merchantId = merchantId,
            source = ObservationSource.BILL,
            shelfPriceMinor = periodPriceMinor,
            packSizeBaseUnits = 1.0,
        )
    }
}

data class BaseWindow(
    val startInclusive: EpochDay,
    val endInclusive: EpochDay,
) {
    init {
        require(endInclusive >= startInclusive) {
            "Base window cannot end before it starts."
        }
    }

    val durationDays: Long
        get() = endInclusive.daysSince(startInclusive) + 1

    operator fun contains(day: EpochDay): Boolean =
        day >= startInclusive && day <= endInclusive

    companion object {
        const val DEFAULT_DURATION_DAYS: Long = 56

        fun startingOn(
            firstDay: EpochDay,
            durationDays: Long = DEFAULT_DURATION_DAYS,
        ): BaseWindow {
            require(durationDays > 0) { "Base window duration must be positive." }
            return BaseWindow(
                startInclusive = firstDay,
                endInclusive = firstDay.plusDays(durationDays - 1),
            )
        }
    }
}

data class IndexConfiguration(
    val baseWindowDays: Long = BaseWindow.DEFAULT_DURATION_DAYS,
    val staleAfterDays: Long = 90,
) {
    init {
        require(baseWindowDays > 0) { "Base window duration must be positive." }
        require(staleAfterDays >= 0) { "Stale threshold cannot be negative." }
    }
}
