package com.siyandimitrov.pocketindex.domain

data class BasketLink(
    val basket: FixedBasket,
    val fromInclusive: EpochDay,
    val toInclusive: EpochDay,
) {
    init {
        require(toInclusive > fromInclusive) { "A chain link must move forward in time." }
        require(fromInclusive >= basket.baseWindow.endInclusive) {
            "A chain link cannot begin before its basket base window ends."
        }
    }
}

data class ChainedIndexPoint(
    val asOf: EpochDay,
    val index: Double,
)

/**
 * Chain-links successive fixed-basket indices multiplicatively.
 *
 * Callers create a newly rebased [FixedBasket] for each annual link. A product that was excluded
 * from the original fixed basket can therefore enter a later link once it meets that link's base
 * eligibility rules.
 */
object ChainedIndexCalculator {
    fun calculate(
        links: List<BasketLink>,
        initialIndex: Double = 100.0,
    ): List<ChainedIndexPoint> {
        require(links.isNotEmpty()) { "At least one basket link is required." }
        require(initialIndex.isFinite() && initialIndex > 0.0) {
            "Initial chained index must be finite and positive."
        }
        links.zipWithNext().forEach { (previous, next) ->
            require(previous.toInclusive == next.fromInclusive) {
                "Basket links must meet at the same rebase day."
            }
        }

        var chainedLevel = initialIndex
        return buildList {
            add(
                ChainedIndexPoint(
                    asOf = links.first().fromInclusive,
                    index = chainedLevel,
                ),
            )
            links.forEach { link ->
                val localStart = link.basket.snapshot(link.fromInclusive).headlineIndex
                val localEnd = link.basket.snapshot(link.toInclusive).headlineIndex
                require(localStart > 0.0 && localEnd.isFinite()) {
                    "Local basket indices must be finite and the start must be positive."
                }
                chainedLevel *= localEnd / localStart
                add(
                    ChainedIndexPoint(
                        asOf = link.toInclusive,
                        index = chainedLevel,
                    ),
                )
            }
        }
    }
}
