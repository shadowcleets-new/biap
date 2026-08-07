package com.sc.biap.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retention picks which image versions get destroyed, so the selection is worth pinning
 * down precisely rather than trusting a dropLast().
 */
class ImageStoreRetentionTest {

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
        val doomed = ImageStore.versionsToPrune(v, keep = 5, protectName = null)
        assertEquals(listOf("1000_aaaa.jpg", "2000_bbbb.jpg"), doomed)
    }

    @Test
    fun `retention default is five`() {
        assertEquals(5, ImageStore.VERSIONS_RETAINED)
    }

    @Test
    fun `prunes nothing when there are fewer than five versions`() {
        val doomed = ImageStore.versionsToPrune(v.take(4), keep = 5, protectName = null)
        assertTrue(doomed.isEmpty())
    }

    @Test
    fun `prunes nothing at exactly five versions`() {
        val doomed = ImageStore.versionsToPrune(v.take(5), keep = 5, protectName = null)
        assertTrue(doomed.isEmpty())
    }

    @Test
    fun `never deletes the live version even when it falls outside the newest five`() {
        // The design still points at the oldest object - it must survive regardless.
        val doomed = ImageStore.versionsToPrune(v, keep = 5, protectName = "1000_aaaa.jpg")
        assertEquals(listOf("2000_bbbb.jpg"), doomed)
        assertTrue("live version must never be pruned", "1000_aaaa.jpg" !in doomed)
    }

    @Test
    fun `input order does not matter - selection is chronological`() {
        val doomed = ImageStore.versionsToPrune(v.shuffled(), keep = 5, protectName = null)
        assertEquals(listOf("1000_aaaa.jpg", "2000_bbbb.jpg"), doomed)
    }

    @Test
    fun `a non-positive keep prunes nothing rather than deleting everything`() {
        assertTrue(ImageStore.versionsToPrune(v, keep = 0, protectName = null).isEmpty())
        assertTrue(ImageStore.versionsToPrune(v, keep = -1, protectName = null).isEmpty())
    }
}
