package com.example.biap.data

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
    val imagePath: String? = null,
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
