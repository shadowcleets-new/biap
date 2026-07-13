package com.example.biap.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.biap.data.Design
import com.example.biap.data.SizeInventory

@Entity(tableName = "designs")
data class DesignEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val imagePath: String?,
    val customFields: Map<String, String>,
    val karigars: List<String>,
    val updatedAt: Long
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
        sizeInventories = inventories.map { it.toDomain() },
        customFields = design.customFields,
        karigars = design.karigars,
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
        customFields = customFields,
        karigars = karigars,
        updatedAt = updatedAt
    )
}

fun SizeInventory.toEntity(designId: String): SizeInventoryEntity {
    return SizeInventoryEntity(
        designId = designId,
        size = size,
        stock = stock,
        price = price
    )
}
