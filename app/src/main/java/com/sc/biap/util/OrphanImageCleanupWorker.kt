package com.sc.biap.util

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sc.biap.data.room.AppDatabase
import com.sc.biap.security.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
                    // A background cleanup worker must never be able to destroy data.
                    // With fallbackToDestructiveMigration this loop could silently wipe a
                    // signed-out user's entire catalogue while merely looking for orphaned
                    // files. Without it, a mismatched database throws, is skipped by the
                    // catch below, and stays intact.
                    val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name)
                        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                        // Same passphrase as the app: these databases are encrypted now, and
                        // without this the worker simply cannot open them.
                        .openHelperFactory(SupportOpenHelperFactory(DatabaseKey.getOrCreate(context)))
                        .build()
                    // Cover photos, extra photos, JobWork fault/sample photos, and local
                    // profile avatars. Missing any of those would sweep live images off disk.
                    val dao = db.designDao()
                    val converters = com.sc.biap.data.room.Converters()
                    val paths = dao.getAllImagePaths() +
                        dao.getAllExtraImagesRaw()
                            .flatMap { converters.toDesignPhotos(it) }
                            .mapNotNull { it.localPath } +
                        dao.getAllJobWorkImagePaths() +
                        listOfNotNull(
                            dao.getProfileOnce()?.photoRef?.takeIf { !it.startsWith("http") }
                        )
                    activeImagePaths.addAll(collectActiveImageRefs(paths))
                    db.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Scan local filesDir for orphaned "img_*.jpg" files and delete them
            var deletedCount = 0
            for (imageFile in findOrphanedImageFiles(context.filesDir, activeImagePaths)) {
                if (imageFile.delete()) {
                    deletedCount++
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}

internal fun collectActiveImageRefs(paths: Iterable<String>): Set<String> {
    val active = mutableSetOf<String>()
    for (path in paths) {
        if (path.isBlank()) continue
        active.add(path)
        active.add(File(path).name)
    }
    return active
}

internal fun findOrphanedImageFiles(filesDir: File, activeImagePaths: Set<String>): List<File> {
    val images = filesDir.listFiles { file ->
        file.name.startsWith("img_") && file.name.endsWith(".jpg")
    } ?: return emptyList()
    return images.filter { imageFile ->
        imageFile.name !in activeImagePaths && imageFile.absolutePath !in activeImagePaths
    }
}
