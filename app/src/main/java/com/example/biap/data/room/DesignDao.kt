package com.example.biap.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.biap.data.BusinessIntelligenceData
import com.example.biap.data.Design
import com.example.biap.data.SizeInventory
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

    @Query("DELETE FROM designs WHERE id = :id")
    suspend fun deleteDesign(id: String)

    @Transaction
    suspend fun deleteDesignAndInventories(id: String) {
        deleteInventoriesForDesign(id)
        deleteDesign(id)
    }

    // Global custom fields
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
}
