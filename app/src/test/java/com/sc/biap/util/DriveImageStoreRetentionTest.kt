package com.sc.biap.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retention picks which image versions get destroyed, so the selection is worth pinning
 * down precisely rather than trusting a dropLast().
 */
class DriveImageStoreRetentionTest {

    /** Version keys are "<millis>_<nonce>.jpg" - oldest first here. */
    private val v = listOf(
        "1000_aaaa.jpg",
        "2000_bbbb.jpg",
        "3000_cccc.jpg",
        "4000_dddd.jpg",
        "5000_eeee.jpg",
        "6000_ffff.jpg",
        "7000_gggg.jpg",
    )

    @Test
    fun `keeps the newest five and prunes the rest`() {
        val doomed = DriveImageStore.versionsToPrune(v, keep = 5, protectNames = emptySet())
        assertEquals(listOf("1000_aaaa.jpg", "2000_bbbb.jpg"), doomed)
    }

    @Test
    fun `retention default is five`() {
        assertEquals(5, DriveImageStore.VERSIONS_RETAINED)
    }

    @Test
    fun `prunes nothing when there are fewer than five versions`() {
        val doomed = DriveImageStore.versionsToPrune(v.take(4), keep = 5, protectNames = emptySet())
        assertTrue(doomed.isEmpty())
    }

    @Test
    fun `prunes nothing at exactly five versions`() {
        val doomed = DriveImageStore.versionsToPrune(v.take(5), keep = 5, protectNames = emptySet())
        assertTrue(doomed.isEmpty())
    }

    @Test
    fun `never deletes the live version even when it falls outside the newest five`() {
        // The design still points at the oldest object - it must survive regardless.
        val doomed = DriveImageStore.versionsToPrune(v, keep = 5, protectNames = setOf("1000_aaaa.jpg"))
        assertEquals(listOf("2000_bbbb.jpg"), doomed)
        assertTrue("live version must never be pruned", "1000_aaaa.jpg" !in doomed)
    }

    /**
     * The case that made this take a set rather than one name.
     *
     * A design carrying five photographs points at five keys at once. All of them share the
     * `img_<designId>_` prefix, so a sweep sees them as one history - and four of the five
     * would have been ordinary old names to delete. This is a live-photograph-loss test, not
     * a housekeeping one.
     */
    @Test
    fun `every live photo survives, not just the one just uploaded`() {
        val live = setOf("1000_aaaa.jpg", "2000_bbbb.jpg", "3000_cccc.jpg", "7000_gggg.jpg")
        val doomed = DriveImageStore.versionsToPrune(v, keep = 5, protectNames = live)

        assertTrue("no live photo may be pruned", doomed.none { it in live })
        // Three superseded versions remain, which is under the retention window, so nothing
        // is deleted at all.
        assertTrue(doomed.isEmpty())
    }

    /**
     * Protected names are excluded BEFORE the window is counted.
     *
     * Counted inside it, five live photos would fill the window on their own and every truly
     * superseded version would survive forever - retention that has quietly stopped retaining.
     */
    @Test
    fun `live photos do not consume the retention window`() {
        val many = v + listOf("8000_hhhh.jpg", "9000_iiii.jpg", "9500_jjjj.jpg")
        val live = setOf("9000_iiii.jpg", "9500_jjjj.jpg")

        val doomed = DriveImageStore.versionsToPrune(many, keep = 5, protectNames = live)

        // Eight superseded versions, five retained, so the three oldest go.
        assertEquals(listOf("1000_aaaa.jpg", "2000_bbbb.jpg", "3000_cccc.jpg"), doomed)
        assertTrue(doomed.none { it in live })
    }

    @Test
    fun `input order does not matter - selection is chronological`() {
        val doomed = DriveImageStore.versionsToPrune(v.shuffled(), keep = 5, protectNames = emptySet())
        assertEquals(listOf("1000_aaaa.jpg", "2000_bbbb.jpg"), doomed)
    }

    @Test
    fun `a non-positive keep prunes nothing rather than deleting everything`() {
        assertTrue(DriveImageStore.versionsToPrune(v, keep = 0, protectNames = emptySet()).isEmpty())
        assertTrue(DriveImageStore.versionsToPrune(v, keep = -1, protectNames = emptySet()).isEmpty())
    }
}
