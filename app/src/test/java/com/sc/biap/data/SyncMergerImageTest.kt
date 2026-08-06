package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The image pointer must merge independently of the rest of the design record.
 *
 * Whole-record last-write-wins is what makes this go wrong: whoever touched the record most
 * recently drags their own (possibly stale) imageRef along with them, silently discarding a
 * newer photo someone else uploaded.
 */
class SyncMergerImageTest {

    private fun design(
        id: String = "d1",
        name: String = "Anarkali",
        updatedAt: Long,
        imageRef: String?,
        imageUpdatedAt: Long,
    ) = Design(
        id = id,
        name = name,
        category = "Kurti",
        updatedAt = updatedAt,
        imageRef = imageRef,
        imageUpdatedAt = imageUpdatedAt,
    )

    private fun bi(vararg designs: Design) = BusinessIntelligenceData(designs = designs.toList())

    @Test
    fun `newer photo survives an edit made afterwards to the rest of the record`() {
        // Priya uploads a better photo at t=200, touching nothing else.
        val remote = bi(design(updatedAt = 100, imageRef = "designs/d1/new.jpg", imageUpdatedAt = 200))
        // Raj edits the price at t=300 on a device that still has the OLD photo.
        val local = bi(design(updatedAt = 300, imageRef = "designs/d1/old.jpg", imageUpdatedAt = 50))

        val merged = SyncMerger.merge(local, remote).designs.single()

        assertEquals("record should be Raj's (edited later)", 300L, merged.updatedAt)
        assertEquals("photo should be Priya's (uploaded later)", "designs/d1/new.jpg", merged.imageRef)
        assertEquals(200L, merged.imageUpdatedAt)
    }

    @Test
    fun `newest photo wins even when its record lost`() {
        val local = bi(design(updatedAt = 500, imageRef = "designs/d1/a.jpg", imageUpdatedAt = 10))
        val remote = bi(design(updatedAt = 100, imageRef = "designs/d1/b.jpg", imageUpdatedAt = 900))

        val merged = SyncMerger.merge(local, remote).designs.single()

        assertEquals(500L, merged.updatedAt)
        assertEquals("designs/d1/b.jpg", merged.imageRef)
    }

    @Test
    fun `record winner keeps its own photo when it is also the newest`() {
        val local = bi(design(updatedAt = 500, imageRef = "designs/d1/a.jpg", imageUpdatedAt = 500))
        val remote = bi(design(updatedAt = 100, imageRef = "designs/d1/b.jpg", imageUpdatedAt = 100))

        val merged = SyncMerger.merge(local, remote).designs.single()

        assertEquals("designs/d1/a.jpg", merged.imageRef)
        assertEquals(500L, merged.imageUpdatedAt)
    }

    @Test
    fun `designs with no image at all are unaffected`() {
        val local = bi(design(updatedAt = 500, imageRef = null, imageUpdatedAt = 0))
        val remote = bi(design(updatedAt = 100, imageRef = null, imageUpdatedAt = 0))

        val merged = SyncMerger.merge(local, remote).designs.single()

        assertEquals(null, merged.imageRef)
        assertEquals(500L, merged.updatedAt)
    }
}
