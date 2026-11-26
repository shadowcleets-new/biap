package com.sc.biap.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies media retention and path matching across designs, JobWork attachments,
 * and Drive image/voice keys.
 */
class OrphanImageCleanupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `orphaned image cleanup spares job_work photoPath along with design photos`() {
        val filesDir = tempFolder.newFolder("files")
        val designCover = File(filesDir, "img_100.jpg").apply { writeText("cover") }
        val designExtra = File(filesDir, "img_200.jpg").apply { writeText("extra") }
        val jobSamplePhoto = File(filesDir, "img_300.jpg").apply { writeText("sample") }
        val jobAlterationPhoto = File(filesDir, "img_400.jpg").apply { writeText("alteration") }
        val profileAvatar = File(filesDir, "img_500.jpg").apply { writeText("profile") }
        val orphan = File(filesDir, "img_999.jpg").apply { writeText("orphan") }
        val unrelatedVoice = File(filesDir, "vn_123.m4a").apply { writeText("voice") }

        val activeRefs = collectActiveImageRefs(
            listOf(
                designCover.absolutePath,
                designExtra.name, // relative filename stored by copyUriToInternalStorage
                jobSamplePhoto.name, // relative filename stored on JobWork.photoPath
                jobAlterationPhoto.absolutePath,
                profileAvatar.name,
            )
        )

        val doomed = findOrphanedImageFiles(filesDir, activeRefs)

        assertEquals(listOf(orphan), doomed)
        assertTrue(designCover.exists())
        assertTrue(designExtra.exists())
        assertTrue(jobSamplePhoto.exists())
        assertTrue(jobAlterationPhoto.exists())
        assertTrue(profileAvatar.exists())
        assertTrue(unrelatedVoice.exists())
    }

    @Test
    fun `sameStoredPath matches relative filename against resolved absolute path`() {
        assertTrue(
            FileUtils.sameStoredPath(
                "img_12345.jpg",
                "/data/user/0/com.sc.biap/files/img_12345.jpg",
            )
        )
        assertTrue(
            FileUtils.sameStoredPath(
                "/data/user/0/com.sc.biap/files/img_12345.jpg",
                "img_12345.jpg",
            )
        )
        val dirA = tempFolder.newFolder("dirA")
        val dirB = tempFolder.newFolder("dirB")
        assertTrue(
            FileUtils.sameStoredPath(
                "img_12345.jpg",
                File(dirA, "img_12345.jpg").absolutePath,
            )
        )
        assertFalse(
            FileUtils.sameStoredPath(
                File(dirA, "img_12345.jpg").absolutePath,
                File(dirB, "img_12345.jpg").absolutePath,
            )
        )
        assertFalse(
            FileUtils.sameStoredPath(
                "/data/dirA/img_12345.jpg",
                "/data/dirB/img_12345.jpg",
            )
        )
        assertFalse(FileUtils.sameStoredPath(null, "img_12345.jpg"))
        assertFalse(FileUtils.sameStoredPath("img_12345.jpg", "img_67890.jpg"))
    }

    @Test
    fun `DriveImageStore distinguishes image and voice keys and caches with proper extensions`() {
        val designKey = DriveImageStore.newVersionKey("d1")
        val jobPhotoKey = DriveImageStore.newJobPhotoKey("j1")
        val voiceKey = DriveImageStore.newVoiceKey("j1")

        assertTrue(DriveImageStore.isDriveKey(designKey))
        assertTrue(DriveImageStore.isDriveKey(jobPhotoKey))
        assertTrue(DriveImageStore.isDriveKey(voiceKey))

        assertFalse(DriveImageStore.isVoiceKey(designKey))
        assertFalse(DriveImageStore.isVoiceKey(jobPhotoKey))
        assertTrue(DriveImageStore.isVoiceKey(voiceKey))

        assertFalse(DriveImageStore.isDriveKey("designs/d1/old.jpg"))

        val (photoDir, photoFile) = DriveImageStore.cacheLocation(jobPhotoKey)
        assertEquals("cloud_images", photoDir)
        assertTrue(photoFile.endsWith(".jpg"))

        val (voiceDir, voiceFile) = DriveImageStore.cacheLocation(voiceKey)
        assertEquals("cloud_voice", voiceDir)
        assertTrue(voiceFile.endsWith(".m4a"))
    }
}
