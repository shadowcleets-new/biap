package com.example.biap.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface DataRepository {
    val biData: StateFlow<BusinessIntelligenceData>
    fun addOrUpdateDesign(design: Design)
    fun deleteDesign(designId: String)
    fun addGlobalCustomField(field: String)
    fun removeGlobalCustomField(field: String)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val file = File(context.filesDir, "bi_data.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _biData = MutableStateFlow(loadData())
    override val biData: StateFlow<BusinessIntelligenceData> = _biData.asStateFlow()

    private fun loadData(): BusinessIntelligenceData {
        val loaded = if (file.exists()) {
            try {
                val content = file.readText()
                json.decodeFromString<BusinessIntelligenceData>(content)
            } catch (e: Exception) {
                e.printStackTrace()
                BusinessIntelligenceData()
            }
        } else {
            BusinessIntelligenceData()
        }

        val defaultGlobals = setOf(
            "Top - Fabric", "Top - Work", "Top - Color",
            "Bottom - Fabric", "Bottom - Work", "Bottom - Color",
            "Dupatta - Fabric", "Dupatta - Work", "Dupatta - Color"
        )
        val currentGlobals = loaded.globalCustomFields
        val missingGlobals = defaultGlobals - currentGlobals
        return if (missingGlobals.isNotEmpty()) {
            val updated = loaded.copy(globalCustomFields = currentGlobals + defaultGlobals)
            saveData(updated)
            updated
        } else {
            loaded
        }
    }

    private fun saveData(data: BusinessIntelligenceData) {
        try {
            val content = json.encodeToString(data)
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun addOrUpdateDesign(design: Design) {
        _biData.update { current ->
            val updatedList = current.designs.toMutableList()
            val index = updatedList.indexOfFirst { it.id == design.id }
            if (index >= 0) {
                updatedList[index] = design
            } else {
                updatedList.add(design)
            }
            val newData = current.copy(designs = updatedList)
            saveData(newData)
            newData
        }
    }

    override fun deleteDesign(designId: String) {
        _biData.update { current ->
            val updatedList = current.designs.filterNot { it.id == designId }
            val newData = current.copy(designs = updatedList)
            saveData(newData)
            newData
        }
    }

    override fun addGlobalCustomField(field: String) {
        if (field.isBlank()) return
        _biData.update { current ->
            val updatedFields = current.globalCustomFields.toMutableSet()
            updatedFields.add(field.trim())
            val newData = current.copy(globalCustomFields = updatedFields)
            saveData(newData)
            newData
        }
    }

    override fun removeGlobalCustomField(field: String) {
        _biData.update { current ->
            val updatedFields = current.globalCustomFields.toMutableSet()
            updatedFields.remove(field)
            val newData = current.copy(globalCustomFields = updatedFields)
            saveData(newData)
            newData
        }
    }
}
