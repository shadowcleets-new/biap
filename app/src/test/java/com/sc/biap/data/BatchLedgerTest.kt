package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ledger and the stock column have to agree, always.
 *
 * Stock stayed a real column (see Batch.kt for why) with batches beside it, which means the
 * invariant `stock == sum(remaining)` is maintained by hand rather than by construction.
 * These tests are what keeps that honest: the total moved must always match what was asked
 * for, even where the split between batches is approximate.
 */
class BatchLedgerTest {

    private fun batch(number: Int, quantity: Int, remaining: Int = quantity, price: Long = 115_000) =
        Batch(
            id = "b$number",
            designId = "d1",
            size = "L",
            number = number,
            quantity = quantity,
            remaining = remaining,
            price = price,
            createdAt = number * 1_000L,
        )

    // --- numbering ---

    @Test
    fun `numbering starts at one and counts per size`() {
        assertEquals(1, nextBatchNumber(emptyList()))
        assertEquals(3, nextBatchNumber(listOf(batch(1, 10), batch(2, 10))))
    }

    @Test
    fun `numbering follows the highest, not the count`() {
        // Renumbered by hand, or merged in from another device. Suggesting 3 here would
        // collide with the batch already called 7.
        assertEquals(8, nextBatchNumber(listOf(batch(1, 10), batch(7, 10))))
    }

    @Test
    fun `a number already in use is reported, and a batch may keep its own`() {
        val existing = listOf(batch(1, 10), batch(2, 10))
        assertFalse(isBatchNumberFree(existing, 2))
        assertTrue(isBatchNumberFree(existing, 3))
        // Editing batch 2 must not report batch 2's own number as taken.
        assertTrue(isBatchNumberFree(existing, 2, ignoreId = "b2"))
    }

    // --- taking pieces out ---

    @Test
    fun `an order empties the oldest batch before touching the newer one`() {
        val ledger = listOf(batch(1, 30, remaining = 5), batch(2, 20, remaining = 20))
        val moves = allocateOldestFirst(ledger, 12)
        assertEquals(listOf(BatchMove("b1", 5), BatchMove("b2", 7)), moves)
        assertEquals(12, moves.sumOf { it.quantity })
    }

    @Test
    fun `an order larger than everything left allocates nothing at all`() {
        // Not a partial allocation: the caller's transaction is meant to fail whole rather
        // than half-fill an order and leave the ledger describing pieces nobody has.
        val ledger = listOf(batch(1, 30, remaining = 4), batch(2, 20, remaining = 3))
        assertEquals(emptyList<BatchMove>(), allocateOldestFirst(ledger, 8))
    }

    @Test
    fun `taking exactly what is left works`() {
        val ledger = listOf(batch(1, 30, remaining = 4), batch(2, 20, remaining = 3))
        assertEquals(7, allocateOldestFirst(ledger, 7).sumOf { it.quantity })
    }

    @Test
    fun `nothing is allocated for a zero or negative quantity`() {
        val ledger = listOf(batch(1, 30, remaining = 30))
        assertEquals(emptyList<BatchMove>(), allocateOldestFirst(ledger, 0))
        assertEquals(emptyList<BatchMove>(), allocateOldestFirst(ledger, -5))
    }

    // --- putting pieces back ---

    @Test
    fun `a cancellation refills the newest batch first`() {
        val ledger = listOf(batch(1, 30, remaining = 0), batch(2, 20, remaining = 15))
        val moves = returnNewestFirst(ledger, 8)
        assertEquals(listOf(BatchMove("b2", 5), BatchMove("b1", 3)), moves)
        assertEquals(8, moves.sumOf { it.quantity })
    }

    @Test
    fun `a batch is never refilled past the quantity it was made with`() {
        // The whole point: returning pieces must not invent stock that was never produced.
        val ledger = listOf(batch(1, 10, remaining = 8))
        val moves = returnNewestFirst(ledger, 50)
        assertEquals(2, moves.sumOf { it.quantity })
        assertEquals(10, ledger.first().quantity)
    }

    @Test
    fun `taking then returning the same amount restores every batch exactly`() {
        val ledger = listOf(batch(1, 30, remaining = 30), batch(2, 20, remaining = 20))
        val taken = allocateOldestFirst(ledger, 25)

        val afterTake = ledger.map { b ->
            b.copy(remaining = b.remaining - (taken.firstOrNull { it.batchId == b.id }?.quantity ?: 0))
        }
        assertEquals(25, stockFromBatches(ledger) - stockFromBatches(afterTake))

        val given = returnNewestFirst(afterTake, 25)
        val afterReturn = afterTake.map { b ->
            b.copy(remaining = b.remaining + (given.firstOrNull { it.batchId == b.id }?.quantity ?: 0))
        }
        assertEquals(stockFromBatches(ledger), stockFromBatches(afterReturn))
        assertTrue(reconcile(afterReturn, 50))
    }

    // --- the invariant ---

    @Test
    fun `stock reads as the sum of what is left, and a reset empties without deleting`() {
        val ledger = listOf(batch(1, 30, remaining = 6), batch(2, 20, remaining = 11))
        assertEquals(17, stockFromBatches(ledger))
        assertTrue(reconcile(ledger, 17))
        assertFalse(reconcile(ledger, 16))

        // A reset is `remaining = 0` on every row; the rows and their quantities survive,
        // which is what keeps the history of what was actually made.
        val afterReset = ledger.map { it.copy(remaining = 0) }
        assertEquals(0, stockFromBatches(afterReset))
        assertEquals(listOf(30, 20), afterReset.map { it.quantity })
        assertEquals(2, afterReset.size)
    }

    @Test
    fun `an unbatched size reads as zero rather than throwing`() {
        // Designs that predate batches have stock and no ledger. Callers treat an empty
        // ledger as "unbatched" and leave the stock column alone; this just must not crash.
        assertEquals(0, stockFromBatches(emptyList()))
        assertEquals(emptyList<BatchMove>(), allocateOldestFirst(emptyList(), 5))
        assertEquals(emptyList<BatchMove>(), returnNewestFirst(emptyList(), 5))
    }
}
