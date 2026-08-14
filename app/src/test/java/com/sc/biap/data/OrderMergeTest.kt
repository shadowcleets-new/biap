package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @file OrderMergeTest.kt
 * @description The two things about orders that are expensive to get wrong.
 *
 * Both are about a cancelled order coming back. Cancelling returns pieces to stock, so an
 * order that reappears after being cancelled is not just a stale row - it is stock counted
 * twice, and nothing on screen would say so.
 */
class OrderMergeTest {

    private fun order(id: String, at: Long, qty: Int = 1) = Order(
        id = id,
        createdAt = at,
        lines = listOf(OrderLine("design-1", "L", qty, unitPrice = 115_000L)),
        updatedAt = at,
    )

    @Test
    fun `orders from both sides are kept`() {
        val local = BusinessIntelligenceData(orders = listOf(order("a", 100)))
        val remote = BusinessIntelligenceData(orders = listOf(order("b", 200)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(setOf("a", "b"), merged.orders.map { it.id }.toSet())
    }

    @Test
    fun `an order cancelled here does not return from the backup`() {
        // The device that cancelled it holds the tombstone; the backup still holds the order.
        val local = BusinessIntelligenceData(deletedOrders = mapOf("a" to 300L))
        val remote = BusinessIntelligenceData(orders = listOf(order("a", 100)))

        val merged = SyncMerger.merge(local, remote)

        assertTrue("cancelled order came back", merged.orders.isEmpty())
        assertTrue("tombstone was dropped", merged.deletedOrders.containsKey("a"))
    }

    @Test
    fun `a tombstone is pruned once nothing it refers to exists any more`() {
        // Nothing on either side carries the order, so the tombstone still has to survive -
        // another device may not have heard about the cancellation yet.
        val local = BusinessIntelligenceData(deletedOrders = mapOf("a" to 300L))
        val remote = BusinessIntelligenceData()

        val merged = SyncMerger.merge(local, remote)

        assertEquals(mapOf("a" to 300L), merged.deletedOrders)
    }

    @Test
    fun `designs still merge normally when orders are present`() {
        // Guards against the orders block quietly replacing part of the existing merge.
        val design = Design(id = "d", name = "Green Gola", category = "Anarkali", updatedAt = 500L)
        val local = BusinessIntelligenceData(
            designs = listOf(design),
            orders = listOf(order("a", 100)),
        )
        val remote = BusinessIntelligenceData(
            designs = listOf(design.copy(name = "Green Gola v2", updatedAt = 900L)),
        )

        val merged = SyncMerger.merge(local, remote)

        assertEquals("Green Gola v2", merged.designs.single().name)
        assertEquals(1, merged.orders.size)
    }

    @Test
    fun `an order totals its lines in paise and names its unpriced ones`() {
        val priced = Order(
            id = "a",
            createdAt = 1L,
            lines = listOf(
                OrderLine("d1", "L", quantity = 2, unitPrice = 115_000L),
                OrderLine("d2", "XL", quantity = 1, unitPrice = 90_000L),
            ),
        )
        assertEquals(320_000L, priced.total)
        assertEquals(3, priced.pieces)
        assertEquals(2, priced.designCount)
        assertFalse(priced.hasUnpricedLines)

        // A size added before its price was known contributes nothing rather than a guess,
        // and the order says so instead of presenting the short total as the real one.
        val partly = priced.copy(
            lines = priced.lines + OrderLine("d3", "5XL", quantity = 4, unitPrice = 0L)
        )
        assertEquals(320_000L, partly.total)
        assertEquals(7, partly.pieces)
        assertTrue(partly.hasUnpricedLines)
    }
}
