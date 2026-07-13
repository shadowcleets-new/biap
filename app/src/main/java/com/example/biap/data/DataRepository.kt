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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    private var currentDao: DesignDao? = null
    private var dbCollectionJob: Job? = null

    init {
        repositoryScope.launch {
            authManager.currentUser.collect { account ->
                dbLock.withLock {
                    // Close previous database connections and cancel current collections
                    dbCollectionJob?.cancelAndJoin()
                    currentDb?.close()
                    currentDb = null
                    currentDao = null
                    driveFileId = null

                    val suffix = getDatabaseSuffix(account?.id)
                    val db = openDatabaseForUser(suffix)
                    currentDb = db
                    val dao = db.designDao()
                    currentDao = dao

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
            // Wait up to 5 seconds for database initialization
            var count = 0
            while (currentDao == null && count < 50) {
                kotlinx.coroutines.delay(100)
                count++
            }
            val dao = currentDao ?: return@withContext ListenableWorker.Result.failure()

            // Option B Automated Cryptography Key Management
            var localKey = EncryptionUtils.getOrGenerateLocalSyncKey(context)
            val driveKeyFileId = driveSyncManager.searchFile(token, "sync_key.txt")
            if (driveKeyFileId != null) {
                val driveKey = driveSyncManager.downloadFile(token, driveKeyFileId)
                if (!driveKey.isNullOrBlank()) {
                    EncryptionUtils.saveLocalSyncKey(context, driveKey)
                    localKey = driveKey
                }
            } else {
                driveSyncManager.uploadFile(token, localKey, null, "sync_key.txt")
            }

            val fileId = driveSyncManager.searchFile(token, "bi_data.json.enc")
            driveFileId = fileId
            if (fileId != null) {
                // Download and decrypt remote JSON using localKey
                val encryptedContent = driveSyncManager.downloadFile(token, fileId)
                if (!encryptedContent.isNullOrBlank()) {
                    val decryptedJson = EncryptionUtils.decryptWithKey(encryptedContent, localKey)
                    val remoteData = json.decodeFromString<BusinessIntelligenceData>(decryptedJson)
                    
                    // 1. Merge deleted designs map using Last-Write-Wins (LWW)
                    val localData = _biData.value
                    val localDeleted = localData.deletedDesigns + localData.deletedDesignIds.associateWith { 0L }
                    val remoteDeleted = remoteData.deletedDesigns + remoteData.deletedDesignIds.associateWith { 0L }
                    
                    val mergedDeleted = (localDeleted.keys + remoteDeleted.keys)
                        .associateWith { id ->
                            maxOf(localDeleted[id] ?: 0L, remoteDeleted[id] ?: 0L)
                        }
                    
                    // Merge active designs using LWW based on updatedAt
                    val allDesigns = (localData.designs + remoteData.designs)
                        .groupBy { it.id }
                        .mapValues { (_, designsList) ->
                            designsList.maxByOrNull { it.updatedAt }!!
                        }
                    
                    // Filter out designs whose updatedAt is older or equal to their deletion timestamp
                    val mergedDesigns = allDesigns.values.filter { design ->
                        val deletedAt = mergedDeleted[design.id] ?: -1L
                        design.updatedAt > deletedAt
                    }
                    
                    // Prune tombstones for designs that are recreated/updated later
                    val finalDeleted = mergedDeleted.filter { (id, deletedAt) ->
                        val design = allDesigns[id]
                        design == null || deletedAt >= design.updatedAt
                    }
                    
                    // 2. Merge custom fields using LWW
                    val localDeletedFields = localData.deletedCustomFields
                    val remoteDeletedFields = remoteData.deletedCustomFields
                    val mergedDeletedFields = (localDeletedFields.keys + remoteDeletedFields.keys)
                        .associateWith { field ->
                            maxOf(localDeletedFields[field] ?: 0L, remoteDeletedFields[field] ?: 0L)
                        }

                    val localFields = localData.globalCustomFieldsMetadata.toMutableMap()
                    localData.globalCustomFields.forEach { field -> localFields.putIfAbsent(field, 0L) }
                    val remoteFields = remoteData.globalCustomFieldsMetadata.toMutableMap()
                    remoteData.globalCustomFields.forEach { field -> remoteFields.putIfAbsent(field, 0L) }

                    val allFields = (localFields.keys + remoteFields.keys)
                        .associateWith { field ->
                            maxOf(localFields[field] ?: 0L, remoteFields[field] ?: 0L)
                        }

                    val mergedFields = allFields.filter { (field, updatedAt) ->
                        val deletedAt = mergedDeletedFields[field] ?: -1L
                        updatedAt > deletedAt
                    }

                    val finalDeletedFields = mergedDeletedFields.filter { (field, deletedAt) ->
                        val updatedAt = allFields[field]
                        updatedAt == null || deletedAt >= updatedAt
                    }

                    val mergedData = BusinessIntelligenceData(
                        designs = mergedDesigns,
                        globalCustomFields = mergedFields.keys,
                        globalCustomFieldsMetadata = mergedFields,
                        deletedDesignIds = finalDeleted.keys,
                        deletedDesigns = finalDeleted,
                        deletedCustomFields = finalDeletedFields
                    )
                    
                    // Transactionally import merged data into Room
                    dao.importData(mergedData)
                    
                    // Upload merged back to drive using localKey
                    val reEncrypted = EncryptionUtils.encryptWithKey(json.encodeToString(mergedData), localKey)
                    driveSyncManager.uploadFile(token, reEncrypted, fileId, "bi_data.json.enc")
                    
                    triggerLocalBackup()
                    ListenableWorker.Result.success()
                } else {
                    ListenableWorker.Result.failure()
                }
            } else {
                // Upload local database to drive using localKey
                val localData = _biData.value
                val encrypted = EncryptionUtils.encryptWithKey(json.encodeToString(localData), localKey)
                val newFileId = driveSyncManager.uploadFile(token, encrypted, null, "bi_data.json.enc")
                driveFileId = newFileId
                triggerLocalBackup()
                ListenableWorker.Result.success()
            }
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
            val dao = currentDao ?: return@launch
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
            val dao = currentDao ?: return@launch
            dao.deleteDesignAndInventories(designId)
            dao.insertDeletedDesign(DeletedDesignEntity(designId, System.currentTimeMillis()))
            triggerLocalBackup()
        }
    }

    override fun addGlobalCustomField(field: String) {
        if (field.isBlank()) return
        val trimmed = field.trim()
        repositoryScope.launch {
            val dao = currentDao ?: return@launch
            dao.insertGlobalCustomFields(listOf(GlobalCustomFieldEntity(trimmed, System.currentTimeMillis())))
            dao.deleteDeletedCustomFieldTombstone(trimmed)
            triggerLocalBackup()
        }
    }

    override fun removeGlobalCustomField(field: String) {
        repositoryScope.launch {
            val dao = currentDao ?: return@launch
            dao.deleteGlobalCustomField(field)
            dao.insertDeletedCustomField(DeletedCustomFieldEntity(field, System.currentTimeMillis()))
            triggerLocalBackup()
        }
    }

    override suspend fun hasGuestData(): Boolean = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        try {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db")
                .fallbackToDestructiveMigration()
                .build()
            val paths = db.designDao().getAllImagePaths()
            db.close()
            paths.isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun mergeGuestData(): Boolean = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        dbLock.withLock {
            val activeDao = currentDao ?: return@withContext false
            try {
                val guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db")
                    .fallbackToDestructiveMigration()
                    .build()
                val guestDao = guestDb.designDao()

                val designs = guestDao.getAllDesignsFlow().first()
                val globals = guestDao.getAllGlobalCustomFieldsFlow().first()
                val deletedDesigns = guestDao.getAllDeletedDesignsFlow().first()
                val deletedCustomFields = guestDao.getAllDeletedCustomFieldsFlow().first()

                for (designWithInv in designs) {
                    val designEntity = designWithInv.design
                    val invEntities = designWithInv.inventories
                    val updatedDesign = designEntity.copy(updatedAt = System.currentTimeMillis())
                    activeDao.insertDesign(updatedDesign)
                    activeDao.insertInventories(invEntities.map { it.copy(id = 0) })
                }

                activeDao.insertGlobalCustomFields(globals.map { it.copy(updatedAt = System.currentTimeMillis()) })
                activeDao.insertDeletedDesigns(deletedDesigns.map { it.copy(deletedAt = System.currentTimeMillis()) })
                activeDao.insertDeletedCustomFields(deletedCustomFields.map { it.copy(deletedAt = System.currentTimeMillis()) })

                guestDao.deleteAllDesigns()
                guestDao.deleteAllInventories()
                guestDao.deleteAllGlobalCustomFields()
                guestDao.deleteAllDeletedDesigns()
                guestDao.deleteAllDeletedCustomFields()

                guestDb.close()
                triggerLocalBackup()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
