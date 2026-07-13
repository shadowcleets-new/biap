package com.example.biap.data

import android.content.Context
import androidx.room.Room
import com.example.biap.data.room.*
import com.example.biap.util.AuthManager
import com.example.biap.util.EncryptionUtils
import com.example.biap.util.GoogleDriveSyncManager
import com.example.biap.util.DriveSyncWorker
import com.example.biap.util.OrphanImageCleanupWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

sealed interface SyncStatus {
    object Idle : SyncStatus
    object Syncing : SyncStatus
    data class Success(val message: String) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

interface DataRepository {
    val biData: StateFlow<BusinessIntelligenceData>
    fun addOrUpdateDesign(design: Design)
    fun deleteDesign(designId: String)
    fun addGlobalCustomField(field: String)
    fun removeGlobalCustomField(field: String)

    // Google Auth & Drive Sync
    val authManager: AuthManager
    val isSyncing: StateFlow<Boolean>
    val syncStatus: StateFlow<SyncStatus>
    fun syncWithGoogleDrive()
    fun signOut(onComplete: () -> Unit)
    fun resetSyncStatus()

    // Guest merge operations
    suspend fun hasGuestData(): Boolean
    suspend fun mergeGuestData(): Boolean
    suspend fun clearGuestData(): Boolean
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    companion object {
        @Volatile
        private var INSTANCE: DefaultDataRepository? = null

        fun getInstance(context: Context): DefaultDataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DefaultDataRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dbLock = Mutex()
    private val backupLock = Mutex()

    private val _biData = MutableStateFlow(BusinessIntelligenceData())
    override val biData: StateFlow<BusinessIntelligenceData> = _biData.asStateFlow()

    override val authManager = AuthManager(context)
    private val driveSyncManager = GoogleDriveSyncManager()
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    private var driveFileId: String? = null

    private var currentDb: AppDatabase? = null
    private val currentDaoFlow = MutableStateFlow<DesignDao?>(null)
    private var dbCollectionJob: Job? = null

    init {
        repositoryScope.launch {
            authManager.currentUser.collect { account ->
                dbLock.withLock {
                    // Close previous database connections and cancel current collections
                    dbCollectionJob?.cancelAndJoin()
                    currentDb?.close()
                    currentDb = null
                    currentDaoFlow.value = null
                    driveFileId = null

                    val suffix = getDatabaseSuffix(account?.id)
                    val db = openDatabaseForUser(suffix)
                    currentDb = db
                    val dao = db.designDao()
                    currentDaoFlow.value = dao

                    // Populate defaults if globals table is empty (one-off check on opening database)
                    val defaultGlobals = listOf(
                        "Top - Fabric", "Top - Work", "Top - Color",
                        "Bottom - Fabric", "Bottom - Work", "Bottom - Color",
                        "Dupatta - Fabric", "Dupatta - Work", "Dupatta - Color"
                    )
                    
                    repositoryScope.launch {
                        try {
                            val currentFields = dao.getAllGlobalCustomFields()
                            if (currentFields.isEmpty()) {
                                dao.insertGlobalCustomFields(defaultGlobals.map { GlobalCustomFieldEntity(it) })
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Start collecting from Room Flow and updating UI state Flow
                    dbCollectionJob = repositoryScope.launch {
                        combine(
                            dao.getAllDesignsFlow(),
                            dao.getAllGlobalCustomFieldsFlow(),
                            dao.getAllDeletedDesignsFlow(),
                            dao.getAllDeletedCustomFieldsFlow()
                        ) { designsList, globalsList, deletedList, deletedFieldsList ->
                            BusinessIntelligenceData(
                                designs = designsList.map { it.toDomain() },
                                globalCustomFields = globalsList.map { it.field }.toSet(),
                                globalCustomFieldsMetadata = globalsList.associate { it.field to it.updatedAt },
                                deletedDesignIds = deletedList.map { it.designId }.toSet(),
                                deletedDesigns = deletedList.associate { it.designId to it.deletedAt },
                                deletedCustomFields = deletedFieldsList.associate { it.field to it.deletedAt }
                            )
                        }.collect { merged ->
                            _biData.value = merged
                        }
                    }
                }
            }
        }
        
        // Schedule periodic unreferenced design images cleanup task (runs every 24 hours)
        val cleanupConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val cleanupRequest = PeriodicWorkRequestBuilder<OrphanImageCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(cleanupConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "OrphanImageCleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    private fun getDatabaseSuffix(googleId: String?): String {
        return if (!googleId.isNullOrBlank()) {
            val bytes = googleId.toByteArray()
            val hash = java.security.MessageDigest.getInstance("MD5").digest(bytes)
            val hex = hash.joinToString("") { "%02x".format(it) }
            "_$hex"
        } else {
            ""
        }
    }

    private fun openDatabaseForUser(suffix: String): AppDatabase {
        val dbName = "bi_database${suffix}.db"
        val dbFile = context.getDatabasePath(dbName)
        val backupDbFile = File(dbFile.parentFile, "$dbName.bak")
        val walFile = File(dbFile.parentFile, "$dbName-wal")
        val shmFile = File(dbFile.parentFile, "$dbName-shm")
        
        fun deleteActiveDbFiles() {
            dbFile.delete()
            walFile.delete()
            shmFile.delete()
        }
        
        fun buildAndVerify(): AppDatabase {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .fallbackToDestructiveMigration()
                .build()
            // Force eager connection to check integrity immediately at start-up
            db.openHelper.writableDatabase.query("SELECT 1").close()
            return db
        }

        return try {
            buildAndVerify()
        } catch (e: Exception) {
            e.printStackTrace()
            // Proactively prevent data loss: backup corrupted database before recreating
            try {
                if (dbFile.exists()) {
                    val corruptFile = File(dbFile.parentFile, "$dbName.corrupt_${System.currentTimeMillis()}")
                    dbFile.copyTo(corruptFile, overwrite = true)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            
            // Re-attempt opening from .bak backup file
            if (backupDbFile.exists()) {
                try {
                    // Must purge auxiliary write-ahead logging files before copy
                    deleteActiveDbFiles()
                    backupDbFile.copyTo(dbFile, overwrite = true)
                    buildAndVerify()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    deleteActiveDbFiles()
                    buildAndVerify()
                }
            } else {
                deleteActiveDbFiles()
                buildAndVerify()
            }
        }
    }

    /**
     * Waits briefly for the active account's DAO (it is null for a moment during
     * account switches). BUG-02 fix: writes no longer silently vanish in that window.
     */
    private suspend fun awaitDao(timeoutMs: Long = 5_000): DesignDao? =
        withTimeoutOrNull(timeoutMs) { currentDaoFlow.filterNotNull().first() }

    /**
     * BUG-01 fix: sync snapshots are read from the database, never from the UI StateFlow
     * (which can be empty right after an account switch and would nuke the merge).
     */
    private suspend fun snapshotFromDao(dao: DesignDao): BusinessIntelligenceData {
        val designs = dao.getAllDesignsOnce()
        val globals = dao.getAllGlobalCustomFields()
        val deletedDesigns = dao.getAllDeletedDesignsOnce()
        val deletedFields = dao.getAllDeletedCustomFieldsOnce()
        return BusinessIntelligenceData(
            designs = designs.map { it.toDomain() },
            globalCustomFields = globals.map { it.field }.toSet(),
            globalCustomFieldsMetadata = globals.associate { it.field to it.updatedAt },
            deletedDesignIds = deletedDesigns.map { it.designId }.toSet(),
            deletedDesigns = deletedDesigns.associate { it.designId to it.deletedAt },
            deletedCustomFields = deletedFields.associate { it.field to it.deletedAt }
        )
    }

    private fun triggerLocalBackup() {
        val account = authManager.currentUser.value
        val suffix = getDatabaseSuffix(account?.id)
        repositoryScope.launch(Dispatchers.IO) {
            backupLock.withLock {
                val dbFile = context.getDatabasePath("bi_database${suffix}.db")
                val backupDbFile = File(dbFile.parentFile, "${dbFile.name}.bak")
                if (dbFile.exists()) {
                    try {
                        // Trigger checkpoint to ensure SQLite WAL logs are merged to main file
                        currentDb?.openHelper?.writableDatabase?.query("PRAGMA wal_checkpoint(FULL)")?.close()
                        dbFile.copyTo(backupDbFile, overwrite = true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun resetSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }

    override fun syncWithGoogleDrive() {
        val account = authManager.currentUser.value
        if (account == null) {
            _syncStatus.value = SyncStatus.Error("No Google account signed in.")
            return
        }
        val googleId = account.id
        if (googleId == null) {
            _syncStatus.value = SyncStatus.Error("Google Account ID is missing.")
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(
            "GoogleDriveSync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        repositoryScope.launch {
            wm.getWorkInfoByIdFlow(syncRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        androidx.work.WorkInfo.State.ENQUEUED -> {
                            _isSyncing.value = false
                            _syncStatus.value = SyncStatus.Idle
                        }
                        androidx.work.WorkInfo.State.RUNNING -> {
                            _isSyncing.value = true
                            _syncStatus.value = SyncStatus.Syncing
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            _isSyncing.value = false
                            _syncStatus.value = SyncStatus.Success("Sync completed successfully.")
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            _isSyncing.value = false
                            _syncStatus.value = SyncStatus.Error("Sync failed in background execution.")
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            _isSyncing.value = false
                            _syncStatus.value = SyncStatus.Idle
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    suspend fun performSyncInternal(token: String, googleId: String): ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            val dao = awaitDao(timeoutMs = 10_000) ?: return@withContext ListenableWorker.Result.failure()

            // Guard: the open database must belong to the account this sync was requested for.
            if (authManager.currentUser.value?.id != googleId) {
                return@withContext ListenableWorker.Result.failure()
            }

            // --- Key management: validated and race-safe (BUG-05) ---
            val localKey = EncryptionUtils.getOrGenerateLocalSyncKey(context)
            var keyFileId = driveSyncManager.searchFile(token, "sync_key.txt")
            if (keyFileId == null) {
                driveSyncManager.uploadFile(token, localKey, null, "sync_key.txt")
                // Re-read so two devices racing here converge on the same files[0] winner.
                keyFileId = driveSyncManager.searchFile(token, "sync_key.txt")
            }
            var candidateKey = localKey
            if (keyFileId != null) {
                val remoteKey = driveSyncManager.downloadFile(token, keyFileId)?.trim()
                if (!remoteKey.isNullOrBlank() && EncryptionUtils.isValidKey(remoteKey)) {
                    candidateKey = remoteKey
                }
            }

            val fileId = driveSyncManager.searchFile(token, "bi_data.json.enc")
            driveFileId = fileId
            val localData = snapshotFromDao(dao)

            if (fileId == null) {
                // First upload for this Drive.
                val encrypted = EncryptionUtils.encryptWithKey(json.encodeToString(localData), candidateKey)
                driveFileId = driveSyncManager.uploadFile(token, encrypted, null, "bi_data.json.enc")
                EncryptionUtils.saveLocalSyncKey(context, candidateKey)
                triggerLocalBackup()
                return@withContext ListenableWorker.Result.success()
            }

            val encryptedContent = driveSyncManager.downloadFile(token, fileId)
            if (encryptedContent.isNullOrBlank()) {
                // Self-heal a zero-byte artifact left by an interrupted upload (BUG-06).
                val encrypted = EncryptionUtils.encryptWithKey(json.encodeToString(localData), candidateKey)
                driveSyncManager.uploadFile(token, encrypted, fileId, "bi_data.json.enc")
                EncryptionUtils.saveLocalSyncKey(context, candidateKey)
                triggerLocalBackup()
                return@withContext ListenableWorker.Result.success()
            }

            // Decrypt: candidate (remote) key first, then the local key (BUG-05).
            var usedKey = candidateKey
            var decryptedJson = try {
                EncryptionUtils.decryptWithKey(encryptedContent, candidateKey)
            } catch (e: Exception) {
                null
            }
            if (decryptedJson == null && candidateKey != localKey) {
                decryptedJson = try {
                    EncryptionUtils.decryptWithKey(encryptedContent, localKey)
                } catch (e: Exception) {
                    null
                }
                if (decryptedJson != null) {
                    usedKey = localKey
                    if (keyFileId != null) {
                        // The escrowed key is wrong for the real payload; repair it.
                        driveSyncManager.uploadFile(token, localKey, keyFileId, "sync_key.txt")
                    }
                }
            }

            val remoteData = if (decryptedJson != null) LegacyBackupDecoder.decode(decryptedJson) else null

            if (remoteData == null) {
                // Unreadable payload (legacy key scheme or corruption): quarantine it,
                // then start fresh from the local snapshot (BUG-03 terminal case).
                driveSyncManager.renameFile(token, fileId, "bi_data.json.enc.unreadable_${System.currentTimeMillis()}")
                val encrypted = EncryptionUtils.encryptWithKey(json.encodeToString(localData), usedKey)
                driveFileId = driveSyncManager.uploadFile(token, encrypted, null, "bi_data.json.enc")
                EncryptionUtils.saveLocalSyncKey(context, usedKey)
                triggerLocalBackup()
                return@withContext ListenableWorker.Result.success()
            }

            val mergedData = SyncMerger.merge(localData, remoteData)
            dao.importData(mergedData)
            val reEncrypted = EncryptionUtils.encryptWithKey(json.encodeToString(mergedData), usedKey)
            driveSyncManager.uploadFile(token, reEncrypted, fileId, "bi_data.json.enc")
            EncryptionUtils.saveLocalSyncKey(context, usedKey)
            triggerLocalBackup()
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            ListenableWorker.Result.retry()
        }
    }

    override fun signOut(onComplete: () -> Unit) {
        authManager.signOut {
            repositoryScope.launch {
                dbLock.withLock {
                    _syncStatus.value = SyncStatus.Idle
                }
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    override fun addOrUpdateDesign(design: Design) {
        repositoryScope.launch {
            val dao = awaitDao() ?: return@launch
            val designWithTime = design.copy(updatedAt = System.currentTimeMillis())
            dao.insertDesignWithInventories(
                designWithTime.toEntity(),
                designWithTime.sizeInventories.map { it.toEntity(design.id) }
            )
            dao.deleteDeletedDesignTombstone(design.id)
            triggerLocalBackup()
        }
    }

    override fun deleteDesign(designId: String) {
        repositoryScope.launch {
            val dao = awaitDao() ?: return@launch
            dao.deleteDesignAndInventories(designId)
            dao.insertDeletedDesign(DeletedDesignEntity(designId, System.currentTimeMillis()))
            triggerLocalBackup()
        }
    }

    override fun addGlobalCustomField(field: String) {
        if (field.isBlank()) return
        val trimmed = field.trim()
        repositoryScope.launch {
            val dao = awaitDao() ?: return@launch
            dao.insertGlobalCustomFields(listOf(GlobalCustomFieldEntity(trimmed, System.currentTimeMillis())))
            dao.deleteDeletedCustomFieldTombstone(trimmed)
            triggerLocalBackup()
        }
    }

    override fun removeGlobalCustomField(field: String) {
        repositoryScope.launch {
            val dao = awaitDao() ?: return@launch
            dao.deleteGlobalCustomField(field)
            dao.insertDeletedCustomField(DeletedCustomFieldEntity(field, System.currentTimeMillis()))
            triggerLocalBackup()
        }
    }

    override suspend fun hasGuestData(): Boolean = withContext(Dispatchers.IO) {
        // Only meaningful while signed in: in guest mode "bi_database.db" IS the active DB.
        if (authManager.currentUser.value == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        var guestDb: AppDatabase? = null
        try {
            guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db").build()
            guestDb.designDao().getAllDesignsOnce().isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            guestDb?.close()
        }
    }

    override suspend fun mergeGuestData(): Boolean = withContext(Dispatchers.IO) {
        if (authManager.currentUser.value == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        dbLock.withLock {
            val activeDao = awaitDao() ?: return@withContext false
            var guestDb: AppDatabase? = null
            try {
                guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db").build()
                val guestDao = guestDb.designDao()

                val designs = guestDao.getAllDesignsOnce()
                val globals = guestDao.getAllGlobalCustomFields()
                val deletedDesigns = guestDao.getAllDeletedDesignsOnce()
                val deletedCustomFields = guestDao.getAllDeletedCustomFieldsOnce()

                for (designWithInv in designs) {
                    val updatedDesign = designWithInv.design.copy(updatedAt = System.currentTimeMillis())
                    activeDao.insertDesign(updatedDesign)
                    activeDao.insertInventories(designWithInv.inventories.map { it.copy(id = 0) })
                }
                activeDao.insertGlobalCustomFields(globals.map { it.copy(updatedAt = System.currentTimeMillis()) })
                // Preserve ORIGINAL tombstone timestamps (BUG-13): restamping to "now" would
                // out-vote legitimately newer edits from other devices in the next LWW merge.
                activeDao.insertDeletedDesigns(deletedDesigns)
                activeDao.insertDeletedCustomFields(deletedCustomFields)

                guestDao.deleteAllDesigns()
                guestDao.deleteAllInventories()
                guestDao.deleteAllGlobalCustomFields()
                guestDao.deleteAllDeletedDesigns()
                guestDao.deleteAllDeletedCustomFields()
                triggerLocalBackup()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                guestDb?.close()
            }
        }
    }

    override suspend fun clearGuestData(): Boolean = withContext(Dispatchers.IO) {
        // Only valid while signed in: in guest mode "bi_database.db" IS the active database.
        if (authManager.currentUser.value == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext true
        var guestDb: AppDatabase? = null
        try {
            guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db").build()
            val dao = guestDb.designDao()
            dao.deleteAllDesigns()
            dao.deleteAllInventories()
            dao.deleteAllGlobalCustomFields()
            dao.deleteAllDeletedDesigns()
            dao.deleteAllDeletedCustomFields()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            guestDb?.close()
        }
    }
}
