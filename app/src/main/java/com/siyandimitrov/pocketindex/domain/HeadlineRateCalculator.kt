package com.siyandimitrov.pocketindex.domain

import kotlin.math.pow

data class IndexPoint(
    val asOf: EpochDay,
    val index: Double,
) {
    init {
        require(index.isFinite() && index > 0.0) {
            "An index point must be finite and positive."
        }
    }
}

enum class HeadlineRateKind {
    YEAR_ON_YEAR,
    ANNUALISED_EARLY_ESTIMATE,
}

data class HeadlineRate(
    val percent: Double,
    val kind: HeadlineRateKind,
    val current: IndexPoint,
    val comparison: IndexPoint,
)

/**
 * Produces the headline inflation rate from a monthly index series.
 *
 * Once at least 365 days of history are available, the comparison is the most recent monthly point
 * at or before the one-year cutoff. Before then, change from the first point is annualised and
 * explicitly labelled as an early estimate.
 */
object HeadlineRateCalculator {
    private const val DAYS_PER_YEAR = 365.2425
    private const val YEAR_ON_YEAR_CUTOFF_DAYS = 365L

    fun calculate(points: Collection<IndexPoint>): HeadlineRate? {
        val ordered = points.sortedBy { it.asOf.value }
        if (ordered.size < 2) return null

        val current = ordered.last()
        val first = ordered.first()
        val elapsedDays = current.asOf.daysSince(first.asOf)
        if (elapsedDays <= 0) return null

        return if (elapsedDays >= YEAR_ON_YEAR_CUTOFF_DAYS) {
            val cutoff = current.asOf.plusDays(-YEAR_ON_YEAR_CUTOFF_DAYS)
            val comparison = ordered.last { it.asOf <= cutoff }
            HeadlineRate(
                percent = current.index / comparison.index * 100.0 - 100.0,
                kind = HeadlineRateKind.YEAR_ON_YEAR,
                current = current,
                comparison = comparison,
            )
        } else {
            HeadlineRate(
                percent = (
                    (current.index / first.index)
                        .pow(DAYS_PER_YEAR / elapsedDays.toDouble()) -
                        1.0
                    ) * 100.0,
                kind = HeadlineRateKind.ANNUALISED_EARLY_ESTIMATE,
                current = current,
                comparison = first,
            )
        }
    }
}
