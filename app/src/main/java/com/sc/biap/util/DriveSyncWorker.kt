package com.sc.biap.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sc.biap.data.DefaultDataRepository

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = DefaultDataRepository.getInstance(applicationContext)
        val authManager = repository.authManager
        val account = authManager.currentUser.value ?: return Result.failure()
        val googleId = account.id ?: return Result.failure()

        val token = authManager.getAccessToken()
        if (token == null) {
            // Interactive re-approval pending, or repeated token failures: stop retrying
            // so the UI shows an actionable error instead of an invisible loop.
            return if (authManager.pendingAuthIntent.value != null || runAttemptCount >= 4) {
                Result.failure()
            } else {
                Result.retry()
            }
        }

        val result = repository.performSyncInternal(token, googleId)
        // Hard retry cap (~10s + 20s + 40s + 80s of backoff already spent).
        // Type check, not equals: WorkManager Result instances are compared by class.
        return if (result is Result.Retry && runAttemptCount >= 4) Result.failure() else result
    }
}
