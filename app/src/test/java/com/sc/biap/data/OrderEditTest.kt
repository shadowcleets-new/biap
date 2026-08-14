package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @file OrderEditTest.kt
 * @description Editing an order: how much stock moves, and what the pieces are worth.
 *
 * Both of these fail silently rather than loudly. A wrong sign or a missed key in
 * [stockDeltas] leaves the catalogue claiming stock that has already gone out, or hiding
 * stock that never left - and nothing on screen would say so. A slip in
 * [keepRecordedPrices] quietly revalues a sale that happened months ago.
 */
class OrderEditTest {

    private fun line(size: String, qty: Int, price: Long = 115_000L) =
        OrderLine(designId = "green-gola", size = size, quantity = qty, unitPrice = price)

    // #region stock deltas

    @Test
    fun `raising a quantity takes only the difference out`() {
        // 3 pieces already gone; the order becomes 5. Two more leave the shelf, not five.
        val deltas = stockDeltas(listOf(line("L", 3)), listOf(line("L", 5)))
        assertEquals(mapOf(("green-gola" to "L") to 2), deltas)
    }

    @Test
    fun `lowering a quantity puts only the difference back`() {
        val deltas = stockDeltas(listOf(line("L", 5)), listOf(line("L", 2)))
        assertEquals(mapOf(("green-gola" to "L") to -3), deltas)
    }

    @Test
    fun `an untouched size does not move at all`() {
        // Present as zero it would still hit the database, bumping the design's timestamp
        // and putting it in the way of a genuine edit made elsewhere.
        val deltas = stockDeltas(
            listOf(line("L", 3), line("XL", 2)),
            listOf(line("L", 3), line("XL", 4)),
        )
        assertEquals(mapOf(("green-gola" to "XL") to 2), deltas)
        assertTrue(("green-gola" to "L") !in deltas)
    }

    @Test
    fun `a size added to the order comes entirely out of stock`() {
        val deltas = stockDeltas(listOf(line("L", 3)), listOf(line("L", 3), line("XXL", 2)))
        assertEquals(mapOf(("green-gola" to "XXL") to 2), deltas)
    }

    @Test
    fun `a size dropped from the order goes entirely back`() {
        val deltas = stockDeltas(listOf(line("L", 3), line("XXL", 2)), listOf(line("L", 3)))
        assertEquals(mapOf(("green-gola" to "XXL") to -2), deltas)
    }

    @Test
    fun `sizes moving in both directions at once are each handled`() {
        val deltas = stockDeltas(
            listOf(line("L", 5), line("XL", 1)),
            listOf(line("L", 2), line("XL", 4)),
        )
        assertEquals(
            mapOf(("green-gola" to "L") to -3, ("green-gola" to "XL") to 3),
            deltas,
        )
    }

    @Test
    fun `the same size twice on one order is counted once`() {
        val deltas = stockDeltas(
            listOf(line("L", 2), line("L", 1)),
            listOf(line("L", 5)),
        )
        assertEquals(mapOf(("green-gola" to "L") to 2), deltas)
    }

    @Test
    fun `designs are kept apart even at the same size`() {
        val other = OrderLine("blue-patti", "L", 4, 100_000L)
        val deltas = stockDeltas(listOf(line("L", 3)), listOf(line("L", 3), other))
        assertEquals(mapOf(("blue-patti" to "L") to 4), deltas)
    }

    @Test
    fun `an unchanged order moves nothing`() {
        val same = listOf(line("L", 3), line("XL", 2))
        assertTrue(stockDeltas(same, same).isEmpty())
    }

    // #endregion

    // #region prices

    /**
     * The rule the owner asked for by name.
     *
     * Green Gola size L sold at ₹1,150 in June. A later batch pushed the catalogue to ₹1,500
     * in August. Editing the June order in August must still bill every piece on it at
     * ₹1,150 - the sale happened at June's price and cannot be revalued by a later batch.
     */
    @Test
    fun `an existing line keeps the price it was sold at, however many pieces it now carries`() {
        val june = listOf(line("L", 2, price = 115_000L))
        val edited = listOf(line("L", 3, price = 150_000L))

        val priced = keepRecordedPrices(edited, june).single()

        assertEquals("must not take August's price", 115_000L, priced.unitPrice)
        assertEquals(3, priced.quantity)
        assertEquals("2 at June's rate plus a third", 345_000L, priced.quantity * priced.unitPrice)
    }

    @Test
    fun `a size the order never had is left unpriced for the caller to fill from the catalogue`() {
        // There is no historic price for something that was never on this order, so today's
        // is the only honest answer - and it is the repository, holding the catalogue, that
        // supplies it. Zero here means "not decided yet", never "free".
        val june = listOf(line("L", 2, price = 115_000L))
        val edited = listOf(line("L", 2, price = 115_000L), line("XL", 1, price = 0L))

        val priced = keepRecordedPrices(edited, june)

        assertEquals(115_000L, priced.first { it.size == "L" }.unitPrice)
        assertEquals(0L, priced.first { it.size == "XL" }.unitPrice)
    }

    @Test
    fun `dropping a line does not disturb the prices of the ones that remain`() {
        val before = listOf(line("L", 2, price = 115_000L), line("XL", 1, price = 122_500L))
        val priced = keepRecordedPrices(listOf(line("XL", 1, price = 999_999L)), before)

        assertEquals(122_500L, priced.single().unitPrice)
    }

    @Test
    fun `prices are matched per design, not per size`() {
        val recorded = listOf(OrderLine("blue-patti", "L", 1, 100_000L))
        val edited = listOf(line("L", 1, price = 0L))

        // Same size string, different design - it must NOT inherit blue-patti's price.
        assertEquals(0L, keepRecordedPrices(edited, recorded).single().unitPrice)
    }

    // #endregion
}
