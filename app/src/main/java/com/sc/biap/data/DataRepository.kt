package com.sc.biap.data

import android.content.Context
import androidx.room.Room
import com.sc.biap.data.room.*
import com.sc.biap.util.AuthManager
import com.sc.biap.util.EncryptionUtils
import com.sc.biap.util.GoogleDriveSyncManager
import com.sc.biap.util.DriveSyncWorker
import com.sc.biap.util.FileUtils
import com.sc.biap.util.DriveImageStore
import com.sc.biap.util.ImageUploadWorker
import com.sc.biap.util.OrphanImageCleanupWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
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
import com.google.firebase.auth.FirebaseAuth
import com.sc.biap.security.DatabaseKey
import com.sc.biap.security.LegacyDatabaseMigrator
import com.sc.biap.security.PassphraseStore
import com.sc.biap.security.SyncPassphrase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.sc.biap.data.room.toDomain
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
    /**
     * Sync stopped because the passphrase that unlocks the sync key is missing or wrong.
     * [isNewPassphrase] is true when this Drive has no key yet, so the UI asks the owner to
     * choose one rather than to recall one they never set.
     */
    data class PassphraseRequired(val isNewPassphrase: Boolean) : SyncStatus
}

interface DataRepository {
    /** Queue a design photo for background upload. Returns the storage key it will land on. */
    fun enqueueImageUpload(designId: String, localPath: String): String
    /**
     * Move ONE photograph's pointer after its bytes are confirmed uploaded.
     *
     * [localPath] identifies which of the design's photographs; the key alone cannot, since
     * a design may hold several and the cover is stored apart from the rest.
     */
    suspend fun applyUploadedImage(
        designId: String,
        localPath: String,
        key: String,
        uploadedBy: String?,
    ): Boolean

    /** Every storage key this design points at, so retention can spare all of them. */
    suspend fun referencedImageKeys(designId: String): Set<String>
    val biData: StateFlow<BusinessIntelligenceData>
    fun addOrUpdateDesign(design: Design)

    /** Hide a design from the working catalogue, or bring it back. */
    fun setDesignArchived(designId: String, archived: Boolean)

    /** Destroy a design permanently. Offered only for designs already archived. */
    fun deleteDesign(designId: String)

    /**
     * Record pieces going out, and take them out of stock in the same transaction.
     *
     * Returns null on success, or a reason to show. Suspends rather than firing and
     * forgetting because the caller has to know whether it worked: the stock may have moved
     * under it since the screen was drawn, and the answer decides whether the screen closes.
     */
    suspend fun placeOrder(lines: List<OrderLine>): String?

    /**
     * Change what is on an existing order, moving only the difference in and out of stock.
     *
     * Returns null on success, or a reason to show. Lines already on the order keep the price
     * they went out at; only a size that was never on it takes today's.
     */
    suspend fun editOrder(orderId: String, lines: List<OrderLine>): String?

    /** One order by id, for the edit screen to fill itself in from. */
    suspend fun orderById(orderId: String): Order?

    /**
     * Cancel an order, returning its pieces to stock. Returns null on success, else a reason.
     *
     * Suspends and reports rather than firing and forgetting, because the edit screen closes
     * itself on the strength of the answer - popping back on a cancellation that never
     * happened would leave the order sitting there having apparently been removed.
     */
    suspend fun cancelOrder(orderId: String): String?

    // --- Batches ---

    /** The number to put in the field when adding a batch. The owner may type over it. */
    suspend fun suggestBatchNumber(designId: String, size: String): Int

    /** Adds a batch and puts its pieces into stock. Null on success, a message on failure. */
    suspend fun addBatch(
        designId: String,
        size: String,
        number: Int,
        quantity: Int,
        price: Long,
        note: String = "",
    ): String?

    /** Empties one size: stock to 0, every batch remainder to 0, everything else kept. */
    suspend fun resetSizeStock(designId: String, size: String): String?

    /** The same for every size of a design. */
    suspend fun resetDesignStock(designId: String): String?

    // --- Business Hub ---

    suspend fun addKarigar(name: String): String?
    suspend fun renameKarigar(id: String, name: String): String?
    suspend fun deleteKarigar(id: String)

    /** Sets the whole activity list for a karigar - what a drop onto an activity writes. */
    suspend fun setKarigarActivities(id: String, activities: List<String>)
    /**
     * Fold one karigar into another, everywhere: the same person under two spellings.
     * Null on success, a message on failure.
     */
    suspend fun mergeKarigar(fromId: String, intoId: String): String?

    /** Hand finished pieces to a karigar for further work. Needs them to be in stock. */
    suspend fun sendOutForJobWork(
        designId: String, size: String, karigar: String, activity: String,
        quantity: Int, note: String,
    ): String?

    /**
     * Commission pieces that do not exist yet.
     *
     * Takes nothing off the shelf, so it works on a size that is empty - which is the size
     * most likely to need making.
     */
    suspend fun startProduction(
        designId: String, karigar: String, activity: String,
        quantities: Map<String, Int>, batchNumber: Int, note: String,
    ): String?

    /** Ask a karigar for one sample of a design that does not exist yet. */
    suspend fun startSample(
        designName: String, karigar: String, activity: String, note: String,
        photoPath: String?, voicePath: String?,
    ): String?

    /** Send a few defective pieces back to be put right. Takes them off the shelf. */
    suspend fun startAlteration(
        designId: String, size: String, karigar: String, quantity: Int, note: String,
        photoPath: String?, voicePath: String?,
    ): String?

    /** Record pieces coming back from a karigar. */
    suspend fun receiveJobWork(jobId: String, quantity: Int): String?

    /** Finish a job, whatever is or is not back. */
    suspend fun closeJobWork(jobId: String): String?

    /** Judge a sample. Approving it creates the design in the catalogue. */
    suspend fun decideSample(jobId: String, approved: Boolean): String?

    suspend fun addActivity(name: String): String?
    suspend fun deleteActivity(name: String)
    fun addGlobalCustomField(field: String)
    fun removeGlobalCustomField(field: String)

    // Google Auth & Drive Sync
    val authManager: AuthManager
    val isSyncing: StateFlow<Boolean>

    /** Epoch millis of the last completed sync, or 0 if none since launch. */
    val lastSyncAt: StateFlow<Long>
    val syncStatus: StateFlow<SyncStatus>
    fun syncWithGoogleDrive()
    /** Store the passphrase that wraps the sync key, then retry the sync. */
    fun setSyncPassphrase(passphrase: CharArray)
    fun hasSyncPassphrase(): Boolean

    /**
     * Re-wrap the SAME sync key under a new passphrase. The backup stays readable.
     *
     * Returns null on success, or a reason to show.
     */
    suspend fun changeSyncPassphrase(current: CharArray, new: CharArray): String?

    /**
     * Abandon the unreadable backup and publish a fresh one from this device.
     *
     * For a passphrase nobody can supply any more. Both Drive files are OVERWRITTEN in
     * place, so a Drive folder never accumulates orphaned backups however many times this
     * is used.
     */
    suspend fun resetSyncPassphrase(new: CharArray): String?

    /** Designs held locally. Reset refuses when this is zero - see [resetSyncPassphrase]. */
    fun localDesignCount(): Int
    fun signOut(onComplete: () -> Unit)
    fun resetSyncStatus()

    // Guest merge operations
    /** The signed-in person's own details, kept with the catalogue and synced with it. */
    val profile: StateFlow<UserProfile>
    suspend fun saveProfile(profile: UserProfile)

    suspend fun hasGuestData(): Boolean
    suspend fun mergeGuestData(): Boolean
    suspend fun clearGuestData(): Boolean
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    // Drive filenames for the sync key. The legacy one held the key in plain text and is
    // migrated away from on the first sync that finds it.
    private val KEY_FILE_WRAPPED = "sync_key.enc"
    private val KEY_FILE_LEGACY = "sync_key.txt"

    // Where the last successful sync time is kept so it survives a restart. Suffixed per
    // identity exactly like the database file, so the two always describe the same catalogue.
    private val SYNC_PREFS = "biap_sync"
    private val KEY_LAST_SYNC_AT = "last_sync_at"
    /** Set once the hand-typed karigar names on existing designs have been tied to the Hub. */
    private val KEY_KARIGARS_LINKED = "karigars_linked"
    private val dbLock = Mutex()
    private val backupLock = Mutex()

    private val _biData = MutableStateFlow(BusinessIntelligenceData())
    override val biData: StateFlow<BusinessIntelligenceData> = _biData.asStateFlow()

    override val authManager = AuthManager(context)
    // Also used by DriveImageStore: photos travel over the same transport as the data.
    val driveSyncManager = GoogleDriveSyncManager()
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * When a sync last actually completed. 0 means never, for this identity.
     *
     * Kept because the catalogue header claims to show it. It previously showed the newest
     * design's edit time instead, which looks identical most of the time and is wrong exactly
     * when it matters - after a sync that changed nothing, or a sync that silently failed.
     * A header that reports success it cannot observe is worse than one that says nothing.
     *
     * IT IS PERSISTED, and that is the point. This used to be memory-only, so 0 meant "not
     * since this launch" and the header announced NOT SYNCED YET on every cold start no
     * matter how recently the owner had synced. That is the same lie in the other direction:
     * a header denying a success that did happen. Stored under the same suffix as the
     * database, so signing into another account reports that account's sync rather than
     * inheriting the previous one's.
     */
    private val _lastSyncAt = MutableStateFlow(0L)
    override val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    private var driveFileId: String? = null

    private var currentDb: AppDatabase? = null
    private val currentDaoFlow = MutableStateFlow<DesignDao?>(null)

    /**
     * The signed-in person's own details.
     *
     * Follows the active DAO rather than being read once, so switching accounts swaps the
     * profile along with the catalogue instead of leaving the previous person's name on
     * screen. Empty while no database is open.
     */
    override val profile: StateFlow<UserProfile> =
        currentDaoFlow
            .flatMapLatest { dao ->
                dao?.getProfileFlow()?.map { it?.toDomain() ?: UserProfile() }
                    ?: flowOf(UserProfile())
            }
            .stateIn(repositoryScope, SharingStarted.Eagerly, UserProfile())

    override suspend fun saveProfile(profile: UserProfile) {
        val dao = awaitDao() ?: return
        // Stamped here rather than by the caller: the timestamp decides which side wins a
        // merge, so it must reflect when the write actually happened.
        dao.upsertProfile(profile.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }
    private var dbCollectionJob: Job? = null
    // Exactly one live WorkInfo observer. Previously every syncWithGoogleDrive() call
    // launched a collector that never completed, so N taps left N collectors racing to
    // write _syncStatus - and a cancelled one could overwrite a newer one's Success.
    private var syncObserverJob: Job? = null

    init {
        repositoryScope.launch {
            signedInUserKeyFlow().collect { userKey ->
                dbLock.withLock {
                    // Close previous database connections and cancel current collections
                    dbCollectionJob?.cancelAndJoin()
                    currentDb?.close()
                    currentDb = null
                    currentDaoFlow.value = null
                    driveFileId = null

                    val suffix = getDatabaseSuffix(userKey)
                    // Before the database opens, so the header never flashes NOT SYNCED YET
                    // on the way in.
                    _lastSyncAt.value = readLastSyncAt(suffix)
                    adoptLegacyGuestDatabaseIfPresent(suffix)
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

                    // Seed the Business Hub the first time this database is opened.
                    //
                    // Guarded on BOTH tables being empty AND no tombstones, rather than just
                    // "no rows". Without the tombstone check, deleting every activity would
                    // look identical to a fresh database and the app would helpfully put
                    // them all back on the next launch - the deletion would never stick.
                    repositoryScope.launch {
                        try {
                            if (dao.activitiesOnce().isEmpty() && dao.deletedActivitiesOnce().isEmpty()) {
                                val now = System.currentTimeMillis()
                                dao.upsertActivities(Activities.DEFAULTS.map { ActivityEntity(it, now) })
                            }
                            if (dao.karigarsOnce().isEmpty() && dao.deletedKarigarsOnce().isEmpty()) {
                                val now = System.currentTimeMillis()
                                dao.upsertKarigars(
                                    DEFAULT_KARIGARS.map {
                                        KarigarEntity(
                                            id = java.util.UUID.randomUUID().toString(),
                                            name = it,
                                            activities = emptyList(),
                                            updatedAt = now,
                                        )
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Tie the karigar names already on designs to the Hub list. Runs once
                    // per database, after the seeding above so there is a list to tie them to.
                    repositoryScope.launch { linkDesignKarigarsToHub(dao, suffix) }

                    // Retry any photo that exists on this device but never reached Storage.
                    repositoryScope.launch { backfillPendingImageUploads(dao) }

                    // Announce this account in the access directory so an admin can find
                    // and grade them. Creates the record at VIEWER; the role itself is only
                    // ever written by an access manager, and only server-side rules decide
                    // whether that write is allowed.
                    if (userKey != null) {
                        repositoryScope.launch { AccessLevelStore.publishIdentity() }
                    }

                    // Start collecting from Room Flow and updating UI state Flow
                    dbCollectionJob = repositoryScope.launch {
                        // The two order flows are paired first because combine tops out at
                        // five sources; six would fall back to the untyped vararg overload.
                        val ordersFlow = combine(
                            dao.getAllOrdersFlow(),
                            dao.getAllDeletedOrdersFlow()
                        ) { orders, deletedOrders -> orders to deletedOrders }

                        val jobWorkFlow = combine(
                            dao.allJobWorkFlow(),
                            dao.deletedJobWorkFlow()
                        ) { jobs, deleted -> jobs to deleted }

                        val hubFlow = combine(
                            dao.allBatchesFlow(),
                            dao.karigarsFlow(),
                            dao.activitiesFlow()
                        ) { batches, karigars, activities -> Triple(batches, karigars, activities) }

                        combine(
                            dao.getAllDesignsFlow(),
                            dao.getAllGlobalCustomFieldsFlow(),
                            dao.getAllDeletedDesignsFlow(),
                            dao.getAllDeletedCustomFieldsFlow(),
                            ordersFlow
                        ) { designsList, globalsList, deletedList, deletedFieldsList, orders ->
                            val (ordersList, deletedOrdersList) = orders
                            BusinessIntelligenceData(
                                designs = designsList.map { it.toDomain() },
                                globalCustomFields = globalsList.map { it.field }.toSet(),
                                globalCustomFieldsMetadata = globalsList.associate { it.field to it.updatedAt },
                                deletedDesignIds = deletedList.map { it.designId }.toSet(),
                                deletedDesigns = deletedList.associate { it.designId to it.deletedAt },
                                deletedCustomFields = deletedFieldsList.associate { it.field to it.deletedAt },
                                orders = ordersList.map { it.toDomain() },
                                deletedOrders = deletedOrdersList.associate { it.orderId to it.deletedAt }
                            )
                        }
                            // The Hub and the batch ledger ride in on a second combine for
                            // the same reason the orders pair do: five sources is the typed
                            // limit, and an untyped vararg here would turn four different
                            // row types into List<Any?> at the exact point they are mapped.
                            .combine(hubFlow) { data, hub ->
                                val (batchRows, karigarRows, activityRows) = hub
                                data.copy(
                                    batches = batchRows.map { it.toDomain() },
                                    karigars = karigarRows.map { it.toDomain() },
                                    activities = activityRows.associate { it.name to it.updatedAt },
                                )
                            }
                            // Job work rides in on a third combine for the same reason again.
                            .combine(jobWorkFlow) { data, jobs ->
                                val (jobRows, deletedJobRows) = jobs
                                data.copy(
                                    jobWork = jobRows.map { it.toDomain() },
                                    deletedJobWork = deletedJobRows.associate { it.id to it.deletedAt },
                                )
                            }
                            .collect { merged ->
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

    // The database is keyed by the app identity (Firebase uid), NOT by the Google account.
    // Google sign-in is an authorisation for Drive, not a statement of who the user is;
    // keying on it meant every phone/email user fell through to the shared guest DB.
    private fun getDatabaseSuffix(userKey: String?): String {
        return if (!userKey.isNullOrBlank()) {
            val bytes = userKey.toByteArray()
            val hash = java.security.MessageDigest.getInstance("MD5").digest(bytes)
            val hex = hash.joinToString("") { "%02x".format(it) }
            "_$hex"
        } else {
            ""
        }
    }

    /** Current app identity, or null when signed out. Drive state is deliberately not consulted. */
    private fun currentUserKey(): String? = FirebaseAuth.getInstance().currentUser?.uid

    // #region Last sync time, persisted

    private fun syncPrefs() =
        context.applicationContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)

    private fun readLastSyncAt(suffix: String): Long =
        syncPrefs().getLong(KEY_LAST_SYNC_AT + suffix, 0L)

    /**
     * One place that both publishes and stores the time, so the two can never disagree.
     *
     * Written synchronously with commit() rather than apply(): a sync often finishes as the
     * app is being put away, and apply() can lose the write if the process dies before the
     * background flush. Losing it puts the header straight back to its old lie.
     */
    private fun recordSyncCompleted() {
        val at = System.currentTimeMillis()
        _lastSyncAt.value = at
        syncPrefs().edit()
            .putLong(KEY_LAST_SYNC_AT + getDatabaseSuffix(currentUserKey()), at)
            .commit()
    }
    // #endregion

    /** Emits the app identity on sign-in/sign-out so the active database follows it. */
    private fun signedInUserKeyFlow(): Flow<String?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    /**
     * One-time adoption of the pre-identity database.
     *
     * Before the database was keyed on the Firebase uid, every phone/email user shared the
     * unsuffixed "bi_database.db". On the first authenticated open we hand that file to the
     * signed-in user rather than stranding it.
     *
     * Deliberately conservative: it only ever runs when the destination does NOT exist, so it
     * cannot overwrite a real per-user database, and it renames rather than deletes - nothing
     * is destroyed even if the move is wrong.
     */
    private fun adoptLegacyGuestDatabaseIfPresent(suffix: String) {
        if (suffix.isEmpty()) return                       // signed out: guest DB IS the active DB
        val legacy = context.getDatabasePath("bi_database.db")
        val target = context.getDatabasePath("bi_database${suffix}.db")
        if (!legacy.exists() || target.exists()) return    // nothing to adopt, or already keyed
        try {
            // Move the WAL/SHM sidecars with the main file or SQLite will reject the result.
            for (ext in listOf("", "-wal", "-shm")) {
                val from = File(legacy.parentFile, "${legacy.name}$ext")
                if (from.exists()) {
                    from.renameTo(File(target.parentFile, "${target.name}$ext"))
                }
            }
            android.util.Log.i("DataRepository", "Adopted legacy guest database into $suffix")
        } catch (e: Exception) {
            e.printStackTrace()                            // leave the legacy file untouched on failure
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
        
        // A plaintext database can arrive at any time - Google Auto Backup restored one
        // during testing, right after the app was reinstalled. Encrypt it in place BEFORE
        // Room sees it, so it is never preserved as a readable copy by the recovery path
        // below. Cheap no-op when the file is already encrypted or absent.
        LegacyDatabaseMigrator.migrateIfPlaintext(context, dbFile, DatabaseKey.getOrCreate(context))

        fun buildAndVerify(): AppDatabase {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                // Encrypted at rest with SQLCipher. Previously this was plain SQLite, so a
                // copy of the file handed over every design, cost price and margin in the
                // business to anyone who could read app storage. The passphrase is random
                // per install and sealed in the Android Keystore, so a copied database is
                // useless without the phone it came from.
                .openHelperFactory(SupportOpenHelperFactory(DatabaseKey.getOrCreate(context)))
                // NO fallbackToDestructiveMigration: a missing migration used to wipe the
                // catalogue silently and call it success. Now the open throws, and the
                // caller below preserves a copy of the file before recreating anything -
                // so a schema mistake costs a support conversation, not the business's data.
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
        val profile = dao.getProfileOnce()?.toDomain()
        val orders = dao.getAllOrdersOnce()
        val deletedOrders = dao.getAllDeletedOrdersOnce()
        return BusinessIntelligenceData(
            designs = designs.map { it.toDomain() },
            globalCustomFields = globals.map { it.field }.toSet(),
            globalCustomFieldsMetadata = globals.associate { it.field to it.updatedAt },
            deletedDesignIds = deletedDesigns.map { it.designId }.toSet(),
            deletedDesigns = deletedDesigns.associate { it.designId to it.deletedAt },
            deletedCustomFields = deletedFields.associate { it.field to it.deletedAt },
            orders = orders.map { it.toDomain() },
            deletedOrders = deletedOrders.associate { it.orderId to it.deletedAt },
            profile = profile
        )
    }

    /**
     * Re-enqueue uploads for photos that exist on this device but never reached Storage.
     *
     * An upload used to be enqueued in exactly ONE place: the instant a design was saved. If
     * that upload failed - no network, an unregistered App Check debug token, rules still
     * propagating - the worker gave up after its attempt cap and NOTHING ever tried again.
     * imageRef stayed null forever, so the photo existed only as a local file. Reinstalling
     * the app then lost it, even though the design record itself synced back intact: sizes,
     * stock and prices returned, the photograph did not.
     *
     * Running this every time a database is opened turns a permanent silent loss into a
     * retry on the next launch.
     *
     * Deliberately conservative: it only enqueues when the local file is actually present,
     * so it can never fabricate an upload for a photo this device does not have.
     */
    private suspend fun backfillPendingImageUploads(dao: DesignDao) {
        try {
            // Sweeps every photograph on every design, not just the cover. A design's second
            // photo can fail to upload exactly as easily as its first, and would have been
            // just as permanently and silently local.
            val pending = dao.getAllDesignsOnce()
                .map { it.toDomain() }
                .flatMap { design -> design.photos.map { design.id to it } }
                .filter { (_, photo) -> photo.ref.isNullOrBlank() && !photo.localPath.isNullOrBlank() }
                .mapNotNull { (designId, photo) ->
                    val file = FileUtils.resolveImageFile(context, photo.localPath)
                    if (file != null && file.exists()) designId to file.absolutePath else null
                }

            if (pending.isEmpty()) return

            android.util.Log.i(
                "DataRepository",
                "Image backfill: re-queueing ${pending.size} photo(s) that never reached Storage"
            )
            pending.forEach { (designId, path) -> enqueueImageUpload(designId, path) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerLocalBackup() {
        val suffix = getDatabaseSuffix(currentUserKey())
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

    override fun setSyncPassphrase(passphrase: CharArray) {
        PassphraseStore.save(context, passphrase)
        syncWithGoogleDrive()
    }

    override fun hasSyncPassphrase(): Boolean = PassphraseStore.isSet(context)

    override fun localDesignCount(): Int = _biData.value.designs.size

    /**
     * Change the passphrase while keeping the backup readable.
     *
     * The sync KEY is never regenerated - only the envelope around it. That is the whole
     * point: the catalogue in Drive stays encrypted under the same key, so it remains
     * decryptable and no other device is orphaned. Only sync_key.enc is rewritten, and it is
     * rewritten IN PLACE using its existing file id, so Drive gains no second copy.
     */
    override suspend fun changeSyncPassphrase(
        current: CharArray,
        new: CharArray,
    ): String? = withContext(Dispatchers.IO) {
        if (new.size < SyncPassphrase.MIN_LENGTH) {
            return@withContext "Use at least ${SyncPassphrase.MIN_LENGTH} characters."
        }
        val token = authManager.getAccessToken()
            ?: return@withContext "Could not reach Google Drive. Check your connection."

        val keyFileId = driveSyncManager.searchFile(token, KEY_FILE_WRAPPED)
            ?: return@withContext "No backup found on Drive yet. Sync once first."

        val envelope = driveSyncManager.downloadFile(token, keyFileId)?.trim()
            ?: return@withContext "Could not read the backup key from Drive."

        val syncKey = SyncPassphrase.unwrap(envelope, current)
        if (syncKey == null || !EncryptionUtils.isValidKey(syncKey)) {
            return@withContext "That is not your current passphrase."
        }

        val rewrapped = SyncPassphrase.wrap(syncKey, new)
        // Existing id passed deliberately: this REPLACES the envelope rather than adding one.
        if (driveSyncManager.uploadFile(token, rewrapped, keyFileId, KEY_FILE_WRAPPED) == null) {
            return@withContext "Could not save the new passphrase to Drive."
        }

        PassphraseStore.save(context, new)
        return@withContext null
    }

    /**
     * Start the backup over under a new passphrase, for a passphrase that is simply gone.
     *
     * WHY THIS CANNOT "RECOVER" ANYTHING:
     *   The old envelope can only be opened by the old passphrase. Without it the existing
     *   ciphertext is unreadable by anyone, including us. So there is nothing to recover -
     *   only a choice between leaving dead ciphertext in Drive forever, or replacing it.
     *
     * WHY IT DOES NOT ACCUMULATE:
     *   Both files are overwritten at their EXISTING file ids. However many times somebody
     *   forgets, the Drive folder still holds exactly one sync_key.enc and one
     *   bi_data.json.enc. The bytes being replaced were permanently undecryptable, so
     *   nothing of value is destroyed.
     *
     * WHY IT REFUSES ON AN EMPTY DEVICE:
     *   With no local designs there is nothing to re-upload, so this would trade an
     *   unreadable backup for an empty one - and if another device still holds the old
     *   passphrase, that device could have restored it. Guarded by the caller via
     *   [localDesignCount].
     */
    override suspend fun resetSyncPassphrase(new: CharArray): String? = withContext(Dispatchers.IO) {
        if (new.size < SyncPassphrase.MIN_LENGTH) {
            return@withContext "Use at least ${SyncPassphrase.MIN_LENGTH} characters."
        }
        if (_biData.value.designs.isEmpty()) {
            return@withContext "There are no designs on this phone to back up."
        }
        val token = authManager.getAccessToken()
            ?: return@withContext "Could not reach Google Drive. Check your connection."

        // A brand-new key. The old one is unreachable, so it cannot be carried forward.
        val freshKey = EncryptionUtils.generateRandomKey()

        val keyFileId = driveSyncManager.searchFile(token, KEY_FILE_WRAPPED)
        if (driveSyncManager.uploadFile(
                token,
                SyncPassphrase.wrap(freshKey, new),
                keyFileId,
                KEY_FILE_WRAPPED,
            ) == null
        ) {
            return@withContext "Could not save the new key to Drive."
        }

        // Re-encrypt this device's catalogue under the new key and overwrite the data file
        // too - otherwise the new key would point at ciphertext it cannot open.
        val payload = json.encodeToString(_biData.value)
        val encrypted = runCatching { EncryptionUtils.encryptWithKey(payload, freshKey) }
            .getOrNull()
            ?: return@withContext "Could not encrypt the catalogue."

        val dataFileId = driveSyncManager.searchFile(token)
        if (driveSyncManager.uploadFile(token, encrypted, dataFileId, "bi_data.json.enc") == null) {
            return@withContext "Could not upload the new backup."
        }

        // Any plaintext key left by an ancient build would now unlock nothing, but leaving
        // it implies otherwise.
        driveSyncManager.searchFile(token, KEY_FILE_LEGACY)?.let {
            driveSyncManager.deleteFile(token, it)
        }

        PassphraseStore.save(context, new)
        recordSyncCompleted()
        return@withContext null
    }

    override fun resetSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }

    override fun syncWithGoogleDrive() {
        // Debounce: ignore taps while a sync is already in flight rather than enqueueing
        // another one. REPLACE would cancel the running job and restart it, which is both
        // wasteful and the reason rapid taps used to end with no status at all.
        if (_isSyncing.value) return

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
                BackoffPolicy.EXPONENTIAL,
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

        // Only ever one observer alive; the previous one cannot outlive its request and
        // clobber this one's result.
        syncObserverJob?.cancel()
        syncObserverJob = repositoryScope.launch {
            wm.getWorkInfoByIdFlow(syncRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        androidx.work.WorkInfo.State.ENQUEUED -> {
                            // Still pending - either the first run or, after a retry, waiting
                            // out the backoff. Treating this as Idle is what made a retrying
                            // sync go completely silent: the spinner stopped and no message
                            // was ever shown. It is still working, so say so.
                            _isSyncing.value = true
                            _syncStatus.value = SyncStatus.Syncing
                        }
                        androidx.work.WorkInfo.State.RUNNING -> {
                            _isSyncing.value = true
                            _syncStatus.value = SyncStatus.Syncing
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            _isSyncing.value = false
                            recordSyncCompleted()
                            _syncStatus.value = SyncStatus.Success("Sync completed successfully.")
                            return@collect   // terminal: stop observing
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            _isSyncing.value = false
                            _syncStatus.value = SyncStatus.Error("Sync failed in background execution.")
                            return@collect   // terminal: stop observing
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

            // --- Key management ---
            //
            // The sync key used to be uploaded as sync_key.txt in PLAIN TEXT, into the same
            // Drive folder as the data it decrypts. Ciphertext and key side by side, which
            // made the encryption close to decorative: anyone reaching the folder - or
            // Google - held both halves.
            //
            // It is now wrapped with a passphrase that never leaves the device, so the
            // folder holds two ciphertexts and no key. The cost is that a sync cannot
            // proceed without the passphrase, and that is the point.
            val localKey = EncryptionUtils.getOrGenerateLocalSyncKey(context)
            val passphrase = PassphraseStore.get(context)

            val wrappedFileId = driveSyncManager.searchFile(token, KEY_FILE_WRAPPED)
            val legacyFileId = driveSyncManager.searchFile(token, KEY_FILE_LEGACY)

            if (passphrase == null) {
                // Distinguishes "choose one" from "enter the existing one", so the UI can
                // ask the right question instead of a generic prompt.
                val isFirstEver = wrappedFileId == null && legacyFileId == null
                _syncStatus.value = SyncStatus.PassphraseRequired(isNewPassphrase = isFirstEver)
                return@withContext ListenableWorker.Result.failure()
            }

            var candidateKey = localKey

            when {
                wrappedFileId != null -> {
                    val envelope = driveSyncManager.downloadFile(token, wrappedFileId)?.trim()
                    val unwrapped = envelope?.let { SyncPassphrase.unwrap(it, passphrase) }
                    if (unwrapped == null || !EncryptionUtils.isValidKey(unwrapped)) {
                        // Refuse rather than fall back to the local key: continuing would
                        // re-encrypt the catalogue under a different key and orphan every
                        // other device permanently.
                        _syncStatus.value = SyncStatus.PassphraseRequired(isNewPassphrase = false)
                        return@withContext ListenableWorker.Result.failure()
                    }
                    candidateKey = unwrapped
                }

                legacyFileId != null -> {
                    // Migrate a plaintext key left by an older build: adopt it, re-upload it
                    // wrapped, then delete the plaintext copy so it stops sitting there.
                    val plain = driveSyncManager.downloadFile(token, legacyFileId)?.trim()
                    if (!plain.isNullOrBlank() && EncryptionUtils.isValidKey(plain)) {
                        candidateKey = plain
                    }
                    val wrapped = SyncPassphrase.wrap(candidateKey, passphrase)
                    if (driveSyncManager.uploadFile(token, wrapped, null, KEY_FILE_WRAPPED) != null) {
                        driveSyncManager.deleteFile(token, legacyFileId)
                    }
                }

                else -> {
                    // First sync for this Drive: publish the key wrapped from the outset.
                    driveSyncManager.uploadFile(
                        token,
                        SyncPassphrase.wrap(candidateKey, passphrase),
                        null,
                        KEY_FILE_WRAPPED
                    )
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
                    // The escrowed key did not match the real payload; repair it - wrapped,
                    // never in plain text, which is what this whole change is about.
                    if (wrappedFileId != null) {
                        driveSyncManager.uploadFile(
                            token,
                            SyncPassphrase.wrap(localKey, passphrase),
                            wrappedFileId,
                            KEY_FILE_WRAPPED
                        )
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
        // Signing out of the app clears the identity as well as the Drive grant,
        // so the two can never drift apart.
        FirebaseAuth.getInstance().signOut()
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

    override fun enqueueImageUpload(designId: String, localPath: String): String {
        val key = DriveImageStore.newVersionKey(designId)
        val request = OneTimeWorkRequestBuilder<ImageUploadWorker>()
            .setInputData(
                workDataOf(
                    ImageUploadWorker.KEY_DESIGN_ID to designId,
                    ImageUploadWorker.KEY_LOCAL_PATH to localPath,
                    ImageUploadWorker.KEY_STORAGE_KEY to key,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        // REPLACE, and keyed by the LOCAL PATH as well as the design: a rapid re-shoot of one
        // photo still supersedes its own pending upload, but five photos on one design no
        // longer evict each other. Keyed by design alone, adding five at once would have left
        // exactly one uploaded.
        WorkManager.getInstance(context).enqueueUniqueWork(
            ImageUploadWorker.workName(designId, localPath),
            ExistingWorkPolicy.REPLACE,
            request
        )
        return key
    }

    /**
     * Point one photograph at its uploaded bytes.
     *
     * [localPath] says WHICH photograph. The design may hold five, and the cover lives in
     * different columns from the rest, so the key alone cannot say where the pointer belongs.
     *
     * Returns true when the photo is no longer on the design at all - removed or replaced
     * while the upload was in flight. The bytes are safely stored and simply unreferenced;
     * the orphan sweep collects them. Reporting that as a failure would make the worker retry
     * to its limit over a job that has nothing left to do.
     */
    override suspend fun applyUploadedImage(
        designId: String,
        localPath: String,
        key: String,
        uploadedBy: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val dao = awaitDao() ?: return@withContext false
            val design = dao.getDesignById(designId)?.toDomain() ?: return@withContext true
            val now = System.currentTimeMillis()

            when {
                design.imagePath == localPath -> {
                    // Targeted column update: whatever the user edited while the upload was
                    // in flight stays exactly as they left it.
                    dao.updateImagePointer(designId, key, now, uploadedBy)
                }

                design.extraImages.any { it.localPath == localPath } -> {
                    val moved = design.extraImages.map { photo ->
                        if (photo.localPath == localPath) {
                            photo.copy(ref = key, uploadedBy = uploadedBy)
                        } else {
                            photo
                        }
                    }
                    dao.updateExtraImages(designId, moved, now)
                }

                else -> {
                    android.util.Log.i(
                        "DataRepository",
                        "uploaded $key but $localPath is no longer on design $designId - leaving it unreferenced"
                    )
                    return@withContext true
                }
            }
            triggerLocalBackup()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Every storage key this design currently points at, for the retention sweep to spare.
     *
     * Empty on any failure, which makes the sweep protect nothing - so callers must treat an
     * empty result as "do not prune" rather than "prune everything".
     */
    override suspend fun referencedImageKeys(designId: String): Set<String> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val dao = awaitDao() ?: return@withContext emptySet()
                val design = dao.getDesignById(designId)?.toDomain() ?: return@withContext emptySet()
                design.photos.mapNotNull { it.ref?.takeIf { key -> key.isNotBlank() } }.toSet()
            } catch (e: Exception) {
                e.printStackTrace()
                emptySet()
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

            // A local photo with no remote pointer is one that has not been uploaded yet -
            // either brand new, or a replacement (picking a new photo clears its ref).
            // Hooked here rather than in each ViewModel so a save path added later cannot
            // silently skip uploading. Sync does not come through here; it uses importData.
            //
            // Every photograph, not just the cover: a design saved with five new photos has
            // five uploads to do, and the one that ran the cover alone would have left four
            // of them existing only on this phone.
            designWithTime.photos
                .filter { !it.localPath.isNullOrBlank() && it.ref.isNullOrBlank() }
                .forEach { photo ->
                    val file = FileUtils.resolveImageFile(context, photo.localPath)
                    if (file != null && file.exists()) {
                        enqueueImageUpload(design.id, file.absolutePath)
                    }
                }
        }
    }

    /**
     * Retire a design without destroying it.
     *
     * This is what the delete button now does. A discontinued style is history worth keeping -
     * its costs, prices and karigars are the record of what the workshop actually made - so
     * the ordinary action hides it rather than erasing it. [deleteDesign] still exists for
     * genuine mistakes, reachable only once a design is already archived.
     */
    /**
     * Take pieces out of stock and write the order that accounts for them.
     *
     * The prices are read HERE, from the database, rather than taken from whatever the screen
     * was showing: the screen may have been open a while, and the price an order records has
     * to be the price at the moment it was placed.
     */
    override suspend fun placeOrder(lines: List<OrderLine>): String? {
        if (lines.isEmpty()) return "Nothing selected."
        val dao = awaitDao() ?: return "Still opening your catalogue - try again in a moment."
        return try {
            val now = System.currentTimeMillis()
            val priced = lines.map { line ->
                val design = dao.getDesignById(line.designId)
                    ?: return "A design in this order no longer exists."
                val inventory = design.inventories.firstOrNull { it.size == line.size }
                    ?: return "Size ${line.size} is no longer on ${design.design.name}."
                line.copy(unitPrice = inventory.price)
            }
            dao.placeOrder(
                Order(
                    id = java.util.UUID.randomUUID().toString(),
                    createdAt = now,
                    lines = priced,
                    updatedAt = now,
                ).toEntity()
            )
            triggerLocalBackup()
            null
        } catch (e: IllegalArgumentException) {
            // Thrown by DesignDao.placeOrder when a line outruns the stock left, which rolls
            // the whole order back. Reported rather than swallowed: the number on screen was
            // stale, and the person needs to know that before they pack anything.
            e.message ?: "Not enough stock left for this order."
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not save the order."
        }
    }

    /**
     * Change what is on an existing order.
     *
     * PRICES STAY AS THE ORDER RECORDED THEM. A line that was already on the order keeps the
     * price it went out at, however many pieces it now carries - raising a size from two to
     * three pieces on a June order bills the third at June's rate, because the order is a
     * record of one sale at one set of prices, not a live quotation. A design's price rising
     * between batches must not silently revalue what was already sold.
     *
     * A genuinely NEW line is the exception, and it has no alternative: the catalogue keeps
     * only the current price, so a size that was never on this order has no historic price
     * to inherit and takes today's.
     */
    override suspend fun editOrder(orderId: String, lines: List<OrderLine>): String? {
        if (lines.isEmpty()) return "An order needs at least one piece. Cancel it instead."
        val dao = awaitDao() ?: return "Still opening your catalogue - try again in a moment."
        return try {
            val existing = dao.getOrderById(orderId)?.toDomain()
                ?: return "That order no longer exists."

            // Lines the order already had come back carrying their own recorded price; a size
            // it never had comes back untouched at zero, and only those read the catalogue.
            val priced = keepRecordedPrices(lines, existing.lines).map { line ->
                if (line.unitPrice > 0L) return@map line
                val design = dao.getDesignById(line.designId)
                    ?: return "A design in this order no longer exists."
                val inventory = design.inventories.firstOrNull { it.size == line.size }
                    ?: return "Size ${line.size} is no longer on ${design.design.name}."
                line.copy(unitPrice = inventory.price)
            }

            dao.editOrder(orderId, priced, System.currentTimeMillis())
            triggerLocalBackup()
            null
        } catch (e: IllegalArgumentException) {
            // Thrown by DesignDao.editOrder when a size outruns what is left, which rolls the
            // whole edit back. Reported rather than swallowed: the numbers on screen were
            // stale, and that has to be said before anything is packed.
            e.message ?: "Not enough stock left for this order."
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not save the changes."
        }
    }

    override suspend fun orderById(orderId: String): Order? = withContext(Dispatchers.IO) {
        return@withContext try {
            awaitDao()?.getOrderById(orderId)?.toDomain()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun cancelOrder(orderId: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val dao = awaitDao()
                ?: return@withContext "Still opening your catalogue - try again in a moment."
            dao.cancelOrder(orderId, System.currentTimeMillis())
            triggerLocalBackup()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not cancel the order."
        }
    }

    // #region Batches

    override suspend fun suggestBatchNumber(designId: String, size: String): Int =
        withContext(Dispatchers.IO) {
            val dao = awaitDao() ?: return@withContext 1
            dao.suggestBatchNumber(designId, size)
        }

    // #region Job work (v10), grown to four kinds (v11)

    /**
     * Hand finished pieces to a karigar for further work.
     *
     * The shelf is reduced in the same transaction that writes the job, because the pieces
     * are no longer on it - and the job is what says where they went. Refusing to send more
     * than is in stock is the guard that matters: the alternative is negative stock standing
     * in for somebody's counting mistake.
     */
    override suspend fun sendOutForJobWork(
        designId: String, size: String, karigar: String, activity: String,
        quantity: Int, note: String,
    ): String? = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext "Enter how many pieces are going out."
        if (karigar.isBlank()) return@withContext "Choose who they are going to."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        val row = dao.getDesignById(designId) ?: return@withContext "That design is gone."
        val inStock = row.inventories.firstOrNull { it.size == size }?.stock ?: 0
        if (quantity > inStock) return@withContext "Only $inStock in stock for $size."

        val now = System.currentTimeMillis()
        val job = JobWork(
            kind = WorkKind.OUTWORK, designId = designId, size = size, karigar = karigar.trim(),
            activity = activity.trim(), sentQuantity = quantity, sentAt = now, note = note.trim(),
        )
        try {
            dao.sendOutWork(job.toEntity(), now, takeFromStock = true)
            null
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not send those out."
        }
    }

    /**
     * Commission pieces that do not exist yet.
     *
     * NOTHING COMES OFF THE SHELF. The karigar is being given an instruction, not goods, so
     * this works on a size standing at zero - which is the whole point, since an empty size
     * is the one most likely to need making.
     *
     * Every size of one order shares a lot id, so the four rows show as one card and come
     * back one size at a time, which is how they actually arrive.
     */
    override suspend fun startProduction(
        designId: String, karigar: String, activity: String,
        quantities: Map<String, Int>, batchNumber: Int, note: String,
    ): String? = withContext(Dispatchers.IO) {
        val wanted = quantities.filterValues { it > 0 }
        if (wanted.isEmpty()) return@withContext "Enter how many of each size to make."
        if (karigar.isBlank()) return@withContext "Choose who is making them."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        dao.getDesignById(designId) ?: return@withContext "That design is gone."

        val now = System.currentTimeMillis()
        val lotId = java.util.UUID.randomUUID().toString()
        val rows = wanted.map { (size, qty) ->
            JobWork(
                kind = WorkKind.PRODUCTION, lotId = lotId, designId = designId, size = size,
                karigar = karigar.trim(), activity = activity.trim(), sentQuantity = qty,
                batchNumber = batchNumber, sentAt = now, note = note.trim(),
            ).toEntity()
        }
        try {
            dao.insertJobWorks(rows)
            null
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not start that production."
        }
    }

    /**
     * Ask for one sample of a design that does not exist yet.
     *
     * It has a working name and no design id, because half the ideas discussed never become
     * anything and a catalogue full of those is worse than no record of them. The design is
     * created when the sample is approved, not before.
     */
    override suspend fun startSample(
        designName: String, karigar: String, activity: String, note: String,
        photoPath: String?, voicePath: String?,
    ): String? = withContext(Dispatchers.IO) {
        val name = designName.trim()
        if (name.isEmpty()) return@withContext "Give the new design a name to call it by."
        if (karigar.isBlank()) return@withContext "Choose who is making the sample."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."

        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        // Uploaded through the same worker the design photos use. A note that only exists on
        // the phone that recorded it is no use to the person who has to act on it, and this
        // is going to the whole workshop.
        val job = JobWork(
            id = id, kind = WorkKind.SAMPLE, designName = name, karigar = karigar.trim(),
            activity = activity.trim(), sentQuantity = 1, sentAt = now, note = note.trim(),
            photoPath = photoPath,
            photoRef = photoPath?.let { enqueueImageUpload(id, it) },
            voicePath = voicePath,
            voiceRef = voicePath?.let { enqueueImageUpload(id, it) },
        )
        try {
            dao.insertJobWork(job.toEntity())
            null
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not record that sample."
        }
    }

    /**
     * Send defective pieces back to be put right.
     *
     * These DO come off the shelf - they are real pieces being handed over - and they are
     * the ones that get forgotten, which is why their patience runs out in ten days rather
     * than thirty.
     */
    override suspend fun startAlteration(
        designId: String, size: String, karigar: String, quantity: Int, note: String,
        photoPath: String?, voicePath: String?,
    ): String? = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext "Enter how many pieces need altering."
        if (karigar.isBlank()) return@withContext "Choose who is altering them."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        val row = dao.getDesignById(designId) ?: return@withContext "That design is gone."
        val inStock = row.inventories.firstOrNull { it.size == size }?.stock ?: 0
        if (quantity > inStock) return@withContext "Only $inStock in stock for $size."

        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        val job = JobWork(
            id = id, kind = WorkKind.ALTERATION, designId = designId, size = size,
            karigar = karigar.trim(), activity = "Alteration", sentQuantity = quantity,
            sentAt = now, note = note.trim(),
            photoPath = photoPath,
            photoRef = photoPath?.let { enqueueImageUpload(id, it) },
            voicePath = voicePath,
            voiceRef = voicePath?.let { enqueueImageUpload(id, it) },
        )
        try {
            dao.sendOutWork(job.toEntity(), now, takeFromStock = true)
            null
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not record that alteration."
        }
    }

    /**
     * Record pieces coming back.
     *
     * Where they go depends on where they came from. Produced pieces are NEW and become a
     * batch of their own; returned pieces go back into the batch they left. Putting produced
     * pieces back into an old batch would inflate it past the quantity it was made at, and
     * the batch history would stop describing anything that happened.
     */
    override suspend fun receiveJobWork(jobId: String, quantity: Int): String? =
        withContext(Dispatchers.IO) {
            val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
            val row = dao.jobWorkById(jobId) ?: return@withContext "That job is gone."
            val now = System.currentTimeMillis()
            val job = row.toDomain()
            val updated = receive(job, quantity, now)
                ?: return@withContext "That is more than went out."
            try {
                when {
                    !job.kind.addsToStockOnReceive -> dao.insertJobWork(updated.toEntity())

                    job.kind == WorkKind.PRODUCTION -> {
                        val number = job.batchNumber.takeIf { it > 0 }
                            ?: dao.suggestBatchNumber(job.designId, job.size)
                        dao.receiveProducedWork(
                            updated.toEntity(),
                            BatchEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                designId = job.designId, size = job.size, number = number,
                                quantity = quantity, remaining = quantity,
                                price = dao.priceFor(job.designId, job.size) ?: 0L,
                                createdAt = now, updatedAt = now,
                                note = "From ${job.karigar}",
                            ),
                            quantity,
                        )
                    }

                    else -> dao.receiveReturnedWork(updated.toEntity(), quantity, now)
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                "Could not record that."
            }
        }

    /**
     * Finish a job. Anything never returned stays on the record as a shortfall.
     *
     * The missing pieces are NOT put back on the shelf. They did not come back, and writing
     * them into stock would be the ledger telling the owner a comfortable lie about cloth
     * that is not there.
     */
    override suspend fun closeJobWork(jobId: String): String? = withContext(Dispatchers.IO) {
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        val row = dao.jobWorkById(jobId) ?: return@withContext "That job is gone."
        try {
            dao.insertJobWork(close(row.toDomain(), System.currentTimeMillis()).toEntity())
            null
        } catch (e: Exception) {
            e.printStackTrace()
            "Could not close that."
        }
    }

    /**
     * Judge a sample.
     *
     * Approving it creates the design, because that is the moment it becomes a real thing the
     * workshop makes - before that it is an idea, and after it the owner will want to show it
     * to a customer whether or not any stock exists yet. It is created with no sizes and no
     * stock, which is honest: nothing has been made.
     */
    override suspend fun decideSample(jobId: String, approved: Boolean): String? =
        withContext(Dispatchers.IO) {
            val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
            val row = dao.jobWorkById(jobId) ?: return@withContext "That sample is gone."
            val job = row.toDomain()
            val now = System.currentTimeMillis()
            try {
                dao.insertJobWork(decideSample(job, approved, now).toEntity())
                if (approved) {
                    // No sizes and no stock, which is honest: nothing has been made yet.
                    // The owner prices it and sets its sizes when the first run is ordered.
                    dao.insertDesign(
                        DesignEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            name = job.designName.ifBlank { "New design" },
                            category = "Salwar Kameez",
                            imagePath = job.photoPath,
                            customFields = emptyMap(),
                            karigars = listOf(job.karigar),
                            updatedAt = now,
                        )
                    )
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                "Could not record that decision."
            }
        }
    // #endregion

    override suspend fun addBatch(
        designId: String,
        size: String,
        number: Int,
        quantity: Int,
        price: Long,
        note: String,
    ): String? = withContext(Dispatchers.IO) {
        if (quantity <= 0) return@withContext "A batch needs at least one piece."
        if (number <= 0) return@withContext "A batch number has to be 1 or more."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        try {
            val design = dao.getDesignById(designId)
                ?: return@withContext "That design no longer exists."
            if (design.inventories.none { it.size == size }) {
                return@withContext "Size " + size + " is no longer on " + design.design.name + "."
            }
            val existing = dao.batchesFor(designId, size).map { it.toDomain() }
            if (!isBatchNumberFree(existing, number)) {
                return@withContext "Batch " + number + " already exists for size " + size + "."
            }
            val now = System.currentTimeMillis()
            dao.addBatch(
                Batch(
                    id = java.util.UUID.randomUUID().toString(),
                    designId = designId,
                    size = size,
                    number = number,
                    quantity = quantity,
                    remaining = quantity,
                    price = price,
                    createdAt = now,
                    updatedAt = now,
                    note = note.trim(),
                ).toEntity()
            )
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Could not add that batch."
        }
    }

    override suspend fun resetSizeStock(designId: String, size: String): String? =
        withContext(Dispatchers.IO) {
            val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
            try {
                dao.resetSizeStock(designId, size, System.currentTimeMillis())
                null
            } catch (e: Exception) {
                e.printStackTrace()
                e.message ?: "Could not reset that size."
            }
        }

    override suspend fun resetDesignStock(designId: String): String? = withContext(Dispatchers.IO) {
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        try {
            dao.resetDesignStock(designId, System.currentTimeMillis())
            null
        } catch (e: Exception) {
            e.printStackTrace()
            e.message ?: "Could not reset that design."
        }
    }

    // #endregion

    /**
     * Tie the karigar names typed on existing designs to the Hub list.
     *
     * Before the Hub existed, every design carried names typed by hand, so the same person
     * appears as "Mohd. Yusuf" on one design and "Mohd Yusuf" on another and the app could
     * not tell they were one man. That is exactly what assigning work by activity needs to
     * know, so the old records are brought onto the list rather than left behind.
     *
     * NOTHING IS DROPPED. A name the Hub has never heard of joins the Hub; it is not removed
     * from the design. A design records who actually made it, and deleting a name to tidy a
     * list would be losing history.
     *
     * ONCE PER DATABASE, not on every launch. Re-running it would undo a deliberate removal:
     * take somebody off the Hub while a design still names them, and a second pass would put
     * them straight back. A design is only written when its names actually change, so the
     * pass is also harmless if it does run twice.
     */
    private suspend fun linkDesignKarigarsToHub(dao: DesignDao, suffix: String) {
        if (syncPrefs().getBoolean(KEY_KARIGARS_LINKED + suffix, false)) return
        try {
            val designs = dao.getAllDesignsOnce()
            val hub = dao.karigarsOnce().map { it.toDomain() }
            val plan = planKarigarLinks(designs.flatMap { it.design.karigars }, hub)

            if (!plan.isEmpty) {
                val now = System.currentTimeMillis()
                if (plan.toAdd.isNotEmpty()) {
                    dao.upsertKarigars(
                        plan.toAdd.map {
                            KarigarEntity(java.util.UUID.randomUUID().toString(), it, emptyList(), now)
                        }
                    )
                }
                for (row in designs) {
                    val linked = applyKarigarLinks(row.design.karigars, plan)
                    if (linked != row.design.karigars) {
                        dao.setDesignKarigars(row.design.id, linked, now)
                    }
                }
                android.util.Log.i(
                    "DataRepository",
                    "Linked karigars to the Hub: ${plan.renames.size} renamed, ${plan.toAdd.size} added"
                )
            }
            syncPrefs().edit().putBoolean(KEY_KARIGARS_LINKED + suffix, true).apply()
        } catch (e: Exception) {
            // Left unflagged on failure, so the next launch tries again rather than leaving
            // the catalogue half-linked for good.
            e.printStackTrace()
        }
    }

    // #region Business Hub

    override suspend fun addKarigar(name: String): String? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext "A name cannot be blank."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        // Case-insensitive, because two rows differing only in capitals are one person to
        // the workshop and would silently split their work between them.
        if (dao.karigarsOnce().map { it.toDomain() }.any { it.key == clean.lowercase() }) {
            return@withContext clean + " is already on the list."
        }
        dao.upsertKarigar(
            KarigarEntity(
                java.util.UUID.randomUUID().toString(),
                clean,
                emptyList(),
                System.currentTimeMillis(),
            )
        )
        null
    }

    override suspend fun renameKarigar(id: String, name: String): String? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext "A name cannot be blank."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        val all = dao.karigarsOnce().map { it.toDomain() }
        if (all.any { it.id != id && it.key == clean.lowercase() }) {
            return@withContext clean + " is already on the list."
        }
        val current = all.firstOrNull { it.id == id }
            ?: return@withContext "That karigar is no longer on the list."
        dao.upsertKarigar(current.copy(name = clean, updatedAt = System.currentTimeMillis()).toEntity())
        null
    }

    override suspend fun deleteKarigar(id: String): Unit = withContext(Dispatchers.IO) {
        val dao = awaitDao() ?: return@withContext
        dao.deleteKarigar(id, System.currentTimeMillis())
    }

    override suspend fun setKarigarActivities(id: String, activities: List<String>): Unit =
        withContext(Dispatchers.IO) {
            val dao = awaitDao() ?: return@withContext
            val current = dao.karigarsOnce().firstOrNull { it.id == id } ?: return@withContext
            dao.upsertKarigar(
                current.copy(
                    activities = activities.distinct(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }

    /**
     * Fold one karigar into another.
     *
     * The same man turns up as "Umesh" on an old design and "Umesh Devaliya" on the Hub, and
     * no amount of matching can safely decide that on its own - "Babli" and "Bablu" differ by
     * as little and are two people. So the app refuses to guess and this is how the owner
     * says so.
     *
     * Every design naming the one being folded away is rewritten to the survivor, the
     * survivor inherits both sets of activities, and only then does the duplicate leave the
     * Hub. Order matters: dropping the duplicate first would leave any design still naming
     * it pointing at somebody the Hub no longer has.
     */
    override suspend fun mergeKarigar(fromId: String, intoId: String): String? =
        withContext(Dispatchers.IO) {
            if (fromId == intoId) return@withContext "Those are the same person already."
            val dao = awaitDao()
                ?: return@withContext "Still opening your catalogue - try again in a moment."
            try {
                val all = dao.karigarsOnce().map { it.toDomain() }
                val from = all.firstOrNull { it.id == fromId }
                    ?: return@withContext "That karigar is no longer on the list."
                val into = all.firstOrNull { it.id == intoId }
                    ?: return@withContext "That karigar is no longer on the list."

                val now = System.currentTimeMillis()
                val plan = KarigarLinkPlan(renames = mapOf(from.name to into.name))
                for (row in dao.getAllDesignsOnce()) {
                    val linked = applyKarigarLinks(row.design.karigars, plan)
                    if (linked != row.design.karigars) {
                        dao.setDesignKarigars(row.design.id, linked, now)
                    }
                }
                dao.upsertKarigar(
                    into.copy(
                        activities = (into.activities + from.activities).distinct(),
                        updatedAt = now,
                    ).toEntity()
                )
                dao.deleteKarigar(from.id, now)
                null
            } catch (e: Exception) {
                e.printStackTrace()
                e.message ?: "Could not merge those two."
            }
        }

    override suspend fun addActivity(name: String): String? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty()) return@withContext "An activity needs a name."
        val dao = awaitDao() ?: return@withContext "Still opening your catalogue - try again in a moment."
        if (dao.activitiesOnce().any { it.name.equals(clean, ignoreCase = true) }) {
            return@withContext clean + " is already an activity."
        }
        dao.upsertActivity(ActivityEntity(clean, System.currentTimeMillis()))
        null
    }

    override suspend fun deleteActivity(name: String): Unit = withContext(Dispatchers.IO) {
        val dao = awaitDao() ?: return@withContext
        val now = System.currentTimeMillis()
        dao.deleteActivity(name, now)
        // Take it off everyone who was doing it. Left behind, they keep an assignment to
        // something that no longer exists and the auto-assignment keeps firing on it.
        dao.karigarsOnce()
            .filter { name in it.activities }
            .forEach { dao.upsertKarigar(it.copy(activities = it.activities - name, updatedAt = now)) }
    }

    // #endregion

    override fun setDesignArchived(designId: String, archived: Boolean) {
        repositoryScope.launch {
            val dao = awaitDao() ?: return@launch
            val now = System.currentTimeMillis()
            dao.setArchived(designId, archivedAt = if (archived) now else 0L, changedAt = now)
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
        if (currentUserKey() == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        var guestDb: AppDatabase? = null
        try {
            guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db")
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                // Encrypted like every other database. Missing this would leave the guest
                // catalogue as the one plaintext file on the device - the exact hole the
                // rest of this change closes.
                .openHelperFactory(SupportOpenHelperFactory(DatabaseKey.getOrCreate(context)))
                .build()
            guestDb.designDao().getAllDesignsOnce().isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            guestDb?.close()
        }
    }

    override suspend fun mergeGuestData(): Boolean = withContext(Dispatchers.IO) {
        if (currentUserKey() == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext false
        dbLock.withLock {
            val activeDao = awaitDao() ?: return@withContext false
            var guestDb: AppDatabase? = null
            try {
                guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db")
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                // Encrypted like every other database. Missing this would leave the guest
                // catalogue as the one plaintext file on the device - the exact hole the
                // rest of this change closes.
                .openHelperFactory(SupportOpenHelperFactory(DatabaseKey.getOrCreate(context)))
                .build()
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
        if (currentUserKey() == null) return@withContext false
        val dbFile = context.getDatabasePath("bi_database.db")
        if (!dbFile.exists()) return@withContext true
        var guestDb: AppDatabase? = null
        try {
            guestDb = Room.databaseBuilder(context, AppDatabase::class.java, "bi_database.db")
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                // Encrypted like every other database. Missing this would leave the guest
                // catalogue as the one plaintext file on the device - the exact hole the
                // rest of this change closes.
                .openHelperFactory(SupportOpenHelperFactory(DatabaseKey.getOrCreate(context)))
                .build()
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
