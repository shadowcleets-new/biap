package com.sc.biap.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.sc.biap.data.DefaultDataRepository
import java.io.File

/**
 * @file ImageUploadWorker.kt
 * @description Uploads a design photo in the background, then moves the pointer.
 *
 * Saving a design never blocks on the network. The photo is already written to local
 * storage and shown from there, so on a weak workshop signal the user sees their work
 * saved immediately; the upload catches up whenever a connection exists, surviving
 * app death and reboots because WorkManager persists the request.
 *
 * ORDERING IS THE SAFETY PROPERTY:
 *   1. upload the bytes to a brand-new key
 *   2. only then move the design's imageRef to it
 *   3. only then prune old versions
 * A failure at step 1 leaves the previous photo live and reachable - the design is
 * never left pointing at something that does not exist.
 */
class ImageUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ImageUploadWorker"
        const val KEY_DESIGN_ID = "designId"
        const val KEY_LOCAL_PATH = "localPath"
        const val KEY_STORAGE_KEY = "storageKey"

        /** Unique work name per design, so rapid re-edits replace rather than queue up. */
        fun workName(designId: String) = "ImageUpload_$designId"

        /** Give up after this many attempts so a permanently bad file cannot retry forever. */
        const val MAX_ATTEMPTS = 4
    }

    override suspend fun doWork(): Result {
        val designId = inputData.getString(KEY_DESIGN_ID) ?: return Result.failure()
        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val storageKey = inputData.getString(KEY_STORAGE_KEY) ?: return Result.failure()

        val file = File(localPath)
        if (!file.exists()) {
            // The source is gone; retrying cannot help.
            Log.w(TAG, "give up: local file missing for design=$designId path=$localPath")
            return Result.failure()
        }

        if (runAttemptCount >= MAX_ATTEMPTS) {
            // Terminal. Previously silent, which is how a photo could stay local forever with
            // nobody aware: the design synced fine, the picture simply never existed remotely.
            // DataRepository.backfillPendingImageUploads retries this on the next launch.
            Log.e(TAG, "give up after $runAttemptCount attempts: design=$designId key=$storageKey")
            return Result.failure()
        }

        // 1. bytes first, to a key nothing points at yet
        val uploaded = ImageStore.upload(file, storageKey)
        if (!uploaded) {
            Log.w(TAG, "upload failed (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS) key=$storageKey - " +
                "check Storage rules and that the App Check debug token is registered")
            return Result.retry()
        }

        // 2. the pointer moves only once the bytes are definitely there
        val uploadedBy = FirebaseAuth.getInstance().currentUser?.uid
        val repository = DefaultDataRepository.getInstance(applicationContext)
        val applied = repository.applyUploadedImage(designId, storageKey, uploadedBy)
        if (!applied) {
            // Bytes are safely stored; the design just has not caught up. Retry the move.
            Log.w(TAG, "uploaded but could not move the pointer for design=$designId; will retry")
            return Result.retry()
        }

        // 3. retention, best-effort - never fails the job
        ImageStore.pruneToLatest(designId, protectKey = storageKey)

        Log.i(TAG, "uploaded design=$designId key=$storageKey")
        return Result.success()
    }
}
