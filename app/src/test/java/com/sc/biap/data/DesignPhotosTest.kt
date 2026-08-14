package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @file DesignPhotosTest.kt
 * @description The cover/extras split, which callers are not supposed to be able to see.
 *
 * [Design.photos] and [Design.withPhotos] are the only two places that know the first photo
 * lives in different columns from the rest. Everything else works with one list, so these two
 * carry the whole illusion - and a mistake in either loses photographs rather than misplacing
 * them.
 */
class DesignPhotosTest {

    /**
     * `updatedAt` is pinned rather than left to default. Design() stamps it from the real
     * clock, which in a merge test dwarfs any timestamp written here - the record under test
     * would win every comparison for a reason that has nothing to do with what is being
     * asserted.
     */
    private fun design(vararg photos: DesignPhoto) =
        Design(id = "d", name = "Green gola", category = "Salwar Kameez", updatedAt = 1_000L)
            .withPhotos(photos.toList(), now = 1_000L)

    @Test
    fun `the first photo becomes the cover and the rest become extras`() {
        val d = design(
            DesignPhoto(localPath = "a.jpg"),
            DesignPhoto(localPath = "b.jpg"),
            DesignPhoto(localPath = "c.jpg"),
        )

        assertEquals("a.jpg", d.imagePath)
        assertEquals(2, d.extraImages.size)
        assertEquals(listOf("b.jpg", "c.jpg"), d.extraImages.map { it.localPath })
    }

    @Test
    fun `photos reads back exactly what was written, cover first`() {
        val given = listOf(
            DesignPhoto(localPath = "a.jpg", ref = "img_a"),
            DesignPhoto(localPath = "b.jpg"),
            DesignPhoto(ref = "img_c", uploadedBy = "someone-else"),
        )
        assertEquals(given, design(*given.toTypedArray()).photos)
    }

    @Test
    fun `a design with no photos has an empty list rather than one blank entry`() {
        val d = Design(id = "d", name = "n", category = "c")
        assertTrue(d.photos.isEmpty())
        assertNull(d.imagePath)
    }

    @Test
    fun `photos beyond the cap are dropped rather than silently stored`() {
        val many = (1..8).map { DesignPhoto(localPath = "$it.jpg") }
        val d = design(*many.toTypedArray())

        assertEquals(Design.MAX_PHOTOS, d.photos.size)
        assertEquals(listOf("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg"), d.photos.map { it.localPath })
    }

    @Test
    fun `removing the cover promotes the next photo`() {
        val d = design(DesignPhoto(localPath = "a.jpg"), DesignPhoto(localPath = "b.jpg"))
        val without = d.withPhotos(d.photos.drop(1), now = 2_000L)

        assertEquals("b.jpg", without.imagePath)
        assertTrue(without.extraImages.isEmpty())
    }

    @Test
    fun `clearing every photo empties the cover columns too`() {
        val d = design(DesignPhoto(localPath = "a.jpg"), DesignPhoto(localPath = "b.jpg"))
        val none = d.withPhotos(emptyList(), now = 2_000L)

        assertTrue(none.photos.isEmpty())
        assertNull(none.imagePath)
        assertNull(none.imageRef)
        assertTrue(none.extraImages.isEmpty())
    }

    /**
     * The timestamps are what sync resolves on, so a clock that moves when its own half did
     * not is how one device's edit silently discards another's.
     */
    @Test
    fun `adding a second photo does not touch the cover's clock`() {
        val one = design(DesignPhoto(localPath = "a.jpg"))
        val two = one.withPhotos(one.photos + DesignPhoto(localPath = "b.jpg"), now = 9_000L)

        assertEquals("cover was not replaced", one.imageUpdatedAt, two.imageUpdatedAt)
        assertEquals("extras did change", 9_000L, two.extraImagesUpdatedAt)
    }

    @Test
    fun `replacing the cover does not touch the extras' clock`() {
        val d = design(DesignPhoto(localPath = "a.jpg"), DesignPhoto(localPath = "b.jpg"))
        val swapped = d.withPhotos(
            listOf(DesignPhoto(localPath = "new.jpg")) + d.photos.drop(1),
            now = 9_000L,
        )

        assertEquals("cover was replaced", 9_000L, swapped.imageUpdatedAt)
        assertEquals("extras are unchanged", d.extraImagesUpdatedAt, swapped.extraImagesUpdatedAt)
    }

    @Test
    fun `rewriting the same photos moves neither clock`() {
        val d = design(DesignPhoto(localPath = "a.jpg"), DesignPhoto(localPath = "b.jpg"))
        val same = d.withPhotos(d.photos, now = 9_000L)

        assertEquals(d.imageUpdatedAt, same.imageUpdatedAt)
        assertEquals(d.extraImagesUpdatedAt, same.extraImagesUpdatedAt)
    }

    @Test
    fun `a newly picked photo carries no ref, which is what marks it for upload`() {
        val d = design(DesignPhoto(localPath = "old.jpg", ref = "img_old"))
        val replaced = d.withPhotos(listOf(DesignPhoto(localPath = "new.jpg")), now = 2_000L)

        assertNull("a fresh photo must not inherit the old ref", replaced.imageRef)
        assertEquals("new.jpg", replaced.imagePath)
    }

    /**
     * The extras merge on their own clock, like the cover pointer and the archive flag.
     * Without this, editing a price on a device holding a stale photo list would drag that
     * list along and delete photographs added elsewhere.
     */
    @Test
    fun `a newer photo list survives an older device's price edit`() {
        val base = design(DesignPhoto(localPath = "a.jpg"))

        val photoAdded = base.withPhotos(
            base.photos + DesignPhoto(localPath = "b.jpg"),
            now = 5_000L,
        )
        // Another device edits something unrelated LATER, still holding the one-photo list.
        val priceEdited = base.copy(name = "Green gola v2", updatedAt = 9_000L)

        val merged = SyncMerger.merge(
            BusinessIntelligenceData(designs = listOf(priceEdited)),
            BusinessIntelligenceData(designs = listOf(photoAdded)),
        ).designs.single()

        assertEquals("the later record still wins the name", "Green gola v2", merged.name)
        assertEquals("but not the photographs", 1, merged.extraImages.size)
        assertEquals("b.jpg", merged.extraImages.single().localPath)
    }

    @Test
    fun `the later photo list wins, and lists are not unioned`() {
        // A union would reinstate photos somebody deliberately removed, and could push a
        // design past MAX_PHOTOS.
        val base = design(DesignPhoto(localPath = "a.jpg"))
        val here = base.withPhotos(base.photos + DesignPhoto(localPath = "b.jpg"), now = 2_000L)
        val there = base.withPhotos(base.photos + DesignPhoto(localPath = "c.jpg"), now = 5_000L)

        val merged = SyncMerger.merge(
            BusinessIntelligenceData(designs = listOf(here)),
            BusinessIntelligenceData(designs = listOf(there)),
        ).designs.single()

        assertEquals(listOf("c.jpg"), merged.extraImages.map { it.localPath })
    }
}
