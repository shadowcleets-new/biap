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
    /**
     * The cheapest and dearest sizes that actually carry a price.
     *
     * Null when NO size has been priced yet, so callers say "not priced" rather than "₹0".
     */
    val minPrice: Long?,
    val maxPrice: Long?,
    /**
     * Sizes added but never given a price.
     *
     * A size sitting at 0 is a gap in data entry, not a garment being given away, so it is
     * counted here and kept out of the range. Including it dragged the minimum to zero and
     * made a design read "₹0 – ₹1,225", which is a price nobody ever set.
     */
    val unpricedSizes: Int,
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
    // Only priced sizes shape the range. minOrNull() over everything let a single unpriced
    // size decide the floor.
    val prices = sizeInventories.map { it.price }.filter { it > 0L }
    return DesignStats(
        units = sizeInventories.sumOf { it.stock },
        minPrice = prices.minOrNull(),
        maxPrice = prices.maxOrNull(),
        unpricedSizes = sizeInventories.count { it.price <= 0L },
        outSizes = sizeInventories.count { it.state() == StockState.OUT },
        lowSizes = sizeInventories.count { it.state() == StockState.LOW },
    )
}

/**
 * The one-line summary above the list: how much of the catalogue wants looking at.
 *
 * EVERY FIGURE HERE COUNTS DESIGNS, NOT SIZES.
 *   The headline is about designs, so the numbers under it must be too. Counting sizes
 *   instead said "1 out of stock" about a design holding 42 units, purely because one of its
 *   five sizes had never been stocked. A design is out of stock when there is nothing left of
 *   it at all - not when one size is empty. Per-size gaps are the row's business, and the
 *   row already shows them.
 */
data class CatalogueSummary(
    val designsNeedingAttention: Int,
    /** Designs with no stock whatsoever. Genuinely sold out. */
    val designsOut: Int,
    /** Designs still sellable, but with a size empty or running down. */
    val designsLow: Int,
) {
    val isClear: Boolean get() = designsNeedingAttention == 0
}

fun List<Design>.summarise(): CatalogueSummary {
    val stats = map { it.stats() }
    val soldOut = stats.count { it.units == 0 }
    return CatalogueSummary(
        designsNeedingAttention = stats.count { it.needsAttention },
        designsOut = soldOut,
        // Needs attention but is not sold out: still has stock, just thin somewhere.
        designsLow = stats.count { it.needsAttention && it.units > 0 },
    )
}
