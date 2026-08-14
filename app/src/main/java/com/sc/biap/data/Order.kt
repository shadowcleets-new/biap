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
