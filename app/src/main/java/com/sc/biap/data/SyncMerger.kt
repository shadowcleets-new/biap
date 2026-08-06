package com.sc.biap.data

/**
 * Pure Last-Write-Wins merge of two dataset snapshots with deletion tombstones.
 * No I/O, no clocks — fully deterministic and unit-tested by SyncMergerTest.
 */
object SyncMerger {
    fun merge(localData: BusinessIntelligenceData, remoteData: BusinessIntelligenceData): BusinessIntelligenceData {
        // 1. Merge deleted-design tombstones (LWW on deletedAt)
        val localDeleted = localData.deletedDesigns.toMutableMap()
        localData.deletedDesignIds.forEach { id -> localDeleted.putIfAbsent(id, 0L) }
        val remoteDeleted = remoteData.deletedDesigns.toMutableMap()
        remoteData.deletedDesignIds.forEach { id -> remoteDeleted.putIfAbsent(id, 0L) }
        val mergedDeleted = (localDeleted.keys + remoteDeleted.keys).associateWith { id ->
            maxOf(localDeleted[id] ?: 0L, remoteDeleted[id] ?: 0L)
        }

        // 2. Merge active designs (LWW on updatedAt), drop those deleted later than their last edit
        val allDesigns = (localData.designs + remoteData.designs)
            .groupBy { it.id }
            .mapValues { (_, designsList) -> designsList.maxByOrNull { it.updatedAt }!! }
        val mergedDesigns = allDesigns.values.filter { design ->
            val deletedAt = mergedDeleted[design.id] ?: -1L
            design.updatedAt > deletedAt
        }
        // Prune tombstones for designs that were recreated/updated after deletion
        val finalDeleted = mergedDeleted.filter { (id, deletedAt) ->
            val design = allDesigns[id]
            design == null || deletedAt >= design.updatedAt
        }

        // 3. Merge global custom fields (LWW with tombstones)
        val mergedDeletedFields = (localData.deletedCustomFields.keys + remoteData.deletedCustomFields.keys)
            .associateWith { field ->
                maxOf(localData.deletedCustomFields[field] ?: 0L, remoteData.deletedCustomFields[field] ?: 0L)
            }
        val localFields = localData.globalCustomFieldsMetadata.toMutableMap()
        localData.globalCustomFields.forEach { field -> localFields.putIfAbsent(field, 0L) }
        val remoteFields = remoteData.globalCustomFieldsMetadata.toMutableMap()
        remoteData.globalCustomFields.forEach { field -> remoteFields.putIfAbsent(field, 0L) }
        val allFields = (localFields.keys + remoteFields.keys).associateWith { field ->
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

        return BusinessIntelligenceData(
            designs = mergedDesigns,
            globalCustomFields = mergedFields.keys,
            globalCustomFieldsMetadata = mergedFields,
            deletedDesignIds = finalDeleted.keys,
            deletedDesigns = finalDeleted,
            deletedCustomFields = finalDeletedFields
        )
    }
}
