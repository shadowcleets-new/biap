package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Work that is with a karigar.
 *
 * Two things here are worth more than the rest. The short return - sending twenty and getting
 * eighteen - is ordinary and is exactly what goes unnoticed for months. And the direction of
 * stock: commissioning new pieces must NOT take anything off the shelf, because the pieces do
 * not exist yet, while an alteration must, because they are being handed over.
 */
class JobWorkTest {

    private val DAY = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun job(
        sent: Int,
        received: Int = 0,
        daysAgo: Int = 1,
        karigar: String = "Deen Mohd.",
        size: String = "L",
        closed: Long = 0L,
        kind: WorkKind = WorkKind.OUTWORK,
        lot: String? = null,
    ) = JobWork(
        id = "j-$karigar-$size-$daysAgo-$kind", kind = kind, lotId = lot ?: "j-$karigar-$size-$daysAgo-$kind",
        designId = "d1", size = size, karigar = karigar, activity = "Printing",
        sentQuantity = sent, receivedQuantity = received, sentAt = now - daysAgo * DAY,
        closedAt = closed,
    )

    // --- which way the stock moves ---

    @Test
    fun `commissioning new pieces takes nothing off the shelf`() {
        // The pieces do not exist yet. This is the mistake that made Send out refuse to work
        // on an empty size - the size most likely to need making.
        assertFalse(WorkKind.PRODUCTION.takesFromStockOnSend)
        assertFalse(WorkKind.SAMPLE.takesFromStockOnSend)
    }

    @Test
    fun `handing over finished pieces does take them off the shelf`() {
        assertTrue(WorkKind.ALTERATION.takesFromStockOnSend)
        assertTrue(WorkKind.OUTWORK.takesFromStockOnSend)
    }

    @Test
    fun `everything that comes back joins stock except a sample`() {
        assertTrue(WorkKind.PRODUCTION.addsToStockOnReceive)
        assertTrue(WorkKind.ALTERATION.addsToStockOnReceive)
        assertTrue(WorkKind.OUTWORK.addsToStockOnReceive)
        // One piece made to be judged, not sold.
        assertFalse(WorkKind.SAMPLE.addsToStockOnReceive)
    }

    // --- patience differs by kind ---

    @Test
    fun `three pieces for alteration are late long before a run of forty is`() {
        val alteration = job(3, daysAgo = 12, kind = WorkKind.ALTERATION)
        val production = job(40, daysAgo = 12, kind = WorkKind.PRODUCTION)
        assertTrue(alteration.isOverdue(now))
        assertFalse(production.isOverdue(now))
    }

    @Test
    fun `each kind uses its own threshold`() {
        assertEquals(10, WorkKind.ALTERATION.overdueDays)
        assertEquals(15, WorkKind.SAMPLE.overdueDays)
        assertEquals(30, WorkKind.PRODUCTION.overdueDays)
    }

    @Test
    fun `a closed lot is never overdue, however old`() {
        assertFalse(job(3, daysAgo = 400, kind = WorkKind.ALTERATION, closed = now).isOverdue(now))
    }

    @Test
    fun `a lot that is fully back is not overdue either`() {
        assertFalse(job(3, received = 3, daysAgo = 400, kind = WorkKind.ALTERATION).isOverdue(now))
    }

    // --- receiving ---

    @Test
    fun `receiving part of a consignment leaves the rest outstanding`() {
        val j = receive(job(20), 12, now)!!
        assertEquals(12, j.receivedQuantity)
        assertEquals(8, j.outstanding)
        assertFalse(j.isComplete)
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
        assertFalse(closed.isOpen)
        assertEquals(listOf(closed), shortfalls(listOf(closed)))
    }

    @Test
    fun `a rejected sample is not a loss of two pieces`() {
        // It was one piece, made to be judged, and the answer was no. Counting that as
        // missing stock would turn every rejected idea into a shortfall to explain.
        val sample = decideSample(job(1, kind = WorkKind.SAMPLE), approved = false, now)
        assertTrue(shortfalls(listOf(sample)).isEmpty())
    }

    @Test
    fun `closing an already closed job does not move the date`() {
        val once = close(job(20, received = 18), now)
        assertEquals(once, close(once, now + 5 * DAY))
    }

    // --- samples ---

    @Test
    fun `a sample waits to be judged and then stops waiting`() {
        val s = job(1, kind = WorkKind.SAMPLE)
        assertEquals(listOf(s), pendingSamples(listOf(s)))
        assertEquals(SampleOutcome.PENDING, s.outcome)

        val approved = decideSample(s, approved = true, now)
        assertEquals(SampleOutcome.APPROVED, approved.outcome)
        assertFalse(approved.isOpen)
        assertTrue(pendingSamples(listOf(approved)).isEmpty())
    }

    @Test
    fun `a sample carries its own name, having no design to point at`() {
        val s = JobWork(
            kind = WorkKind.SAMPLE, designName = "Pink chikan panel",
            karigar = "Atul", sentQuantity = 1, sentAt = now,
        )
        assertEquals("", s.designId)
        assertEquals("Pink chikan panel", s.titleOr(null))
        // A real design's name wins when there is one.
        assertEquals("Green gola", s.titleOr("Green gola"))
    }

    // --- lots ---

    @Test
    fun `the sizes of one order are one card`() {
        val rows = listOf(
            job(8, size = "L", kind = WorkKind.PRODUCTION, lot = "lot1"),
            job(8, size = "XL", kind = WorkKind.PRODUCTION, lot = "lot1"),
            job(4, size = "XXL", kind = WorkKind.PRODUCTION, lot = "lot1"),
        )
        val lots = openLots(rows, now)
        assertEquals(1, lots.size)
        assertEquals(20, lots.first().outstanding)
        assertEquals(3, lots.first().rows.size)
    }

    @Test
    fun `a single size job is simply a lot of one`() {
        assertEquals(1, openLots(listOf(job(5)), now).size)
    }

    @Test
    fun `overdue sorts above merely old`() {
        // A week-late alteration must not sit under a month-old production run that is fine.
        val lots = openLots(
            listOf(
                job(40, daysAgo = 20, kind = WorkKind.PRODUCTION, lot = "big"),
                job(2, daysAgo = 12, kind = WorkKind.ALTERATION, lot = "small"),
            ),
            now,
        )
        assertEquals("small", lots.first().lotId)
    }

    // --- what is out versus what is coming ---

    @Test
    fun `pieces being made are not counted as missing from the shelf`() {
        val jobs = listOf(
            job(10, size = "L", kind = WorkKind.PRODUCTION),
            job(3, size = "L", kind = WorkKind.ALTERATION),
        )
        // Only the alteration actually took pieces off the shelf.
        assertEquals(mapOf("L" to 3), outBySize(jobs, "d1"))
        assertEquals(mapOf("L" to 10), incomingBySize(jobs, "d1"))
    }

    @Test
    fun `a closed lot counts as neither out nor coming`() {
        val jobs = listOf(job(10, size = "L", kind = WorkKind.PRODUCTION, closed = now))
        assertTrue(outBySize(jobs, "d1").isEmpty())
        assertTrue(incomingBySize(jobs, "d1").isEmpty())
    }

    // --- the karigar board ---

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
    fun `one order of several sizes counts as one lot on the board, not several`() {
        val jobs = listOf(
            job(8, size = "L", kind = WorkKind.PRODUCTION, lot = "lot1"),
            job(8, size = "XL", kind = WorkKind.PRODUCTION, lot = "lot1"),
        )
        assertEquals(1, loadByKarigar(jobs, now).first().jobs)
        assertEquals(16, loadByKarigar(jobs, now).first().pieces)
    }

    @Test
    fun `an empty ledger answers emptily rather than failing`() {
        assertTrue(openJobs(emptyList()).isEmpty())
        assertTrue(openLots(emptyList(), now).isEmpty())
        assertTrue(loadByKarigar(emptyList(), now).isEmpty())
        assertTrue(outBySize(emptyList(), "d1").isEmpty())
    }
}
