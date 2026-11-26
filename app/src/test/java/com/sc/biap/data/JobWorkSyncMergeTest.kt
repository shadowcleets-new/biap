package com.sc.biap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LWW merge semantics for JobWork records, deletion tombstones, and media pointer preservation.
 */
class JobWorkSyncMergeTest {

    private fun job(
        id: String = "j1",
        karigar: String = "Atul",
        sentAt: Long = 100L,
        updatedAt: Long = sentAt,
        receivedQuantity: Int = 0,
        photoPath: String? = null,
        photoRef: String? = null,
        voicePath: String? = null,
        voiceRef: String? = null,
    ) = JobWork(
        id = id,
        kind = WorkKind.SAMPLE,
        designName = "Pink Chikan",
        karigar = karigar,
        activity = "Embroidery",
        sentQuantity = 1,
        receivedQuantity = receivedQuantity,
        sentAt = sentAt,
        photoPath = photoPath,
        photoRef = photoRef,
        voicePath = voicePath,
        voiceRef = voiceRef,
        updatedAt = updatedAt,
    )

    @Test
    fun `jobWork from both sides are kept`() {
        val local = BusinessIntelligenceData(jobWork = listOf(job(id = "j1", updatedAt = 100L)))
        val remote = BusinessIntelligenceData(jobWork = listOf(job(id = "j2", updatedAt = 200L)))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(setOf("j1", "j2"), merged.jobWork.map { it.id }.toSet())
    }

    @Test
    fun `newer jobWork edit wins last-write-wins`() {
        val local = BusinessIntelligenceData(jobWork = listOf(job(id = "j1", receivedQuantity = 0, updatedAt = 100L)))
        val remote = BusinessIntelligenceData(jobWork = listOf(job(id = "j1", receivedQuantity = 1, updatedAt = 300L)))

        val merged = SyncMerger.merge(local, remote).jobWork.single()

        assertEquals(1, merged.receivedQuantity)
        assertEquals(300L, merged.updatedAt)
    }

    @Test
    fun `deletedJobWork tombstone drops older jobWork and keeps tombstone`() {
        val local = BusinessIntelligenceData(deletedJobWork = mapOf("j1" to 300L))
        val remote = BusinessIntelligenceData(jobWork = listOf(job(id = "j1", updatedAt = 100L)))

        val merged = SyncMerger.merge(local, remote)

        assertTrue(merged.jobWork.isEmpty())
        assertEquals(mapOf("j1" to 300L), merged.deletedJobWork)
    }

    @Test
    fun `jobWork updated after deletion survives and prunes older tombstone`() {
        val local = BusinessIntelligenceData(jobWork = listOf(job(id = "j1", updatedAt = 500L)))
        val remote = BusinessIntelligenceData(deletedJobWork = mapOf("j1" to 300L))

        val merged = SyncMerger.merge(local, remote)

        assertEquals(1, merged.jobWork.size)
        assertTrue(merged.deletedJobWork.isEmpty())
    }

    @Test
    fun `deletedJobWork tombstone survives when job is absent from both sides`() {
        val local = BusinessIntelligenceData(deletedJobWork = mapOf("j1" to 300L))
        val remote = BusinessIntelligenceData()

        val merged = SyncMerger.merge(local, remote)

        assertEquals(mapOf("j1" to 300L), merged.deletedJobWork)
    }

    @Test
    fun `remote status update preserves uploaded photoRef and voiceRef from losing side`() {
        val withMedia = job(
            id = "j1",
            receivedQuantity = 0,
            updatedAt = 150L,
            photoPath = "img_150.jpg",
            photoRef = "img_job_j1_150_abcd.enc",
            voicePath = "/files/voice_notes/vn_150.m4a",
            voiceRef = "vn_j1_150_efgh.enc",
        )
        val receivedWithoutMedia = job(
            id = "j1",
            receivedQuantity = 1,
            updatedAt = 250L,
            photoPath = null,
            photoRef = null,
            voicePath = null,
            voiceRef = null,
        )

        val forward = SyncMerger.merge(
            BusinessIntelligenceData(jobWork = listOf(withMedia)),
            BusinessIntelligenceData(jobWork = listOf(receivedWithoutMedia)),
        ).jobWork.single()
        val reverse = SyncMerger.merge(
            BusinessIntelligenceData(jobWork = listOf(receivedWithoutMedia)),
            BusinessIntelligenceData(jobWork = listOf(withMedia)),
        ).jobWork.single()

        for (merged in listOf(forward, reverse)) {
            assertEquals(1, merged.receivedQuantity)
            assertEquals("img_150.jpg", merged.photoPath)
            assertEquals("img_job_j1_150_abcd.enc", merged.photoRef)
            assertEquals("/files/voice_notes/vn_150.m4a", merged.voicePath)
            assertEquals("vn_j1_150_efgh.enc", merged.voiceRef)
        }
    }

    @Test
    fun `v9 tables merge deterministically alongside v11 jobWork`() {
        val design = Design(id = "d1", name = "Kurti", category = "Kurti", updatedAt = 100L)
        val batch = Batch(
            id = "b1",
            designId = "d1",
            size = "L",
            number = 1,
            quantity = 10,
            remaining = 8,
            price = 120_000L,
            createdAt = 100L,
            updatedAt = 200L,
        )
        val karigar = Karigar(id = "k1", name = "Atul", activities = listOf("Printing"), updatedAt = 200L)

        val local = BusinessIntelligenceData(
            designs = listOf(design),
            batches = listOf(batch),
            karigars = listOf(karigar),
            deletedKarigars = mapOf("k2" to 400L),
            activities = mapOf("Printing" to 200L),
            deletedActivities = mapOf("OldWork" to 500L),
            jobWork = listOf(job(id = "j1", updatedAt = 200L)),
        )
        val remote = BusinessIntelligenceData(
            karigars = listOf(Karigar(id = "k2", name = "Old", updatedAt = 100L)),
            activities = mapOf("OldWork" to 100L),
        )

        val merged = SyncMerger.merge(local, remote)

        assertEquals(listOf(batch), merged.batches)
        assertEquals(listOf(karigar), merged.karigars)
        assertFalse(merged.activities.containsKey("OldWork"))
        assertTrue(merged.deletedKarigars.containsKey("k2"))
        assertEquals(1, merged.jobWork.size)
    }
}
