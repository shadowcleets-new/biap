package com.sc.biap.util

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sc.biap.data.room.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OrphanImageCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val dbFolder = context.getDatabasePath("bi_database.db").parentFile ?: return@withContext Result.failure()
            
            // 1. Find all user database files
            val dbFiles = dbFolder.listFiles { file ->
                file.name.startsWith("bi_database") && file.name.endsWith(".db")
            } ?: emptyArray()

            val activeImagePaths = mutableSetOf<String>()

            // 2. Open each database and extract referenced imagePath values
            for (dbFile in dbFiles) {
                try {
                    val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name)
                        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .fallbackToDestructiveMigration()
                        .build()
                    val paths = db.designDao().getAllImagePaths()
                    for (path in paths) {
                        activeImagePaths.add(path)
                        val file = File(path)
                        activeImagePaths.add(file.name)
                    }
                    db.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Scan local filesDir for design photographs matching "img_*.jpg"
            val filesDir = context.filesDir
            val images = filesDir.listFiles { file ->
                file.name.startsWith("img_") && file.name.endsWith(".jpg")
            } ?: emptyArray()

            // 4. Delete files that are not referenced in any database
            var deletedCount = 0
            for (imageFile in images) {
                val relativeName = imageFile.name
                val absolutePath = imageFile.absolutePath
                if (relativeName !in activeImagePaths && absolutePath !in activeImagePaths) {
                    if (imageFile.delete()) {
                        deletedCount++
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
