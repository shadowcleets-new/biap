package com.sc.biap.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.sc.biap.data.Design
import com.sc.biap.data.DesignPhoto
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
