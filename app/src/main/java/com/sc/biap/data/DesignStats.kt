package com.sc.biap.data

/**
 * @file DesignStats.kt
 * @description What a design's size matrix adds up to.
 *
 * Pure functions on data already in memory. The catalogue screen used to show "6 items" and a
 * price range, both derived from this same matrix and then thrown away - so nothing here needs
 * a new query, a schema change or a sync round trip. It only stops discarding what was already
 * loaded.
 */

/** How a single size is doing. The thresholds are the whole definition of "needs attention". */
enum class StockState { HEALTHY, LOW, OUT }

/** At or below this many units a size counts as running low. */
const val LOW_STOCK_THRESHOLD = 3

fun SizeInventory.state(): StockState = when {
    stock <= 0 -> StockState.OUT
    stock <= LOW_STOCK_THRESHOLD -> StockState.LOW
    else -> StockState.HEALTHY
}

data class DesignStats(
    val units: Int,
    /** Null when the design has no sizes at all, so callers must decide what to show. */
    val minPrice: Long?,
    val maxPrice: Long?,
    val outSizes: Int,
    val lowSizes: Int,
) {
    /** Out of stock is worse than low, and either one earns a place at the top of the list. */
    val needsAttention: Boolean get() = outSizes > 0 || lowSizes > 0

    val worstState: StockState
        get() = when {
            outSizes > 0 -> StockState.OUT
            lowSizes > 0 -> StockState.LOW
            else -> StockState.HEALTHY
        }
}

fun Design.stats(): DesignStats {
    val prices = sizeInventories.map { it.price }
    return DesignStats(
        units = sizeInventories.sumOf { it.stock },
        minPrice = prices.minOrNull(),
        maxPrice = prices.maxOrNull(),
        outSizes = sizeInventories.count { it.state() == StockState.OUT },
        lowSizes = sizeInventories.count { it.state() == StockState.LOW },
    )
}

/** The one-line summary above the list: how much of the catalogue wants looking at. */
data class CatalogueSummary(
    val designsNeedingAttention: Int,
    val sizesOut: Int,
    val sizesLow: Int,
) {
    val isClear: Boolean get() = designsNeedingAttention == 0
}

fun List<Design>.summarise(): CatalogueSummary {
    val stats = map { it.stats() }
    return CatalogueSummary(
        designsNeedingAttention = stats.count { it.needsAttention },
        sizesOut = stats.sumOf { it.outSizes },
        sizesLow = stats.sumOf { it.lowSizes },
    )
}
