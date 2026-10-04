package com.sc.biap.data

/**
 * @file Demand.kt
 * @description What the records already kept - batches made, orders taken - say about what
 *              to cut next. Nothing here asks the owner to log anything new.
 *
 * [TABLE OF CONTENTS]
 * 1. TYPES
 * 2. PRODUCTION HISTORY  - how often a design has been made
 * 3. SIZE DEMAND         - what each size actually did
 * 4. THE CUT SUGGESTION  - the ratio to cut next time
 * 5. PACE                - how fast it moves, and whether it has stopped
 * 6. HELPERS
 */

// #region 1. Types

/** Two batches cut within a day of each other are one trip to the cutting table. */
private const val RUN_WINDOW_MS = 86_400_000L
private const val MONTH_MS = 2_629_800_000L

@Suppress("unused")
data class ProductionHistory(
    /** Trips to the cutting table, not batches: cutting five sizes on one day is one run. */
    val runs: Int = 0,
    val firstMadeAt: Long? = null,
    val lastMadeAt: Long? = null,
    val totalMade: Int = 0,
)

data class SizeDemand(
    val size: String,
    val made: Int,
    val sold: Int,
    val inStock: Int,
    /**
     * Sitting at zero, which means [sold] is a FLOOR and not the truth.
     *
     * A size that emptied early then sat unavailable went on being wanted and could not be
     * bought. Reading its sold figure as demand is how a workshop teaches itself to keep
     * under-cutting the size that sells best.
     */
    val soldOut: Boolean,
)

data class CutSuggestion(val size: String, val pieces: Int, val previously: Int)

data class DesignPace(
    val sold: Int = 0,
    val soldPerMonth: Double = 0.0,
    val daysSinceLastSale: Int? = null,
    val daysSinceLastRun: Int? = null,
)
// #endregion

// #region 2. Production history

/** How many times a design has been put on the table, and when it last was. */
fun productionHistory(batches: List<Batch>): ProductionHistory {
    if (batches.isEmpty()) return ProductionHistory()
    val times = batches.map { it.createdAt }.sorted()
    var runs = 1
    for (i in 1 until times.size) {
        if (times[i] - times[i - 1] > RUN_WINDOW_MS) runs++
    }
    return ProductionHistory(
        runs = runs,
        firstMadeAt = times.first(),
        lastMadeAt = times.last(),
        totalMade = batches.sumOf { it.quantity },
    )
}
// #endregion

// #region 3. Size demand

/**
 * What each size of one design actually did: made, sold, still on the shelf.
 *
 * Sold comes from orders rather than from stock arithmetic, because stock also moves when
 * a count is corrected or a design is reset, and neither of those is somebody wanting the
 * garment. Orders are the only movement that means demand.
 */
fun sizeDemand(design: Design, batches: List<Batch>, orders: List<Order>): List<SizeDemand> {
    val mine = batches.filter { it.designId == design.id }
    val madeBySize = mine.groupBy { it.size }.mapValues { (_, b) -> b.sumOf { it.quantity } }
    val soldBySize = HashMap<String, Int>()
    for (order in orders) {
        for (line in order.lines) {
            if (line.designId == design.id) {
                soldBySize[line.size] = (soldBySize[line.size] ?: 0) + line.quantity
            }
        }
    }
    // Every size the design has, plus any it no longer lists but was once made or sold in.
    val sizes = LinkedHashSet<String>()
    design.sizeInventories.forEach { sizes.add(it.size) }
    sizes.addAll(madeBySize.keys)
    sizes.addAll(soldBySize.keys)

    return sizes.map { size ->
        val stock = design.sizeInventories.firstOrNull { it.size == size }?.stock ?: 0
        SizeDemand(
            size = size,
            made = madeBySize[size] ?: 0,
            sold = soldBySize[size] ?: 0,
            inStock = stock,
            soldOut = stock == 0,
        )
    }
}
// #endregion

// #region 4. The cut suggestion

/**
 * How to split the next [total] pieces across sizes, from how the last ones actually went.
 *
 * Proportional to what sold, which is the whole point: cutting five of everything is the
 * habit this is meant to replace. Two deliberate departures from straight proportion:
 *
 *   - A size that ever sold gets at least one piece. Dropping a size entirely because it
 *     sold two is a decision for the owner, not for a rounding rule.
 *   - A size standing at zero is counted as if it had sold one more than it did. It ran out
 *     and could not be bought, so its figure is a floor; this nudges it up without pretending
 *     to know by how much.
 *
 * Largest remainder, so the pieces add up to exactly [total] rather than to whatever
 * rounding leaves behind.
 *
 * ponytail: no seasonality, no trend, no decay on old sales. Add when a year of history
 * exists to read a season from.
 */
fun suggestCut(demand: List<SizeDemand>, total: Int): List<CutSuggestion> {
    if (demand.isEmpty() || total <= 0) return emptyList()
    val previously = demand.associate { it.size to it.made }

    val weights = demand.map { if (it.soldOut && it.sold > 0) it.sold + 1 else it.sold }
    val totalWeight = weights.sum()

    // Nothing has sold yet - there is nothing to learn from, so keep the even split the
    // owner would have cut anyway rather than inventing a preference.
    if (totalWeight == 0) {
        val each = total / demand.size
        var spare = total % demand.size
        return demand.map {
            val extra = if (spare > 0) { spare--; 1 } else 0
            CutSuggestion(it.size, each + extra, previously[it.size] ?: 0)
        }
    }

    val everSold = demand.indices.filter { weights[it] > 0 }
    val floors = IntArray(demand.size)
    // Reserve one piece for each size that sold at all, then share out what is left.
    val reserved = minOf(everSold.size, total)
    everSold.take(reserved).forEach { floors[it] = 1 }

    val share = total - reserved
    val exact = weights.map { if (share > 0) it.toDouble() * share / totalWeight else 0.0 }
    exact.forEachIndexed { i, v -> floors[i] += v.toInt() }

    var left = total - floors.sum()
    val byRemainder = exact.indices.sortedByDescending { exact[it] - exact[it].toInt() }
    var i = 0
    while (left > 0 && byRemainder.isNotEmpty()) {
        floors[byRemainder[i % byRemainder.size]]++
        left--
        i++
    }
    return demand.mapIndexed { idx, d -> CutSuggestion(d.size, floors[idx], previously[d.size] ?: 0) }
}
// #endregion

// #region 5. Pace

/** How fast a design moves, and how long since anybody wanted one. */
fun designPace(design: Design, batches: List<Batch>, orders: List<Order>, now: Long): DesignPace {
    val mine = batches.filter { it.designId == design.id }
    val lines = orders.filter { o -> o.lines.any { it.designId == design.id } }
    val sold = lines.sumOf { o -> o.lines.filter { it.designId == design.id }.sumOf { it.quantity } }

    val firstMade = mine.minOfOrNull { it.createdAt }
    val lastRun = mine.maxOfOrNull { it.createdAt }
    val lastSale = lines.maxOfOrNull { it.createdAt }

    // Measured from when it was first made, not from its first sale: time on the shelf
    // before anybody bought one is exactly the part worth knowing about.
    val months = firstMade?.let { (now - it).toDouble() / MONTH_MS }?.takeIf { it > 0.0 }
    return DesignPace(
        sold = sold,
        soldPerMonth = if (months == null || months < 0.25) 0.0 else sold / months,
        daysSinceLastSale = lastSale?.let { daysBetween(it, now) },
        daysSinceLastRun = lastRun?.let { daysBetween(it, now) },
    )
}
// #endregion

// #region 6. Helpers
private fun daysBetween(from: Long, to: Long): Int =
    ((to - from).coerceAtLeast(0L) / 86_400_000L).toInt()
// #endregion
