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

        // 6. Merge batches (LWW on updatedAt), keyed by id and dropped with their design.
        //
        // No tombstone table, because nothing deletes a batch on its own: emptying a size
        // sets `remaining` to 0 and keeps the row. A batch therefore only disappears when
        // its design does, which is why it is filtered against the design tombstones rather
        // than against any of its own.
        //
        // LWW on the whole row is right here in a way it is not for a design: a batch has no
        // independently-edited parts, and `remaining` moving is the only thing that normally
        // changes. The cost is that two devices selling from the same batch while offline
        // resolve to one of the two counts rather than the sum of both deductions - the same
        // limitation stock has always had, and the reason the app syncs rather than merging
        // arithmetic.
        val mergedBatches = (localData.batches + remoteData.batches)
            .groupBy { it.id }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.updatedAt }!! }
            .values
            .filter { batch ->
                val deletedAt = finalDeleted[batch.designId] ?: -1L
                deletedAt < 0L || batch.updatedAt > deletedAt
            }

        // 7. Merge karigars (LWW with tombstones), exactly as designs are merged.
        val mergedDeletedKarigars =
            (localData.deletedKarigars.keys + remoteData.deletedKarigars.keys).associateWith { id ->
                maxOf(localData.deletedKarigars[id] ?: 0L, remoteData.deletedKarigars[id] ?: 0L)
            }
        val allKarigars = (localData.karigars + remoteData.karigars)
            .groupBy { it.id }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.updatedAt }!! }
        val mergedKarigars = allKarigars.values.filter { karigar ->
            val deletedAt = mergedDeletedKarigars[karigar.id] ?: -1L
            karigar.updatedAt > deletedAt
        }
        val finalDeletedKarigars = mergedDeletedKarigars.filter { (id, deletedAt) ->
            val karigar = allKarigars[id]
            karigar == null || deletedAt >= karigar.updatedAt
        }

        // 8. Merge activities, which are names with a timestamp - the same shape as the
        // global custom fields above, and merged the same way.
        val mergedDeletedActivities =
            (localData.deletedActivities.keys + remoteData.deletedActivities.keys).associateWith { name ->
                maxOf(localData.deletedActivities[name] ?: 0L, remoteData.deletedActivities[name] ?: 0L)
            }
        val allActivities = (localData.activities.keys + remoteData.activities.keys).associateWith { name ->
            maxOf(localData.activities[name] ?: 0L, remoteData.activities[name] ?: 0L)
        }
        val mergedActivities = allActivities.filter { (name, updatedAt) ->
            val deletedAt = mergedDeletedActivities[name] ?: -1L
            updatedAt > deletedAt
        }
        val finalDeletedActivities = mergedDeletedActivities.filter { (name, deletedAt) ->
            val updatedAt = allActivities[name]
            updatedAt == null || deletedAt >= updatedAt
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
            profile = mergedProfile,
            batches = mergedBatches,
            karigars = mergedKarigars,
            deletedKarigars = finalDeletedKarigars,
            activities = mergedActivities,
            deletedActivities = finalDeletedActivities,
        )
    }
}
