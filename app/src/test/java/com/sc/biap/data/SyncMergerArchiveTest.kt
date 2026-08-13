package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The archive flag must merge independently of the rest of the design record.
 *
 * Same trap as the image pointer, with a worse outcome. Under whole-record last-write-wins,
 * whoever touched the record most recently drags their own archive state along - so editing a
 * price on a device that has not synced yet silently un-archives a design somebody retired,
 * and it reappears in the working catalogue with no trace of why.
 */
class SyncMergerArchiveTest {

    private fun design(
        id: String = "d1",
        updatedAt: Long,
        archivedAt: Long = 0L,
        archiveUpdatedAt: Long = 0L,
    ) = Design(
        id = id,
        name = "Green Panel",
        category = "Salwar Kameez",
        updatedAt = updatedAt,
        archivedAt = archivedAt,
        archiveUpdatedAt = archiveUpdatedAt,
    )

    private fun bi(vararg designs: Design) = BusinessIntelligenceData(designs = designs.toList())

    @Test
    fun `archiving survives a later edit made on a stale device`() {
        // Priya archives the design at t=200.
        val remote = bi(design(updatedAt = 100, archivedAt = 200, archiveUpdatedAt = 200))
        // Raj edits the price at t=300, on a device that never saw the archive.
        val local = bi(design(updatedAt = 300, archivedAt = 0, archiveUpdatedAt = 0))

        val merged = SyncMerger.merge(local, remote).designs.single()

        assertEquals("record should be Raj's, edited later", 300L, merged.updatedAt)
        assertTrue("but it must stay archived", merged.isArchived)
        assertEquals(200L, merged.archivedAt)
    }

    @Test
    fun `restoring later beats an older archive`() {
        val remote = bi(design(updatedAt = 100, archivedAt = 200, archiveUpdatedAt = 200))
        // Somebody deliberately brings it back at t=400.
        val local = bi(design(updatedAt = 100, archivedAt = 0, archiveUpdatedAt = 400))

        val merged = SyncMerger.merge(local, remote).designs.single()

        assertFalse("the newer restore wins", merged.isArchived)
        assertEquals(400L, merged.archiveUpdatedAt)
    }

    @Test
    fun `a design that never heard of archiving stays active`() {
        // An older build uploads a payload with no archive fields at all.
        val remote = bi(design(updatedAt = 500))
        val local = bi(design(updatedAt = 100))

        val merged = SyncMerger.merge(local, remote).designs.single()

        assertFalse(merged.isArchived)
        assertEquals(0L, merged.archiveUpdatedAt)
    }

    @Test
    fun `archiving does not resurrect a deleted design`() {
        // Deletion still wins over everything: the tombstone is newer than the record.
        val remote = BusinessIntelligenceData(
            designs = listOf(design(updatedAt = 100, archivedAt = 300, archiveUpdatedAt = 300)),
            deletedDesigns = mapOf("d1" to 400L),
        )
        val merged = SyncMerger.merge(bi(), remote)

        assertTrue("design should be gone", merged.designs.isEmpty())
    }
}
