package com.sc.biap.data

import com.sc.biap.util.SizeUtils
import kotlinx.serialization.Serializable

/**
 * @file Order.kt
 * @description Pieces leaving the workshop. The output side of the inventory.
 *
 * WHY THE PRICE IS COPIED ONTO THE LINE:
 *   [OrderLine.unitPrice] is the price as it stood the moment the order was placed, not a
 *   pointer to the design's current price. Raising a price next month must not silently
 *   rewrite what last month's orders were worth - an order is a record of what happened, and
 *   a record that changes underneath you is not a record.
 *
 * WHY THE SIZE IS A STRING, NOT A REFERENCE:
 *   Same reason. Sizes live inside a design and can be edited or removed; the order still has
 *   to say which size went out, even if that size no longer exists in the catalogue.
 */
@Serializable
data class OrderLine(
    val designId: String,
    val size: String,
    val quantity: Int,
    /** Paise per piece, as at the time of the order. Zero when the size was never priced. */
    val unitPrice: Long,
)

@Serializable
data class Order(
    val id: String,
    val createdAt: Long,
    val lines: List<OrderLine> = emptyList(),
    /**
     * When the record last changed, for merge resolution. Equal to [createdAt] for an order
     * that has never been touched since, which is currently every order - orders are written
     * once and cancelled, never edited.
     */
    val updatedAt: Long = createdAt,
) {
    val pieces: Int get() = lines.sumOf { it.quantity }

    /** Paise. Lines with no price contribute nothing rather than guessing one. */
    val total: Long get() = lines.sumOf { it.quantity * it.unitPrice }

    val designCount: Int get() = lines.distinctBy { it.designId }.size

    /** True when any line went out at an unknown price, so a total can be shown honestly. */
    val hasUnpricedLines: Boolean get() = lines.any { it.unitPrice <= 0L }

    /** The order's lines grouped by design, in the order the designs first appear. */
    fun linesByDesign(): Map<String, List<OrderLine>> = lines.groupBy { it.designId }
}

/**
 * How much stock each design-and-size must move by, going from [before] to [after].
 *
 * Positive means take that many more out; negative means put that many back. Sizes that did
 * not change are absent rather than present as zero, so a caller can act on every entry.
 *
 * Quantities are summed per design-and-size first, so an order carrying the same size on two
 * lines is counted once. Pure and tested because getting a sign or a missing key wrong here
 * does not throw - it silently leaves the catalogue claiming stock that has already gone out,
 * or hiding stock that never left.
 */
fun stockDeltas(
    before: List<OrderLine>,
    after: List<OrderLine>,
): Map<Pair<String, String>, Int> {
    fun tally(lines: List<OrderLine>) = lines
        .groupBy { it.designId to it.size }
        .mapValues { (_, group) -> group.sumOf { it.quantity } }

    val old = tally(before)
    val new = tally(after)
    return (old.keys + new.keys)
        .associateWith { (new[it] ?: 0) - (old[it] ?: 0) }
        .filterValues { it != 0 }
}

/**
 * Put the order's own prices back onto the lines it already had.
 *
 * An order is a record of one sale at one set of prices. Two pieces of size L sold in June at
 * ₹1,150 are still ₹1,150 apiece when a third is added in August, even though the catalogue
 * has since moved to ₹1,500 for a later batch - otherwise editing an order in August silently
 * revalues what June was worth.
 *
 * A size the order NEVER had comes back with whatever price it arrived with (zero), because
 * there is no historic price to inherit; the caller fills those from the catalogue. That is
 * the one case where today's price is the only honest answer available.
 */
fun keepRecordedPrices(lines: List<OrderLine>, recorded: List<OrderLine>): List<OrderLine> {
    val was = recorded.associateBy { it.designId to it.size }
    return lines.map { line ->
        val previous = was[line.designId to line.size]
        if (previous != null) line.copy(unitPrice = previous.unitPrice) else line
    }
}

/**
 * How one design's sizes read on an order: "L – 5XL" or "XL, XXL & 5XL".
 *
 * WHY TWO FORMS RATHER THAN ONE:
 *   A range and a list say different things. "L – 5XL" means every size this design has, from
 *   one end to the other, went out - the design was cleared across the board. "XL, XXL & 5XL"
 *   means three specific sizes did, and the gaps are the point. Writing both as a list hides
 *   the first fact; writing both as a range would be a lie about the second.
 *
 * CONTIGUOUS AGAINST THE DESIGN'S OWN LADDER, NOT A UNIVERSAL ONE:
 *   A design stocking L, XL, XXL, 3XL, 5XL has no 4XL, so ordering all five of those IS
 *   an unbroken run and reads "L – 5XL". Measured against the universal ladder it would look
 *   like it skipped 4XL and be written out longhand, which would be true of the ladder and
 *   false about the design.
 *
 * Compared by sort weight rather than by string, so a line stored as "2XL" still matches a
 * design holding "XXL" - [SizeUtils] treats those as the same rung and so must this.
 *
 * A size no longer on the design (removed after the order was placed) has no rung, so the run
 * is broken and the list form is used. That is the safe direction to fail: listing sizes is
 * never wrong, only longer.
 */
fun orderedSizeLabel(orderedSizes: List<String>, designSizes: List<String>): String {
    val ordered = orderedSizes.distinct().sortedBy { SizeUtils.getSizeSortWeight(it) }
    if (ordered.isEmpty()) return ""
    if (ordered.size == 1) return ordered.first()

    val ladder = designSizes.map { SizeUtils.getSizeSortWeight(it) }.sorted()
    val rungs = ordered.map { ladder.indexOf(SizeUtils.getSizeSortWeight(it)) }
    val unbroken = rungs.none { it < 0 } && rungs.zipWithNext().all { (a, b) -> b == a + 1 }

    return if (unbroken) {
        "${ordered.first()} – ${ordered.last()}"
    } else {
        ordered.dropLast(1).joinToString(", ") + " & " + ordered.last()
    }
}
