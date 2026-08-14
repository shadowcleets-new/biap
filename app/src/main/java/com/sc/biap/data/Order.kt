package com.sc.biap.data

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
}
