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
        const val KEY_JOB_ID = "jobId"
        const val KEY_TARGET_TYPE = "targetType"
        const val KEY_LOCAL_PATH = "localPath"
        const val KEY_STORAGE_KEY = "storageKey"

        const val TARGET_DESIGN = "design"
        const val TARGET_JOB_PHOTO = "job_photo"
        const val TARGET_JOB_VOICE = "job_voice"

        /**
         * Unique work name per PHOTOGRAPH, so rapid re-edits of one replace rather than
         * queue up - while five photos on one design still all get uploaded.
         *
         * This was keyed by design alone. That was right when a design had one photo and
         * actively wrong once it could have five: enqueuing them together, each would have
         * evicted the last and exactly one would have reached Drive.
         */
        fun workName(designId: String, localPath: String) =
            "ImageUpload_${designId}_${localPath.hashCode()}"

        fun jobWorkName(jobId: String, targetType: String, localPath: String) =
            "ImageUpload_${targetType}_${jobId}_${localPath.hashCode()}"

        /** Give up after this many attempts so a permanently bad file cannot retry forever. */
        const val MAX_ATTEMPTS = 4
    }

    override suspend fun doWork(): Result {
        val targetType = inputData.getString(KEY_TARGET_TYPE) ?: TARGET_DESIGN
        val targetId = when (targetType) {
            TARGET_DESIGN -> inputData.getString(KEY_DESIGN_ID)
            TARGET_JOB_PHOTO, TARGET_JOB_VOICE ->
                inputData.getString(KEY_JOB_ID) ?: inputData.getString(KEY_DESIGN_ID)
            else -> return Result.failure()
        } ?: return Result.failure()

        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val storageKey = inputData.getString(KEY_STORAGE_KEY) ?: return Result.failure()

        val file = when (targetType) {
            TARGET_JOB_VOICE -> VoiceNotes.resolve(applicationContext, localPath) ?: File(localPath)
            else -> FileUtils.resolveImageFile(applicationContext, localPath)
        }
        if (file == null || !file.exists()) {
            // The source is gone; retrying cannot help.
            Log.w(TAG, "give up: local file missing for target=$targetType id=$targetId path=$localPath")
            return Result.failure()
        }

        if (runAttemptCount >= MAX_ATTEMPTS) {
            // Terminal. Previously silent, which is how a photo could stay local forever with
            // nobody aware: the design synced fine, the picture simply never existed remotely.
            // DataRepository.backfillPendingImageUploads retries this on the next launch.
            Log.e(TAG, "give up after $runAttemptCount attempts: target=$targetType id=$targetId key=$storageKey")
            return Result.failure()
        }

        // 1. bytes first, to a key nothing points at yet
        val uploaded = DriveImageStore.upload(applicationContext, file, storageKey)
        if (!uploaded) {
            Log.w(TAG, "upload failed (attempt ${runAttemptCount + 1}/$MAX_ATTEMPTS) key=$storageKey - " +
                "check that the Google account is connected and has Drive access")
            return Result.retry()
        }

        // 2. the pointer moves only once the bytes are definitely there
        val repository = DefaultDataRepository.getInstance(applicationContext)
        when (targetType) {
            TARGET_DESIGN -> {
                val uploadedBy = FirebaseAuth.getInstance().currentUser?.uid
                val applied = repository.applyUploadedImage(targetId, localPath, storageKey, uploadedBy)
                if (!applied) {
                    // Bytes are safely stored; the design just has not caught up. Retry the move.
                    Log.w(TAG, "uploaded but could not move the pointer for design=$targetId; will retry")
                    return Result.retry()
                }

                // 3. retention, best-effort - never fails the job.
                //
                // Protects EVERY key the design points at, read back after the pointer moved. A
                // design may hold five photographs whose versions all share one prefix, so a sweep
                // told to spare only the key just uploaded would delete the other four. An empty set
                // means the read failed, and pruning on that would be deleting photos on the strength
                // of a lookup that did not work - so the sweep is skipped entirely.
                val live = repository.referencedImageKeys(targetId)
                if (live.isEmpty()) {
                    Log.w(TAG, "skipping retention for design=$targetId: could not read its live keys")
                } else {
                    DriveImageStore.pruneToLatest(applicationContext, targetId, protectKeys = live)
                }
            }
            TARGET_JOB_PHOTO -> {
                val applied = repository.applyUploadedJobMedia(targetId, isVoice = false, key = storageKey)
                if (!applied) {
                    Log.w(TAG, "uploaded but could not move photo pointer for job=$targetId; will retry")
                    return Result.retry()
                }
            }
            TARGET_JOB_VOICE -> {
                val applied = repository.applyUploadedJobMedia(targetId, isVoice = true, key = storageKey)
                if (!applied) {
                    Log.w(TAG, "uploaded but could not move voice pointer for job=$targetId; will retry")
                    return Result.retry()
                }
            }
        }

        Log.i(TAG, "uploaded target=$targetType id=$targetId key=$storageKey")
        return Result.success()
    }
}
