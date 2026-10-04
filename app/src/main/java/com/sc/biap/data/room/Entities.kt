package com.sc.biap.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.sc.biap.data.Batch
import com.sc.biap.data.JobWork
import com.sc.biap.data.SampleOutcome
import com.sc.biap.data.WorkKind
import com.sc.biap.data.Design
import com.sc.biap.data.DesignPhoto
import com.sc.biap.data.Karigar
import com.sc.biap.data.Order
import com.sc.biap.data.OrderLine
import com.sc.biap.data.SizeInventory
import com.sc.biap.data.UserProfile

@Entity(tableName = "designs")
data class DesignEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val imagePath: String?,
    val customFields: Map<String, String>,
    val karigars: List<String>,
    val updatedAt: Long,
    // Added in schema v4 - see AppDatabase.MIGRATION_3_4
    val imageRef: String? = null,
    val imageUpdatedAt: Long = 0L,
    val imageUpdatedBy: String? = null,
    // Added in schema v6 - see AppDatabase.MIGRATION_5_6
    val archivedAt: Long = 0L,
    val archiveUpdatedAt: Long = 0L,
    // Added in schema v8 - see AppDatabase.MIGRATION_7_8. Photos beyond the cover, which
    // stays in imagePath/imageRef above.
    val extraImages: List<DesignPhoto> = emptyList(),
    val extraImagesUpdatedAt: Long = 0L
)

@Entity(
    tableName = "size_inventories",
    foreignKeys = [
        ForeignKey(
            entity = DesignEntity::class,
            parentColumns = ["id"],
            childColumns = ["designId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["designId", "size"], unique = true)
    ]
)
data class SizeInventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val designId: String,
    val size: String,
    val stock: Int,
    val price: Long
)

/**
 * The signed-in person's own details. Exactly one row.
 *
 * The database is already per-identity (bi_database_<hash of uid>.db), so a single row IS
 * this user's profile; no uid column is needed and none is stored. The access level is
 * deliberately absent - it lives in Firestore, where the user cannot edit their own.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val firstName: String = "",
    val lastName: String = "",
    val age: String = "",
    val designation: String = "",
    val photoRef: String? = null,
    val updatedAt: Long = 0L,
) {
    companion object {
        /** Fixed key, so every write replaces the profile rather than appending another. */
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "global_custom_fields")
data class GlobalCustomFieldEntity(
    @PrimaryKey val field: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "deleted_custom_fields")
data class DeletedCustomFieldEntity(
    @PrimaryKey val field: String,
    val deletedAt: Long
)

@Entity(tableName = "deleted_designs")
data class DeletedDesignEntity(
    @PrimaryKey val designId: String,
    val deletedAt: Long
)

/** Added in schema v7 - see AppDatabase.MIGRATION_6_7. Lines are a JSON column; see Converters. */
@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val lines: List<OrderLine>,
    val updatedAt: Long
)

/**
 * Added in schema v9 - see AppDatabase.MIGRATION_8_9.
 *
 * Cascades with its design, like size_inventories: a batch of a design that no longer
 * exists is not history, it is a dangling row. There is deliberately NO tombstone table,
 * because nothing deletes a batch on its own - emptying a size sets `remaining` to 0 and
 * keeps the row, so there is no deletion for a sync to undo.
 */
@Entity(
    tableName = "batches",
    foreignKeys = [
        ForeignKey(
            entity = DesignEntity::class,
            parentColumns = ["id"],
            childColumns = ["designId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["designId", "size"]),
        Index(value = ["designId", "size", "number"], unique = true)
    ]
)
data class BatchEntity(
    @PrimaryKey val id: String,
    val designId: String,
    val size: String,
    val number: Int,
    val quantity: Int,
    val remaining: Int,
    val price: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val note: String = ""
)

/**
 * Added in schema v10. Pieces out with a karigar and what has come back.
 *
 * Indexed on designId because the design screen asks "what of mine is out" on every open,
 * and on karigar because the board asks the same question the other way round.
 */
@Entity(
    tableName = "job_work",
    indices = [Index("designId"), Index("karigar")],
)
data class JobWorkEntity(
    @PrimaryKey val id: String,
    val designId: String,
    val size: String,
    val karigar: String,
    val activity: String,
    val sentQuantity: Int,
    val receivedQuantity: Int,
    val sentAt: Long,
    val lastReceivedAt: Long,
    val closedAt: Long,
    val note: String = "",
    val updatedAt: Long,
    // Added in v11. Defaulted throughout so every row written by v10 reads back as what it
    // was then: a consignment of finished stock sent out for work.
    val kind: String = "OUTWORK",
    val lotId: String = "",
    val designName: String = "",
    val batchNumber: Int = 0,
    val outcome: String = "PENDING",
    val photoPath: String? = null,
    val photoRef: String? = null,
    val voicePath: String? = null,
    val voiceRef: String? = null,
)

/** Tombstone, so a job deleted on one device does not come back from another. */
@Entity(tableName = "deleted_job_work")
data class DeletedJobWorkEntity(
    @PrimaryKey val id: String,
    val deletedAt: Long,
)

/** Added in schema v9. The workshop's people; `activities` is a JSON list. */
@Entity(tableName = "karigars")
data class KarigarEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activities: List<String> = emptyList(),
    val updatedAt: Long = 0L
)

@Entity(tableName = "deleted_karigars")
data class DeletedKarigarEntity(
    @PrimaryKey val karigarId: String,
    val deletedAt: Long
)

/** Added in schema v9. Seeded with [com.sc.biap.data.Activities.DEFAULTS], then editable. */
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val name: String,
    val updatedAt: Long = 0L
)

@Entity(tableName = "deleted_activities")
data class DeletedActivityEntity(
    @PrimaryKey val name: String,
    val deletedAt: Long
)

@Entity(tableName = "deleted_orders")
data class DeletedOrderEntity(
    @PrimaryKey val orderId: String,
    val deletedAt: Long
)

data class DesignWithInventories(
    @Embedded val design: DesignEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "designId"
    )
    val inventories: List<SizeInventoryEntity>
)

// Mapping Extension Functions
fun DesignWithInventories.toDomain(): Design {
    return Design(
        id = design.id,
        name = design.name,
        category = design.category,
        imagePath = design.imagePath,
        imageRef = design.imageRef,
        imageUpdatedAt = design.imageUpdatedAt,
        imageUpdatedBy = design.imageUpdatedBy,
        sizeInventories = inventories.map { it.toDomain() },
        customFields = design.customFields,
        karigars = design.karigars,
        archivedAt = design.archivedAt,
        archiveUpdatedAt = design.archiveUpdatedAt,
        extraImages = design.extraImages,
        extraImagesUpdatedAt = design.extraImagesUpdatedAt,
        updatedAt = design.updatedAt
    )
}

fun SizeInventoryEntity.toDomain(): SizeInventory {
    return SizeInventory(
        size = size,
        stock = stock,
        price = price
    )
}

fun Design.toEntity(): DesignEntity {
    return DesignEntity(
        id = id,
        name = name,
        category = category,
        imagePath = imagePath,
        imageRef = imageRef,
        imageUpdatedAt = imageUpdatedAt,
        imageUpdatedBy = imageUpdatedBy,
        customFields = customFields,
        karigars = karigars,
        archivedAt = archivedAt,
        archiveUpdatedAt = archiveUpdatedAt,
        extraImages = extraImages,
        extraImagesUpdatedAt = extraImagesUpdatedAt,
        updatedAt = updatedAt
    )
}

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    firstName = firstName,
    lastName = lastName,
    age = age,
    designation = designation,
    photoRef = photoRef,
    updatedAt = updatedAt,
)

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    firstName = firstName,
    lastName = lastName,
    age = age,
    designation = designation,
    photoRef = photoRef,
    updatedAt = updatedAt,
)

fun OrderEntity.toDomain(): Order = Order(
    id = id,
    createdAt = createdAt,
    lines = lines,
    updatedAt = updatedAt,
)

fun Order.toEntity(): OrderEntity = OrderEntity(
    id = id,
    createdAt = createdAt,
    lines = lines,
    updatedAt = updatedAt,
)

fun SizeInventory.toEntity(designId: String): SizeInventoryEntity {
    return SizeInventoryEntity(
        designId = designId,
        size = size,
        stock = stock,
        price = price
    )
}

fun JobWorkEntity.toDomain(): JobWork = JobWork(
    id = id,
    // An unrecognised kind reads as OUTWORK rather than throwing: a row written by a newer
    // build on another device must not crash this one on sync.
    kind = runCatching { WorkKind.valueOf(kind) }.getOrDefault(WorkKind.OUTWORK),
    lotId = lotId.ifBlank { id },
    designId = designId, designName = designName, size = size, karigar = karigar,
    activity = activity, sentQuantity = sentQuantity, receivedQuantity = receivedQuantity,
    batchNumber = batchNumber, sentAt = sentAt, lastReceivedAt = lastReceivedAt,
    closedAt = closedAt,
    outcome = runCatching { SampleOutcome.valueOf(outcome) }.getOrDefault(SampleOutcome.PENDING),
    photoPath = photoPath, photoRef = photoRef, voicePath = voicePath, voiceRef = voiceRef,
    note = note, updatedAt = updatedAt,
)

fun JobWork.toEntity(): JobWorkEntity = JobWorkEntity(
    id = id, designId = designId, size = size, karigar = karigar, activity = activity,
    sentQuantity = sentQuantity, receivedQuantity = receivedQuantity, sentAt = sentAt,
    lastReceivedAt = lastReceivedAt, closedAt = closedAt, note = note, updatedAt = updatedAt,
    kind = kind.name, lotId = lotId, designName = designName, batchNumber = batchNumber,
    outcome = outcome.name, photoPath = photoPath, photoRef = photoRef,
    voicePath = voicePath, voiceRef = voiceRef,
)

fun BatchEntity.toDomain(): Batch = Batch(
    id = id,
    designId = designId,
    size = size,
    number = number,
    quantity = quantity,
    remaining = remaining,
    price = price,
    createdAt = createdAt,
    updatedAt = updatedAt,
    note = note,
)

fun Batch.toEntity(): BatchEntity = BatchEntity(
    id = id,
    designId = designId,
    size = size,
    number = number,
    quantity = quantity,
    remaining = remaining,
    price = price,
    createdAt = createdAt,
    updatedAt = updatedAt,
    note = note,
)

fun KarigarEntity.toDomain(): Karigar = Karigar(
    id = id,
    name = name,
    activities = activities,
    updatedAt = updatedAt,
)

fun Karigar.toEntity(): KarigarEntity = KarigarEntity(
    id = id,
    name = name,
    activities = activities,
    updatedAt = updatedAt,
)
