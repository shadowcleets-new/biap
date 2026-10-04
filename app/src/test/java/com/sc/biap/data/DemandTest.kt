package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the records back as advice.
 *
 * The risk here is not a crash, it is confident nonsense: a suggestion that looks considered
 * and is wrong sends real cloth to the cutting table. So the tests pin the arithmetic, and
 * pin the cases where there is NOT enough history to say anything.
 */
class DemandTest {

    private val DAY = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun design(vararg stock: Pair<String, Int>) = Design(
        id = "d1", name = "Yellow bouti", category = "Salwar Kameez",
        sizeInventories = stock.map { SizeInventory(it.first, it.second, 115_000L) },
    )

    private fun batch(size: String, qty: Int, at: Long, remaining: Int = 0) = Batch(
        id = "$size-$at", designId = "d1", size = size, number = 1,
        quantity = qty, remaining = remaining, price = 115_000L, createdAt = at,
    )

    private fun order(at: Long, vararg lines: Pair<String, Int>) = Order(
        id = "o$at", createdAt = at,
        lines = lines.map { OrderLine("d1", it.first, it.second, 115_000L) },
    )

    // --- production history ---

    @Test
    fun `sizes cut on the same day are one trip to the table, not five`() {
        val h = productionHistory(listOf(
            batch("L", 5, now - 10 * DAY), batch("XL", 5, now - 10 * DAY),
            batch("XXL", 5, now - 10 * DAY),
        ))
        assertEquals(1, h.runs)
        assertEquals(15, h.totalMade)
    }

    @Test
    fun `cutting the same design again months later is a second run`() {
        val h = productionHistory(listOf(
            batch("L", 5, now - 90 * DAY), batch("L", 5, now - 10 * DAY),
        ))
        assertEquals(2, h.runs)
        assertEquals(now - 10 * DAY, h.lastMadeAt)
        assertEquals(now - 90 * DAY, h.firstMadeAt)
    }

    @Test
    fun `a design never made says nothing rather than zero`() {
        val h = productionHistory(emptyList())
        assertEquals(0, h.runs)
        assertNull(h.lastMadeAt)
    }

    // --- size demand ---

    @Test
    fun `sold comes from orders, not from stock going down`() {
        // Stock fell by 8 but only 3 were ordered: the rest was a correction, not demand.
        val d = sizeDemand(
            design("L" to 2),
            listOf(batch("L", 10, now - 30 * DAY, remaining = 2)),
            listOf(order(now - 5 * DAY, "L" to 3)),
        )
        assertEquals(10, d.first().made)
        assertEquals(3, d.first().sold)
    }

    @Test
    fun `a size standing at zero is flagged, because its sold figure is a floor`() {
        val d = sizeDemand(design("L" to 0, "3XL" to 4), emptyList(), listOf(order(now, "L" to 9)))
        assertTrue(d.first { it.size == "L" }.soldOut)
        assertTrue(!d.first { it.size == "3XL" }.soldOut)
    }

    @Test
    fun `a size the design no longer lists is still counted if it was once sold`() {
        val d = sizeDemand(design("L" to 3), emptyList(), listOf(order(now, "XXL" to 2)))
        assertTrue(d.any { it.size == "XXL" && it.sold == 2 })
    }

    // --- the cut suggestion ---

    @Test
    fun `the suggestion adds up to exactly what was asked for`() {
        for (total in listOf(1, 7, 25, 40, 100)) {
            val d = sizeDemand(
                design("L" to 0, "XL" to 1, "XXL" to 4, "3XL" to 6),
                emptyList(),
                listOf(order(now, "L" to 18, "XL" to 9, "XXL" to 4, "3XL" to 1)),
            )
            assertEquals(total, suggestCut(d, total).sumOf { it.pieces })
        }
    }

    @Test
    fun `the size that actually sold gets the most pieces`() {
        val d = sizeDemand(
            design("L" to 0, "XL" to 2, "XXL" to 4, "3XL" to 6),
            emptyList(),
            listOf(order(now, "L" to 18, "XL" to 9, "XXL" to 4, "3XL" to 1)),
        )
        val cut = suggestCut(d, 40).associate { it.size to it.pieces }
        assertTrue("L should lead", cut["L"]!! > cut["XL"]!!)
        assertTrue(cut["XL"]!! > cut["XXL"]!!)
        assertTrue(cut["XXL"]!! > cut["3XL"]!!)
    }

    @Test
    fun `a size that sold even once is never cut to nothing`() {
        val d = sizeDemand(
            design("L" to 0, "3XL" to 9),
            emptyList(),
            listOf(order(now, "L" to 60, "3XL" to 1)),
        )
        assertTrue(suggestCut(d, 20).first { it.size == "3XL" }.pieces >= 1)
    }

    @Test
    fun `with nothing sold it keeps the even split rather than inventing a preference`() {
        val d = sizeDemand(design("L" to 5, "XL" to 5, "XXL" to 5), emptyList(), emptyList())
        val cut = suggestCut(d, 15)
        assertTrue(cut.all { it.pieces == 5 })
    }

    @Test
    fun `fewer pieces than sizes still adds up and does not go negative`() {
        val d = sizeDemand(
            design("L" to 0, "XL" to 0, "XXL" to 0, "3XL" to 0),
            emptyList(),
            listOf(order(now, "L" to 5, "XL" to 4, "XXL" to 3, "3XL" to 2)),
        )
        val cut = suggestCut(d, 2)
        assertEquals(2, cut.sumOf { it.pieces })
        assertTrue(cut.all { it.pieces >= 0 })
    }

    @Test
    fun `asking for nothing returns nothing`() {
        val d = sizeDemand(design("L" to 1), emptyList(), emptyList())
        assertTrue(suggestCut(d, 0).isEmpty())
        assertTrue(suggestCut(emptyList(), 10).isEmpty())
    }

    // --- pace ---

    @Test
    fun `pace counts from when it was first made, including time nobody wanted it`() {
        // 12 sold, first cut six months ago: two a month, not twelve.
        val p = designPace(
            design("L" to 3),
            listOf(batch("L", 15, now - 180 * DAY, remaining = 3)),
            listOf(order(now - 10 * DAY, "L" to 12)),
            now,
        )
        assertEquals(12, p.sold)
        assertEquals(2.0, p.soldPerMonth, 0.2)
        assertEquals(10, p.daysSinceLastSale)
    }

    @Test
    fun `a design cut days ago is not yet called slow`() {
        // Too new to have a rate. Reporting 0 a month here would read as "nobody wants it".
        val p = designPace(
            design("L" to 5), listOf(batch("L", 5, now - 2 * DAY, remaining = 5)), emptyList(), now,
        )
        assertEquals(0.0, p.soldPerMonth, 0.001)
        assertNull(p.daysSinceLastSale)
    }

    @Test
    fun `a design that has never sold reports no last sale rather than day zero`() {
        val p = designPace(
            design("L" to 5), listOf(batch("L", 5, now - 200 * DAY, remaining = 5)), emptyList(), now,
        )
        assertNull(p.daysSinceLastSale)
        assertEquals(200, p.daysSinceLastRun)
    }
}
