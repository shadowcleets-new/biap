package com.sc.biap.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sc.biap.data.BusinessIntelligenceData
import com.sc.biap.data.Design
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
}
