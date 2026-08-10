package com.sc.biap.util

import android.net.Uri
import android.util.Log
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

    private const val TAG = "ImageStore"
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
     * Resolved download URLs, kept for the life of the process.
     *
     * Keys are write-once, so the bytes behind one can never change and a URL can never go
     * stale - which makes this safe to cache indefinitely. Without it, every scroll past a
     * design costs a fresh getDownloadUrl() round trip; a catalogue browsed repeatedly on a
     * workshop connection spends most of its time re-asking for URLs it already had.
     *
     * Bounded so a large catalogue cannot grow it without limit. Failures are never cached -
     * a refusal is usually transient (no signal, an expired token) and caching it would turn
     * a momentary problem into a permanent blank.
     */
    private const val URL_CACHE_LIMIT = 256
    private val urlCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > URL_CACHE_LIMIT
    }

    /**
     * Resolve a storage key to a URL for loading, or null if it cannot be reached.
     *
     * A null here is what the UI turns into "photo unavailable", so the reason is logged
     * rather than swallowed: a rejected App Check token and a dead network produce the same
     * blank square on screen but need completely different fixes.
     */
    suspend fun resolveUrl(key: String): String? = withContext(Dispatchers.IO) {
        synchronized(urlCache) { urlCache[key] }?.let { return@withContext it }

        return@withContext try {
            val url = refFor(key).downloadUrl.await().toString()
            synchronized(urlCache) { urlCache[key] = url }
            url
        } catch (e: Exception) {
            Log.w(
                TAG,
                "could not resolve $key - the design points at a photo this device cannot " +
                    "fetch. Check sign-in, connectivity, Storage read rules, and that the " +
                    "App Check token is registered. Cause: ${e.message}"
            )
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
