package com.sc.biap.data

import kotlinx.serialization.Serializable

@Serializable
data class SizeInventory(
    val size: String,
    val stock: Int,
    val price: Long
)

@Serializable
data class Design(
    val id: String,
    val name: String,
    val category: String,
    // Local cache path. Remains authoritative offline and for images not yet uploaded.
    val imagePath: String? = null,
    // Storage key of the CURRENT image version, e.g. "designs/<id>/1707_a3f9.jpg".
    // Versioned: replacing a photo writes a new key and repoints here, so a blob is
    // never overwritten in place.
    val imageRef: String? = null,
    // The image pointer carries its OWN timestamp so it merges independently of the
    // rest of the design - editing a price must not clobber a newer photo.
    val imageUpdatedAt: Long = 0L,
    val imageUpdatedBy: String? = null,
    val sizeInventories: List<SizeInventory> = emptyList(),
    val customFields: Map<String, String> = emptyMap(),
    val karigars: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class BusinessIntelligenceData(
    val designs: List<Design> = emptyList(),
    val globalCustomFields: Set<String> = emptySet(),
    val globalCustomFieldsMetadata: Map<String, Long> = emptyMap(),
    val deletedDesignIds: Set<String> = emptySet(),
    val deletedDesigns: Map<String, Long> = emptyMap(),
    val deletedCustomFields: Map<String, Long> = emptyMap()
)
