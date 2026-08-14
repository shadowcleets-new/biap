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
        //
        // The image pointer is resolved SEPARATELY from the rest of the record. Whole-record
        // LWW would mean an edit to any field (a price, a karigar) drags that writer's older
        // imageRef along with it and silently discards a newer photo someone else uploaded.
        // Winning the record and winning the photo are two different questions.
        // The archive flag is resolved independently too, for exactly the same reason: a
        // device holding a stale archive state that edits a price would otherwise drag that
        // state along and silently un-archive - or worse, re-archive - the design.
        val allDesigns = (localData.designs + remoteData.designs)
            .groupBy { it.id }
            .mapValues { (_, designsList) ->
                val winner = designsList.maxByOrNull { it.updatedAt }!!
                val newestImage = designsList.maxByOrNull { it.imageUpdatedAt }!!
                val newestArchive = designsList.maxByOrNull { it.archiveUpdatedAt }!!

                var resolved = winner
                if (newestImage.imageUpdatedAt > winner.imageUpdatedAt) {
                    resolved = resolved.copy(
                        imageRef = newestImage.imageRef,
                        imageUpdatedAt = newestImage.imageUpdatedAt,
                        imageUpdatedBy = newestImage.imageUpdatedBy
                    )
                }
                if (newestArchive.archiveUpdatedAt > winner.archiveUpdatedAt) {
                    resolved = resolved.copy(
                        archivedAt = newestArchive.archivedAt,
                        archiveUpdatedAt = newestArchive.archiveUpdatedAt
                    )
                }
                // The extra photographs resolve on their own clock too, for the third time
                // and the same reason. Note what this does NOT do: merge the two lists. Two
                // devices that each added a photo keep only the later set, because a union
                // would silently reinstate photos somebody had deliberately removed - and
                // the cap of five means a union could also produce a design holding more
                // photographs than the app allows.
                val newestExtras = designsList.maxByOrNull { it.extraImagesUpdatedAt }!!
                if (newestExtras.extraImagesUpdatedAt > winner.extraImagesUpdatedAt) {
                    resolved = resolved.copy(
                        extraImages = newestExtras.extraImages,
                        extraImagesUpdatedAt = newestExtras.extraImagesUpdatedAt
                    )
                }
                resolved
            }
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

        // 4. Merge the owner's profile (LWW on its own updatedAt)
        //
        // Resolved independently of everything else, for the same reason the image pointer
        // is: editing a design on one device must never drag a stale copy of the profile
        // along with it. A null side loses to a real one rather than erasing it - an older
        // build that has never heard of profiles uploads null, and that must not wipe the
        // details entered on a newer device.
        val mergedProfile = listOfNotNull(localData.profile, remoteData.profile)
            .maxByOrNull { it.updatedAt }

        // 5. Merge orders (LWW with tombstones), exactly as designs are merged.
        //
        // Orders are written once and cancelled, never edited, so in practice this is a union
        // by id. The full LWW treatment is kept anyway because the cancellation case is real:
        // without the tombstone check, a device that still holds an order cancelled elsewhere
        // hands it back on its next sync, and the pieces it accounts for get counted twice.
        val mergedDeletedOrders = (localData.deletedOrders.keys + remoteData.deletedOrders.keys)
            .associateWith { id ->
                maxOf(localData.deletedOrders[id] ?: 0L, remoteData.deletedOrders[id] ?: 0L)
            }
        val allOrders = (localData.orders + remoteData.orders)
            .groupBy { it.id }
            .mapValues { (_, orders) -> orders.maxByOrNull { it.updatedAt }!! }
        val mergedOrders = allOrders.values.filter { order ->
            val deletedAt = mergedDeletedOrders[order.id] ?: -1L
            order.updatedAt > deletedAt
        }
        val finalDeletedOrders = mergedDeletedOrders.filter { (id, deletedAt) ->
            val order = allOrders[id]
            order == null || deletedAt >= order.updatedAt
        }

        return BusinessIntelligenceData(
            designs = mergedDesigns,
            globalCustomFields = mergedFields.keys,
            globalCustomFieldsMetadata = mergedFields,
            deletedDesignIds = finalDeleted.keys,
            deletedDesigns = finalDeleted,
            deletedCustomFields = finalDeletedFields,
            orders = mergedOrders,
            deletedOrders = finalDeletedOrders,
            profile = mergedProfile
        )
    }
}
