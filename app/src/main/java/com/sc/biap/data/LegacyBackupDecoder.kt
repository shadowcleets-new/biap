package com.sc.biap.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Decodes a Drive backup payload. Tries the current schema (v2: price in Long paise,
 * updatedAt present) first; on failure falls back to the legacy schema (v1: price as
 * rupee Double, optional fields absent). Returns null only for undecodable garbage.
 */
object LegacyBackupDecoder {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decode(rawJson: String): BusinessIntelligenceData? {
        try {
            return json.decodeFromString<BusinessIntelligenceData>(rawJson)
        } catch (e: Exception) {
            // Fall through to the legacy decoder below.
        }
        return try {
            decodeLegacy(rawJson)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeLegacy(rawJson: String): BusinessIntelligenceData {
        val root = json.parseToJsonElement(rawJson).jsonObject

        val designs = root["designs"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val sizeInventories = obj["sizeInventories"]?.jsonArray?.mapNotNull { invElement ->
                val inv = invElement.jsonObject
                val size = inv["size"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val stock = inv["stock"]?.jsonPrimitive?.intOrNull ?: 0
                // Legacy prices were rupee Doubles (e.g. 1500.5); convert to paise.
                val priceDouble = inv["price"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                SizeInventory(size = size, stock = stock, price = Math.round(priceDouble * 100))
            } ?: emptyList()
            Design(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "",
                imagePath = obj["imagePath"]?.jsonPrimitive?.contentOrNull,
                sizeInventories = sizeInventories,
                customFields = obj["customFields"]?.jsonObject?.mapNotNull { (k, v) ->
                    v.jsonPrimitive.contentOrNull?.let { k to it }
                }?.toMap() ?: emptyMap(),
                karigars = obj["karigars"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                } ?: emptyList(),
                // 0L means "oldest possible" so any local edit wins the LWW merge.
                updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        } ?: emptyList()

        return BusinessIntelligenceData(
            designs = designs,
            globalCustomFields = root["globalCustomFields"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            globalCustomFieldsMetadata = root["globalCustomFieldsMetadata"]?.jsonObject
                ?.mapNotNull { (k, v) -> v.jsonPrimitive.longOrNull?.let { k to it } }?.toMap() ?: emptyMap(),
            deletedDesignIds = root["deletedDesignIds"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            deletedDesigns = root["deletedDesigns"]?.jsonObject
                ?.mapNotNull { (k, v) -> v.jsonPrimitive.longOrNull?.let { k to it } }?.toMap() ?: emptyMap(),
            deletedCustomFields = root["deletedCustomFields"]?.jsonObject
                ?.mapNotNull { (k, v) -> v.jsonPrimitive.longOrNull?.let { k to it } }?.toMap() ?: emptyMap()
        )
    }
}
