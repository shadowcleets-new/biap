package com.sc.biap.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sc.biap.data.BusinessIntelligenceData
import com.sc.biap.data.Design
import com.sc.biap.data.DesignPhoto
import com.sc.biap.data.OrderLine
import com.sc.biap.data.Batch
import com.sc.biap.data.allocateOldestFirst
import com.sc.biap.data.nextBatchNumber
import com.sc.biap.data.returnNewestFirst
import com.sc.biap.data.stockDeltas
import com.sc.biap.data.SizeInventory
import kotlinx.coroutines.flow.Flow

@Dao
interface DesignDao {
    @Transaction
    @Query("SELECT * FROM designs")
    fun getAllDesignsFlow(): Flow<List<DesignWithInventories>>

    @Transaction
    @Query("SELECT * FROM designs WHERE id = :id")
    suspend fun getDesignById(id: String): DesignWithInventories?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesign(design: DesignEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventories(inventories: List<SizeInventoryEntity>)

    @Query("DELETE FROM size_inventories WHERE designId = :designId")
    suspend fun deleteInventoriesForDesign(designId: String)

    @Transaction
    suspend fun insertDesignWithInventories(design: DesignEntity, inventories: List<SizeInventoryEntity>) {
        insertDesign(design)
        deleteInventoriesForDesign(design.id)
        insertInventories(inventories)
    }

    /**
     * Move ONLY the image pointer.
     *
     * Deliberately not a read-modify-write of the whole row: an upload completing in the
     * background must not carry a stale copy of the name/price/karigars back over whatever
     * the user has edited in the meantime.
     */
    @Query("UPDATE designs SET imageRef = :key, imageUpdatedAt = :updatedAt, imageUpdatedBy = :updatedBy WHERE id = :id")
    suspend fun updateImagePointer(id: String, key: String, updatedAt: Long, updatedBy: String?)

    @Query("SELECT imageRef FROM designs WHERE id = :id")
    suspend fun getImageRef(id: String): String?

    /**
     * Move the extra photos, and nothing else. The counterpart to [updateImagePointer].
     *
     * Same reasoning: an upload finishing in the background must not carry a stale copy of
     * the name, price or karigars back over whatever has been edited since.
     */
    @Query("UPDATE designs SET extraImages = :photos, extraImagesUpdatedAt = :updatedAt WHERE id = :id")
    suspend fun updateExtraImages(id: String, photos: List<DesignPhoto>, updatedAt: Long)

    /**
     * The raw extra-photo columns, one JSON string per design.
     *
     * Returns strings rather than `List<List<DesignPhoto>>` because Room cannot apply a type
     * converter to the element type of a multi-row result. Decode with
     * [Converters.toDesignPhotos], which already handles a corrupt column.
     */
    @Query("SELECT extraImages FROM designs")
    suspend fun getAllExtraImagesRaw(): List<String>

    /**
     * Archive or restore. Pass 0 as [archivedAt] to restore.
     *
     * [changedAt] is written to archiveUpdatedAt so sync can resolve the flag on its own
     * clock, independently of edits to the rest of the record. Deliberately does NOT touch
     * updatedAt: archiving is not an edit to the design's contents, and bumping it would let
     * an archive action win a merge against a genuine price change made elsewhere.
     */
    @Query("UPDATE designs SET archivedAt = :archivedAt, archiveUpdatedAt = :changedAt WHERE id = :id")
    suspend fun setArchived(id: String, archivedAt: Long, changedAt: Long)

    @Query("DELETE FROM designs WHERE id = :id")
    suspend fun deleteDesign(id: String)

    @Transaction
    suspend fun deleteDesignAndInventories(id: String) {
        deleteInventoriesForDesign(id)
        deleteDesign(id)
    }

    // --- Orders ---
    //
    // Placing and cancelling both move stock, so both are @Transaction: the order record and
    // the stock it accounts for either land together or not at all. A crash between the two
    // would otherwise leave pieces missing from the catalogue with nothing to say where they
    // went, or an order for pieces that were never taken out.

    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun getAllOrdersFlow(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders")
    suspend fun getAllOrdersOnce(): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrder(id: String)

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("SELECT * FROM deleted_orders")
    fun getAllDeletedOrdersFlow(): Flow<List<DeletedOrderEntity>>

    @Query("SELECT * FROM deleted_orders")
    suspend fun getAllDeletedOrdersOnce(): List<DeletedOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedOrder(deleted: DeletedOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedOrders(deletedList: List<DeletedOrderEntity>)

    @Query("DELETE FROM deleted_orders")
    suspend fun deleteAllDeletedOrders()

    @Query("DELETE FROM deleted_orders WHERE orderId = :orderId")
    suspend fun deleteDeletedOrderTombstone(orderId: String)

    /**
     * Take stock out. Returns the number of rows changed, so 0 means "not enough left".
     *
     * `stock >= :qty` in the WHERE clause rather than a read, a check in Kotlin and a write:
     * the check and the deduction are then a single statement the database evaluates, and two
     * orders for the last piece cannot both pass the check before either writes.
     */
    @Query(
        "UPDATE size_inventories SET stock = stock - :qty " +
            "WHERE designId = :designId AND size = :size AND stock >= :qty"
    )
    suspend fun decrementStock(designId: String, size: String, qty: Int): Int

    /** Put stock back when an order is cancelled. No ceiling: the pieces are returning. */
    @Query(
        "UPDATE size_inventories SET stock = stock + :qty WHERE designId = :designId AND size = :size"
    )
    suspend fun incrementStock(designId: String, size: String, qty: Int): Int

    /**
     * Bump a design's updatedAt after its stock moved.
     *
     * Not cosmetic. Sync resolves designs by last-write-wins on updatedAt, so a deduction
     * that left the timestamp alone would lose to any older copy in the backup and quietly
     * restore the stock it just took out.
     */
    @Query("UPDATE designs SET updatedAt = :now WHERE id = :id")
    suspend fun touchDesign(id: String, now: Long)

    /**
     * Rewrite only a design's karigar names.
     *
     * Deliberately one column rather than re-inserting the whole row: linking names to the
     * Hub runs over every design at once, and a full upsert would carry whatever this
     * device happens to hold for the photo pointer, the archive flag and the rest into a
     * write whose only purpose is a spelling.
     */
    @Query("UPDATE designs SET karigars = :karigars, updatedAt = :now WHERE id = :id")
    suspend fun setDesignKarigars(id: String, karigars: List<String>, now: Long)

    // #region Batches - the ledger beside the stock column

    @Query("SELECT * FROM batches WHERE designId = :designId AND size = :size ORDER BY number")
    suspend fun batchesFor(designId: String, size: String): List<BatchEntity>

    @Query("SELECT * FROM batches ORDER BY designId, size, number")
    suspend fun allBatchesOnce(): List<BatchEntity>

    @Query("SELECT * FROM batches ORDER BY designId, size, number")
    fun allBatchesFlow(): Flow<List<BatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatches(batches: List<BatchEntity>)

    @Query("UPDATE batches SET remaining = remaining - :qty, updatedAt = :now WHERE id = :id")
    suspend fun takeFromBatch(id: String, qty: Int, now: Long)

    @Query("UPDATE batches SET remaining = remaining + :qty, updatedAt = :now WHERE id = :id")
    suspend fun returnToBatch(id: String, qty: Int, now: Long)

    @Query("UPDATE batches SET remaining = 0, updatedAt = :now WHERE designId = :designId AND size = :size")
    suspend fun emptyBatchesForSize(designId: String, size: String, now: Long)

    @Query("UPDATE batches SET remaining = 0, updatedAt = :now WHERE designId = :designId")
    suspend fun emptyBatchesForDesign(designId: String, now: Long)

    @Query("UPDATE size_inventories SET stock = 0 WHERE designId = :designId AND size = :size")
    suspend fun zeroStockForSize(designId: String, size: String)

    @Query("UPDATE size_inventories SET stock = 0 WHERE designId = :designId")
    suspend fun zeroStockForDesign(designId: String)

    @Query("UPDATE size_inventories SET stock = stock + :qty WHERE designId = :designId AND size = :size")
    suspend fun addStock(designId: String, size: String, qty: Int)

    /**
     * Move pieces out of the ledger to match a deduction already made on the stock column.
     *
     * Separate from [decrementStock] rather than folded into it, because the two are not
     * always both needed: a design that predates batches has stock and no ledger, and must
     * keep selling. An empty ledger therefore means "unbatched" and is left alone rather
     * than being treated as "nothing left" - which would refuse every order on every design
     * that existed before the batch feature.
     */
    @Transaction
    suspend fun takeFromLedger(designId: String, size: String, qty: Int, now: Long) {
        val ledger = batchesFor(designId, size)
        if (ledger.isEmpty()) return
        val moves = allocateOldestFirst(ledger.map { it.toDomain() }, qty)
        for (move in moves) takeFromBatch(move.batchId, move.quantity, now)
    }

    /** The mirror of [takeFromLedger], for a cancellation or a reduced order. */
    @Transaction
    suspend fun returnToLedger(designId: String, size: String, qty: Int, now: Long) {
        val ledger = batchesFor(designId, size)
        if (ledger.isEmpty()) return
        val moves = returnNewestFirst(ledger.map { it.toDomain() }, qty)
        for (move in moves) returnToBatch(move.batchId, move.quantity, now)
    }

    /**
     * Add a batch and put its pieces into stock, in one transaction.
     *
     * The number is the caller's: [nextBatchNumber] suggests one, and the owner may type
     * over it. Both halves move together so the ledger cannot describe pieces the stock
     * column does not have.
     */
    @Transaction
    suspend fun addBatch(batch: BatchEntity) {
        insertBatch(batch)
        addStock(batch.designId, batch.size, batch.remaining)
        touchDesign(batch.designId, batch.updatedAt)
    }

    /** Suggests the next number for a size, so the UI and the DAO agree on one rule. */
    suspend fun suggestBatchNumber(designId: String, size: String): Int =
        nextBatchNumber(batchesFor(designId, size).map { it.toDomain() })

    /**
     * Reset one size to zero, keeping everything else about it.
     *
     * The price stays on the size, the karigars and details stay on the design, and every
     * batch keeps its row and its quantity - only `remaining` goes to 0. What was made is
     * history; what is left is a number.
     */
    @Transaction
    suspend fun resetSizeStock(designId: String, size: String, now: Long) {
        zeroStockForSize(designId, size)
        emptyBatchesForSize(designId, size, now)
        touchDesign(designId, now)
    }

    /** The same, for every size of a design at once. */
    @Transaction
    suspend fun resetDesignStock(designId: String, now: Long) {
        zeroStockForDesign(designId)
        emptyBatchesForDesign(designId, now)
        touchDesign(designId, now)
    }

    // #endregion

    /**
     * Record an order and take its pieces out of stock.
     *
     * Throws if any line asks for more than is left, which rolls the whole transaction back -
     * a partly-filled order is not a thing this app should be able to produce.
     */
    @Transaction
    suspend fun placeOrder(order: OrderEntity) {
        for (line in order.lines) {
            val changed = decrementStock(line.designId, line.size, line.quantity)
            require(changed == 1) {
                "Not enough stock left for size ${line.size}"
            }
            takeFromLedger(line.designId, line.size, line.quantity, order.createdAt)
            touchDesign(line.designId, order.createdAt)
        }
        insertOrder(order)
        deleteDeletedOrderTombstone(order.id)
    }

    /**
     * Change what is on an order, moving only the DIFFERENCE in and out of stock.
     *
     * NOT cancel-then-replace. That would return every piece and take them all out again,
     * and a failure between the two halves would leave the stock quietly restored with the
     * order still listed - pieces that read as available on the shelf but are already packed.
     * Here the two directions happen inside one transaction, so either the whole edit lands
     * or nothing moves at all.
     *
     * Quantities are summed per design-and-size before comparing, so an order that somehow
     * carries the same size on two lines is still counted once.
     *
     * Throws if any size is asked for more than is left, which rolls the whole edit back.
     * `createdAt` is deliberately carried over: editing an order does not make it a new one.
     */
    @Transaction
    suspend fun editOrder(orderId: String, newLines: List<OrderLine>, now: Long) {
        val existing = getOrderById(orderId)
            ?: throw IllegalArgumentException("That order no longer exists.")

        for ((key, delta) in stockDeltas(existing.lines, newLines)) {
            val (designId, size) = key
            if (delta > 0) {
                val changed = decrementStock(designId, size, delta)
                require(changed == 1) { "Not enough stock left for size $size" }
                takeFromLedger(designId, size, delta, now)
            } else {
                incrementStock(designId, size, -delta)
                returnToLedger(designId, size, -delta, now)
            }
            touchDesign(designId, now)
        }

        insertOrder(existing.copy(lines = newLines, updatedAt = now))
        deleteDeletedOrderTombstone(orderId)
    }

    /** Cancel an order: the pieces go back into stock and a tombstone stops it returning. */
    @Transaction
    suspend fun cancelOrder(id: String, deletedAt: Long) {
        val order = getOrderById(id) ?: return
        for (line in order.lines) {
            incrementStock(line.designId, line.size, line.quantity)
            returnToLedger(line.designId, line.size, line.quantity, deletedAt)
            touchDesign(line.designId, deletedAt)
        }
        deleteOrder(id)
        insertDeletedOrder(DeletedOrderEntity(id, deletedAt))
    }

    // #region Business Hub - karigars and the activities they do

    @Query("SELECT * FROM karigars ORDER BY name COLLATE NOCASE")
    fun karigarsFlow(): Flow<List<KarigarEntity>>

    @Query("SELECT * FROM karigars ORDER BY name COLLATE NOCASE")
    suspend fun karigarsOnce(): List<KarigarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKarigar(karigar: KarigarEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKarigars(karigars: List<KarigarEntity>)

    @Query("DELETE FROM karigars WHERE id = :id")
    suspend fun deleteKarigarRow(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedKarigar(row: DeletedKarigarEntity)

    @Query("SELECT * FROM deleted_karigars")
    suspend fun deletedKarigarsOnce(): List<DeletedKarigarEntity>

    @Query("DELETE FROM deleted_karigars WHERE karigarId = :id")
    suspend fun clearDeletedKarigar(id: String)

    /** Removing a karigar leaves a tombstone, or the next sync brings them straight back. */
    @Transaction
    suspend fun deleteKarigar(id: String, now: Long) {
        deleteKarigarRow(id)
        insertDeletedKarigar(DeletedKarigarEntity(id, now))
    }

    @Query("SELECT * FROM activities ORDER BY name COLLATE NOCASE")
    fun activitiesFlow(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities")
    suspend fun activitiesOnce(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: ActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(activities: List<ActivityEntity>)

    @Query("DELETE FROM activities WHERE name = :name")
    suspend fun deleteActivityRow(name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedActivity(row: DeletedActivityEntity)

    @Query("SELECT * FROM deleted_activities")
    suspend fun deletedActivitiesOnce(): List<DeletedActivityEntity>

    @Transaction
    suspend fun deleteActivity(name: String, now: Long) {
        deleteActivityRow(name)
        insertDeletedActivity(DeletedActivityEntity(name, now))
    }

    // #endregion

    // Global custom fields
    // --- Profile (exactly one row; see UserProfileEntity) ---

    @Query("SELECT * FROM user_profile WHERE id = :id")
    fun getProfileFlow(id: Int = UserProfileEntity.SINGLETON_ID): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :id")
    suspend fun getProfileOnce(id: Int = UserProfileEntity.SINGLETON_ID): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM global_custom_fields")
    fun getAllGlobalCustomFieldsFlow(): Flow<List<GlobalCustomFieldEntity>>

    @Query("SELECT * FROM global_custom_fields")
    suspend fun getAllGlobalCustomFields(): List<GlobalCustomFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalCustomFields(fields: List<GlobalCustomFieldEntity>)

    @Query("DELETE FROM global_custom_fields WHERE field = :field")
    suspend fun deleteGlobalCustomField(field: String)

    // Deleted designs (tombstones)
    @Query("SELECT * FROM deleted_designs")
    fun getAllDeletedDesignsFlow(): Flow<List<DeletedDesignEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedDesign(deleted: DeletedDesignEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedDesigns(deletedList: List<DeletedDesignEntity>)

    // Deleted custom fields (tombstones)
    @Query("SELECT * FROM deleted_custom_fields")
    fun getAllDeletedCustomFieldsFlow(): Flow<List<DeletedCustomFieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedCustomField(deleted: DeletedCustomFieldEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedCustomFields(deletedList: List<DeletedCustomFieldEntity>)

    @Query("DELETE FROM deleted_custom_fields WHERE field = :field")
    suspend fun deleteDeletedCustomFieldTombstone(field: String)

    @Query("DELETE FROM designs")
    suspend fun deleteAllDesigns()

    @Query("DELETE FROM size_inventories")
    suspend fun deleteAllInventories()

    @Query("DELETE FROM global_custom_fields")
    suspend fun deleteAllGlobalCustomFields()

    @Query("DELETE FROM deleted_designs")
    suspend fun deleteAllDeletedDesigns()

    @Query("DELETE FROM deleted_custom_fields")
    suspend fun deleteAllDeletedCustomFields()

    @Transaction
    suspend fun importData(data: BusinessIntelligenceData) {
        deleteAllDesigns()
        deleteAllInventories()
        deleteAllGlobalCustomFields()
        deleteAllDeletedDesigns()
        deleteAllDeletedCustomFields()
        deleteAllOrders()
        deleteAllDeletedOrders()

        // Orders arrive already merged, and the stock they account for arrived with the
        // designs. Deliberately NOT routed through placeOrder: that would deduct the same
        // pieces a second time, on every device, on every sync.
        insertOrders(data.orders.map { it.toEntity() })
        insertDeletedOrders(data.deletedOrders.map { DeletedOrderEntity(it.key, it.value) })

        // Insert designs and their inventories
        for (design in data.designs) {
            insertDesign(design.toEntity())
            insertInventories(design.sizeInventories.map { it.toEntity(design.id) })
        }

        // The profile is upserted, never cleared first: a payload from an older build
        // carries null here, and that must leave the existing details alone rather than
        // wiping them.
        data.profile?.let { upsertProfile(it.toEntity()) }

        // Insert globals
        val globalEntities = data.globalCustomFields.map { field ->
            val timestamp = data.globalCustomFieldsMetadata[field] ?: System.currentTimeMillis()
            GlobalCustomFieldEntity(field, timestamp)
        }
        insertGlobalCustomFields(globalEntities)

        // Insert tombstones
        val deletedEntities = data.deletedDesigns.entries.map { entry ->
            DeletedDesignEntity(entry.key, entry.value)
        }
        insertDeletedDesigns(deletedEntities)

        // Insert deleted custom fields tombstones
        val deletedFieldEntities = data.deletedCustomFields.entries.map { entry ->
            DeletedCustomFieldEntity(entry.key, entry.value)
        }
        insertDeletedCustomFields(deletedFieldEntities)
    }

    @Query("DELETE FROM deleted_designs WHERE designId = :designId")
    suspend fun deleteDeletedDesignTombstone(designId: String)

    @Query("SELECT imagePath FROM designs WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePaths(): List<String>

    @Transaction
    @Query("SELECT * FROM designs")
    suspend fun getAllDesignsOnce(): List<DesignWithInventories>

    @Query("SELECT * FROM deleted_designs")
    suspend fun getAllDeletedDesignsOnce(): List<DeletedDesignEntity>

    @Query("SELECT * FROM deleted_custom_fields")
    suspend fun getAllDeletedCustomFieldsOnce(): List<DeletedCustomFieldEntity>
    // #region Job work (v10): goods out with karigars

    @Query("SELECT * FROM job_work ORDER BY sentAt DESC")
    fun allJobWorkFlow(): Flow<List<JobWorkEntity>>

    @Query("SELECT * FROM job_work ORDER BY sentAt DESC")
    suspend fun allJobWorkOnce(): List<JobWorkEntity>

    @Query("SELECT * FROM job_work WHERE id = :id")
    suspend fun jobWorkById(id: String): JobWorkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobWork(job: JobWorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobWorks(jobs: List<JobWorkEntity>)

    @Query("DELETE FROM job_work WHERE id = :id")
    suspend fun deleteJobWork(id: String)

    @Query("SELECT * FROM deleted_job_work")
    fun deletedJobWorkFlow(): Flow<List<DeletedJobWorkEntity>>

    @Query("SELECT * FROM deleted_job_work")
    suspend fun deletedJobWorkOnce(): List<DeletedJobWorkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedJobWork(deleted: DeletedJobWorkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedJobWorks(deleted: List<DeletedJobWorkEntity>)

    /**
     * Send pieces out: the job is written and the shelf is reduced in one transaction.
     *
     * Together or not at all. A job written without the stock moving shows pieces in two
     * places at once; stock moved without a job shows them nowhere.
     */
    @Transaction
    suspend fun sendOut(job: JobWorkEntity, now: Long) {
        insertJobWork(job)
        takeFromLedger(job.designId, job.size, job.sentQuantity, now)
    }

    /** Pieces back on the shelf, and the job updated to say so. */
    @Transaction
    suspend fun receiveBack(job: JobWorkEntity, quantity: Int, now: Long) {
        insertJobWork(job)
        returnToLedger(job.designId, job.size, quantity, now)
    }
    // #endregion

}
