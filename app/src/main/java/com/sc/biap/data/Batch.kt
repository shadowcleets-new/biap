package com.sc.biap.data

// #region Imports
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
// #endregion

/**
 * @file Batch.kt
 * @description A run of pieces made for one size of one design, and what is left of it.
 *
 * [TABLE OF CONTENTS]
 * 1. MODEL
 * 2. NUMBERING
 * 3. TAKING PIECES OUT AND PUTTING THEM BACK
 *
 * WHY STOCK IS STILL A COLUMN, NOT A SUM:
 *   The obvious design is to delete `size_inventories.stock` and read `SUM(remaining)`
 *   instead. It was not done, and the reason is worth keeping.
 *
 *   Stock is read constantly - every catalogue row, every sort, every order ceiling - and
 *   written inside a transaction that must refuse to oversell. Replacing that column with an
 *   aggregate turns one guarded UPDATE into a read, a sum and a write, and the guard
 *   ("AND stock >= :qty", which is what makes overselling impossible) has nowhere to live.
 *
 *   So the column stays authoritative and the batches are a ledger beside it, with one
 *   invariant holding them together:
 *
 *       size_inventories.stock  ==  SUM(batches.remaining) for that design and size
 *
 *   Every movement maintains both halves inside a single transaction. [reconcile] exists to
 *   assert it, and is used by the tests.
 *
 * WHY A RESET EMPTIES BATCHES RATHER THAN DELETING THEM:
 *   Resetting a size to zero sets every batch's `remaining` to 0 and leaves the rows alone.
 *   The history of what was made, when, and at what price is the point of having batches at
 *   all - throwing it away to represent "none left" would destroy the record to express a
 *   quantity. It also means a reset needs no tombstone: nothing is deleted, so nothing can
 *   come back on the next sync.
 */

// #region 1. Model

/**
 * One batch: a run of pieces made for a single size of a single design.
 *
 * [quantity] is what was made and never changes. [remaining] is what is left of it and falls
 * as pieces go out on orders. A batch with `remaining == 0` is spent, not gone.
 */
@Serializable
@Immutable
data class Batch(
    val id: String,
    val designId: String,
    val size: String,
    /** Shown to the owner. Suggested automatically; they may type over it. See [nextBatchNumber]. */
    val number: Int,
    val quantity: Int,
    val remaining: Int,
    /** Paise, as everywhere else. What this run cost to make or was priced at. */
    val price: Long,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val note: String = "",
) {
    val isSpent: Boolean get() = remaining <= 0
    val soldOrUsed: Int get() = (quantity - remaining).coerceAtLeast(0)
}

// #endregion

// #region 2. Numbering

/**
 * The number to suggest for the next batch of a size.
 *
 * One past the highest number already used for THIS design and size, so numbering restarts
 * per size rather than running globally: "Green Desi, L, batch 3" is a sentence the workshop
 * can say, and a global counter in the thousands is not.
 *
 * Takes the highest rather than the count, so it still suggests something sensible after a
 * batch has been renumbered by hand or a design has been merged in from another device.
 */
fun nextBatchNumber(existing: List<Batch>): Int =
    (existing.maxOfOrNull { it.number } ?: 0) + 1

/**
 * Is this number free for this size?
 *
 * Numbering is the owner's to override, so this reports rather than prevents; the caller
 * decides whether to warn. [ignoreId] lets a batch keep its own number while being edited.
 */
fun isBatchNumberFree(existing: List<Batch>, number: Int, ignoreId: String? = null): Boolean =
    existing.none { it.number == number && it.id != ignoreId }

// #endregion

// #region 3. Taking pieces out and putting them back

/** How many pieces to move in or out of one batch. */
@Immutable
data class BatchMove(val batchId: String, val quantity: Int)

/**
 * Which batches an order should take its pieces from: oldest first.
 *
 * Oldest-first because cloth does not improve on a shelf, and because it makes the batch a
 * design was actually sold from the one most likely to still be true months later.
 *
 * Returns an empty list if there is not enough left across all batches, rather than a
 * partial allocation. A caller that cannot fill an order completely must not fill it
 * partially - the transaction is meant to fail whole.
 */
fun allocateOldestFirst(batches: List<Batch>, quantity: Int): List<BatchMove> {
    if (quantity <= 0) return emptyList()
    if (batches.sumOf { it.remaining } < quantity) return emptyList()

    var left = quantity
    val moves = mutableListOf<BatchMove>()
    for (batch in batches.sortedWith(compareBy({ it.number }, { it.createdAt }))) {
        if (left == 0) break
        val take = minOf(batch.remaining, left)
        if (take > 0) {
            moves += BatchMove(batch.id, take)
            left -= take
        }
    }
    return moves
}

/**
 * Where returned pieces go when an order is cancelled or reduced: newest batch first.
 *
 * The mirror of [allocateOldestFirst], so pieces taken from the oldest batch come back to
 * the newest one with room. No batch is ever refilled past the [Batch.quantity] it was made
 * with, which is what stops a return from inventing pieces that were never made.
 *
 * ATTRIBUTION IS APPROXIMATE, THE TOTAL IS NOT. The app does not record which batch each
 * individual piece left on, so a cancellation may credit a different batch than the one the
 * pieces came from if batches changed in between. The total returned always equals the total
 * taken, so stock and the invariant stay exact; only the split between batches can drift.
 * Recording per-line allocations on the order would make it exact, and is the upgrade path
 * if batch-level costing ever needs to be audited.
 */
fun returnNewestFirst(batches: List<Batch>, quantity: Int): List<BatchMove> {
    if (quantity <= 0) return emptyList()

    var left = quantity
    val moves = mutableListOf<BatchMove>()
    for (batch in batches.sortedWith(compareByDescending<Batch> { it.number }.thenByDescending { it.createdAt })) {
        if (left == 0) break
        val room = (batch.quantity - batch.remaining).coerceAtLeast(0)
        val give = minOf(room, left)
        if (give > 0) {
            moves += BatchMove(batch.id, give)
            left -= give
        }
    }
    // Anything still unplaced has nowhere legitimate to go - every batch is already at the
    // size it was made. The caller keeps the aggregate correct; the ledger says no more was
    // ever made, which is the truthful answer.
    return moves
}

/** The stock a size should be showing, from its batches alone. */
fun stockFromBatches(batches: List<Batch>): Int = batches.sumOf { it.remaining }

/**
 * Does the ledger agree with the stock column? Used by the tests and by the reconcile path.
 */
fun reconcile(batches: List<Batch>, stock: Int): Boolean = stockFromBatches(batches) == stock

// #endregion
