package com.example.biap.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.biap.data.DefaultDataRepository

class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = DefaultDataRepository.getInstance(applicationContext)
        val authManager = repository.authManager
        val account = authManager.currentUser.value ?: return Result.failure()
        val googleId = account.id ?: return Result.failure()
        
        val token = authManager.getAccessToken() ?: return Result.retry()

        return repository.performSyncInternal(token, googleId)
    }
}
