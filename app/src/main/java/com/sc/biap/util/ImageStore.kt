package com.sc.biap.util

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * @file ImageStore.kt
 * @description Versioned image storage on Firebase Storage.
 *
 * Design photos are MUTABLE from the user's point of view - replace a photo, upload a
 * better-quality one, re-shoot it - but the stored objects are write-once. Replacing an
 * image writes a NEW key and the design's `imageRef` is repointed at it.
 *
 * That single decision is what makes the mutable behaviour safe:
 *
 *  - A failed or interrupted upload cannot destroy the existing photo. The old object is
 *    still there and `imageRef` still points at it; nothing changed.
 *  - Two people replacing the same photo at once cannot corrupt each other. Both uploads
 *    succeed to different keys, and the winner is decided by an ordinary timestamp compare
 *    on one field. The loser's image still exists.
 *  - On-device image caches keep working. Caches key on URL, so overwriting a fixed name
 *    like main.jpg would leave every device serving last month's photo with no way to know
 *    it was stale. A new key per version changes the URL, so the cache misses and refetches.
 *  - A bad replacement is recoverable, because the previous object was never deleted.
 *
 * Images are uploaded as-is. No compression or re-encoding is applied.
 */
object ImageStore {

    private const val ROOT = "designs"

    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()

    /**
     * A fresh, never-reused key for a new version of a design's image.
     * Time prefix keeps versions ordered when listing; the random suffix removes any chance
     * of collision when two devices upload within the same millisecond.
     */
    fun newVersionKey(designId: String, extension: String = "jpg"): String {
        val stamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString().take(8)
        return "$ROOT/$designId/${stamp}_$nonce.$extension"
    }

    fun refFor(key: String): StorageReference = storage.getReference(key)

    /**
     * Upload [localFile] to [key]. Returns true on success.
     *
     * The caller must only repoint `imageRef` AFTER this returns true - that ordering is
     * what guarantees a failed upload leaves the previous image intact and reachable.
     */
    suspend fun upload(localFile: File, key: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!localFile.exists()) return@withContext false
            refFor(key).putFile(Uri.fromFile(localFile)).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Resolve a storage key to a URL for loading. Safe to cache: the key is immutable, so
     * the bytes behind it can never change.
     */
    suspend fun resolveUrl(key: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            refFor(key).downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Delete a superseded version. Only ever called for a key that is no longer referenced
     * by any design; never called as part of a replacement, so a replacement can always be
     * rolled back to the previous object.
     */
    suspend fun deleteVersion(key: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            refFor(key).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** How many versions of a design's image are retained. Older ones are pruned. */
    const val VERSIONS_RETAINED = 5

    /**
     * Prune a design's image history to the newest [keep] versions.
     *
     * [protectKey] - the version the design currently points at - is NEVER deleted, even if
     * it somehow falls outside the newest [keep]. Losing the live image to a retention sweep
     * would be far worse than keeping one extra object.
     *
     * Pruning is best-effort and deliberately non-fatal: it runs after a successful upload
     * and pointer move, so a failure here leaves extra objects behind but never breaks a
     * design. Returns the number of versions actually deleted.
     */
    suspend fun pruneToLatest(
        designId: String,
        keep: Int = VERSIONS_RETAINED,
        protectKey: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val items = storage.getReference("$ROOT/$designId").listAll().await().items
            val doomedNames = versionsToPrune(
                names = items.map { it.name },
                keep = keep,
                protectName = protectKey?.substringAfterLast('/'),
            ).toSet()
            var deleted = 0
            for (ref in items.filter { it.name in doomedNames }) {
                try {
                    ref.delete().await()
                    deleted++
                } catch (e: Exception) {
                    e.printStackTrace()   // one stubborn object must not abort the sweep
                }
            }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Which version filenames should be deleted. Pure, so the choice of what to destroy is
     * testable without touching the network.
     *
     * Keys are "<millis>_<nonce>.<ext>", so lexical order on the name is chronological and
     * the newest [keep] are simply the last [keep] entries. [protectName] is never returned.
     */
    fun versionsToPrune(names: List<String>, keep: Int, protectName: String?): List<String> {
        if (keep <= 0) return emptyList()
        return names.sorted()
            .dropLast(keep)
            .filter { it != protectName }
    }
}
