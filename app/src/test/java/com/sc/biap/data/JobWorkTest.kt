package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goods out with karigars.
 *
 * The case that matters is the short return. Sending twenty and getting eighteen back is
 * ordinary and is exactly what goes unnoticed for months, so the tests pin that the two
 * missing pieces stay visible rather than being tidied away by arithmetic.
 */
class JobWorkTest {

    private val DAY = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun job(sent: Int, received: Int = 0, daysAgo: Int = 1, karigar: String = "Deen Mohd.",
                    size: String = "L", closed: Long = 0L) = JobWork(
        id = "j-$karigar-$size-$daysAgo", designId = "d1", size = size, karigar = karigar,
        activity = "Printing", sentQuantity = sent, receivedQuantity = received,
        sentAt = now - daysAgo * DAY, closedAt = closed,
    )

    // --- receiving ---

    @Test
    fun `receiving part of a consignment leaves the rest outstanding`() {
        val j = receive(job(20), 12, now)!!
        assertEquals(12, j.receivedQuantity)
        assertEquals(8, j.outstanding)
        assertTrue(!j.isComplete)
    }

    @Test
    fun `receiving the rest completes it`() {
        val j = receive(job(20, received = 12), 8, now)!!
        assertEquals(0, j.outstanding)
        assertTrue(j.isComplete)
    }

    @Test
    fun `receiving more than was sent is refused, not clamped`() {
        // Clamping would put stock on the shelf that never existed.
        assertNull(receive(job(20), 21, now))
        assertNull(receive(job(20, received = 18), 3, now))
    }

    @Test
    fun `receiving nothing or a negative count is refused`() {
        assertNull(receive(job(20), 0, now))
        assertNull(receive(job(20), -5, now))
    }

    // --- the short return ---

    @Test
    fun `an open job short of pieces reports no shortfall yet`() {
        // Still out is not yet lost. The owner has not said they are not coming.
        assertEquals(0, job(20, received = 18).shortfall)
    }

    @Test
    fun `closing a short job keeps the missing pieces visible`() {
        val closed = close(job(20, received = 18), now)
        assertEquals(2, closed.shortfall)
        assertTrue(!closed.isOpen)
        assertEquals(listOf(closed), shortfalls(listOf(closed)))
    }

    @Test
    fun `a job that came back whole shows no shortfall when closed`() {
        assertEquals(0, close(job(20, received = 20), now).shortfall)
        assertTrue(shortfalls(listOf(close(job(20, received = 20), now))).isEmpty())
    }

    @Test
    fun `closing an already closed job does not move the date`() {
        val once = close(job(20, received = 18), now)
        assertEquals(once, close(once, now + 5 * DAY))
    }

    // --- where are my goods ---

    @Test
    fun `only genuinely outstanding work counts as out`() {
        val jobs = listOf(
            job(20, received = 20),                      // all back
            job(10, received = 4),                       // 6 out
            job(5, received = 0, closed = now),          // closed, written off
            job(8, received = 0),                        // 8 out
        )
        assertEquals(2, openJobs(jobs).size)
        assertEquals(14, openJobs(jobs).sumOf { it.outstanding })
    }

    @Test
    fun `the load board puts the longest wait first, not the biggest`() {
        val jobs = listOf(
            job(50, daysAgo = 2, karigar = "Atul"),
            job(6, daysAgo = 30, karigar = "Israr"),
        )
        val board = loadByKarigar(jobs, now)
        assertEquals("Israr", board.first().karigar)
        assertEquals(30, board.first().oldestDaysOut)
    }

    @Test
    fun `one karigar's several consignments add up to one line`() {
        val jobs = listOf(
            job(10, daysAgo = 5, size = "L"),
            job(10, daysAgo = 9, size = "XL"),
            job(4, received = 4, daysAgo = 3, size = "XXL"),
        )
        val board = loadByKarigar(jobs, now)
        assertEquals(1, board.size)
        assertEquals(20, board.first().pieces)
        assertEquals(2, board.first().jobs)
        assertEquals(9, board.first().oldestDaysOut)
    }

    @Test
    fun `overdue is counted from when it was sent`() {
        val jobs = listOf(job(10, daysAgo = 3), job(10, daysAgo = 19, karigar = "Atul"))
        assertEquals(listOf("Atul"), overdueJobs(jobs, now).map { it.karigar })
    }

    @Test
    fun `a design reports what is out by size, so the shelf can say where they went`() {
        val jobs = listOf(
            job(10, received = 2, size = "L"),
            job(6, size = "L"),
            job(4, size = "XL"),
            job(9, size = "XXL", closed = now),
        )
        assertEquals(mapOf("L" to 14, "XL" to 4), outBySize(jobs, "d1"))
    }

    @Test
    fun `an empty ledger answers emptily rather than failing`() {
        assertTrue(openJobs(emptyList()).isEmpty())
        assertTrue(loadByKarigar(emptyList(), now).isEmpty())
        assertTrue(outBySize(emptyList(), "d1").isEmpty())
    }
}
