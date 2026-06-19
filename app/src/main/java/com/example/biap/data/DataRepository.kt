package com.example.biap.data

import android.content.Context
import com.example.biap.util.AuthManager
import com.example.biap.util.EncryptionUtils
import com.example.biap.util.GoogleDriveSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface DataRepository {
    val biData: StateFlow<BusinessIntelligenceData>
    fun addOrUpdateDesign(design: Design)
    fun deleteDesign(designId: String)
    fun addGlobalCustomField(field: String)
    fun removeGlobalCustomField(field: String)

    // Google Auth & Drive Sync
    val authManager: AuthManager
    val isSyncing: StateFlow<Boolean>
    fun syncWithGoogleDrive()
    fun signOut(onComplete: () -> Unit)
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val file = File(context.filesDir, "bi_data.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _biData = MutableStateFlow(loadData())
    override val biData: StateFlow<BusinessIntelligenceData> = _biData.asStateFlow()

    override val authManager = AuthManager(context)
    private val driveSyncManager = GoogleDriveSyncManager()
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    private var driveFileId: String? = null

    init {
        // Auto sync on startup if signed in
        if (authManager.currentUser.value != null) {
            syncWithGoogleDrive()
        }
    }

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

    private fun triggerBackgroundUpload(data: BusinessIntelligenceData) {
        repositoryScope.launch {
            val account = authManager.currentUser.value ?: return@launch
            val googleId = account.id ?: return@launch
            try {
                val token = authManager.getAccessToken() ?: return@launch
                var fileId = driveFileId
                if (fileId == null) {
                    fileId = driveSyncManager.searchFile(token)
                    driveFileId = fileId
                }
                val encrypted = EncryptionUtils.encrypt(json.encodeToString(data), googleId)
                val newFileId = driveSyncManager.uploadFile(token, encrypted, fileId)
                if (newFileId != null) {
                    driveFileId = newFileId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun syncWithGoogleDrive() {
        repositoryScope.launch {
            val account = authManager.currentUser.value ?: return@launch
            val googleId = account.id ?: return@launch
            _isSyncing.value = true
            try {
                val token = authManager.getAccessToken() ?: return@launch
                val fileId = driveSyncManager.searchFile(token)
                driveFileId = fileId
                if (fileId != null) {
                    // Download and decrypt
                    val encryptedContent = driveSyncManager.downloadFile(token, fileId)
                    if (!encryptedContent.isNullOrBlank()) {
                        val decryptedJson = EncryptionUtils.decrypt(encryptedContent, googleId)
                        val remoteData = json.decodeFromString<BusinessIntelligenceData>(decryptedJson)
                        
                        // Merge data by unique IDs
                        val localData = _biData.value
                        val mergedDesigns = (localData.designs + remoteData.designs)
                            .associateBy { it.id }
                            .values
                            .toList()
                        val mergedGlobals = localData.globalCustomFields + remoteData.globalCustomFields
                        val mergedData = BusinessIntelligenceData(mergedDesigns, mergedGlobals)
                        
                        saveData(mergedData)
                        _biData.value = mergedData
                        
                        // Upload merged back to drive
                        val reEncrypted = EncryptionUtils.encrypt(json.encodeToString(mergedData), googleId)
                        driveSyncManager.uploadFile(token, reEncrypted, fileId)
                    }
                } else {
                    // Upload local database to drive
                    val localData = _biData.value
                    val encrypted = EncryptionUtils.encrypt(json.encodeToString(localData), googleId)
                    val newFileId = driveSyncManager.uploadFile(token, encrypted, null)
                    driveFileId = newFileId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    override fun signOut(onComplete: () -> Unit) {
        authManager.signOut {
            driveFileId = null
            onComplete()
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
            triggerBackgroundUpload(newData)
            newData
        }
    }

    override fun deleteDesign(designId: String) {
        _biData.update { current ->
            val updatedList = current.designs.filterNot { it.id == designId }
            val newData = current.copy(designs = updatedList)
            saveData(newData)
            triggerBackgroundUpload(newData)
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
            triggerBackgroundUpload(newData)
            newData
        }
    }

    override fun removeGlobalCustomField(field: String) {
        _biData.update { current ->
            val updatedFields = current.globalCustomFields.toMutableSet()
            updatedFields.remove(field)
            val newData = current.copy(globalCustomFields = updatedFields)
            saveData(newData)
            triggerBackgroundUpload(newData)
            newData
        }
    }
}
