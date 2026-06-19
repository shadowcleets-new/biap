package com.example.biap.data

import kotlinx.serialization.Serializable

@Serializable
data class SizeInventory(
    val size: String,
    val stock: Int,
    val price: Double
)

@Serializable
data class Design(
    val id: String,
    val name: String,
    val category: String,
    val imagePath: String? = null,
    val sizeInventories: List<SizeInventory> = emptyList(),
    val customFields: Map<String, String> = emptyMap()
)

@Serializable
data class BusinessIntelligenceData(
    val designs: List<Design> = emptyList(),
    val globalCustomFields: Set<String> = emptySet()
)
