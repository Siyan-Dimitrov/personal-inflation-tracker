package com.siyandimitrov.pocketindex.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Millionths of one currency minor unit per product base unit.
 *
 * For GBP, 120 pence for 1,000 ml is 120,000 micros per ml. The integer representation keeps
 * per-gram and per-millilitre prices precise enough for index calculations without floating-point
 * drift during normalisation.
 */
@JvmInline
value class UnitPriceMicros(val value: Long) {
    init {
        require(value >= 0) { "Unit price cannot be negative." }
    }

    companion object {
        private val MICROS_PER_MINOR_UNIT = BigDecimal.valueOf(1_000_000L)

        fun fromShelfPrice(
            shelfPriceMinor: Long,
            packSizeBaseUnits: Double,
        ): UnitPriceMicros {
            require(shelfPriceMinor >= 0) { "Shelf price cannot be negative." }
            require(packSizeBaseUnits.isFinite() && packSizeBaseUnits > 0.0) {
                "Pack size must be finite and positive."
            }

            val normalised = BigDecimal
                .valueOf(shelfPriceMinor)
                .multiply(MICROS_PER_MINOR_UNIT)
                .divide(
                    BigDecimal.valueOf(packSizeBaseUnits),
                    0,
                    RoundingMode.HALF_UP,
                )
                .longValueExact()
            return UnitPriceMicros(normalised)
        }
    }
}
