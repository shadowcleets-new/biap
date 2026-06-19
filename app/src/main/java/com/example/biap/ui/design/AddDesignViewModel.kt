package com.example.biap.ui.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biap.data.DataRepository
import com.example.biap.data.Design
import com.example.biap.data.SizeInventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AddDesignUiState(
    val name: String = "",
    val category: String = "Salwar Kameez",
    val imagePath: String? = null,
    val sizes: List<SizeInventory> = emptyList(),
    val customFields: Map<String, String> = emptyMap(),
    val globalCustomFields: Set<String> = emptySet(),
    val isSaved: Boolean = false
)

class AddDesignViewModel(private val repository: DataRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddDesignUiState())
    val uiState: StateFlow<AddDesignUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.biData.collect { biData ->
                _uiState.update { current ->
                    val updatedFields = current.customFields.toMutableMap()
                    biData.globalCustomFields.forEach { key ->
                        if (!updatedFields.containsKey(key)) {
                            updatedFields[key] = ""
                        }
                    }
                    current.copy(
                        globalCustomFields = biData.globalCustomFields,
                        customFields = updatedFields
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onCategoryChange(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onImageSelected(path: String?) {
        _uiState.update { it.copy(imagePath = path) }
    }

    fun updateSizeInventory(size: String, stock: Int, price: Double, enabled: Boolean) {
        _uiState.update { current ->
            val updated = current.sizes.toMutableList()
            updated.removeAll { it.size == size }
            if (enabled) {
                updated.add(SizeInventory(size, stock, price))
            }
            current.copy(sizes = updated)
        }
    }

    fun updateCustomFieldValue(key: String, value: String) {
        _uiState.update { current ->
            val updated = current.customFields.toMutableMap()
            updated[key] = value
            current.copy(customFields = updated)
        }
    }

    fun addCustomField(key: String, makeGlobal: Boolean) {
        if (key.isBlank()) return
        val trimmed = key.trim()
        _uiState.update { current ->
            val updated = current.customFields.toMutableMap()
            if (!updated.containsKey(trimmed)) {
                updated[trimmed] = ""
            }
            current.copy(customFields = updated)
        }
        if (makeGlobal) {
            repository.addGlobalCustomField(trimmed)
        }
    }

    fun removeCustomField(key: String) {
        _uiState.update { current ->
            val updated = current.customFields.toMutableMap()
            updated.remove(key)
            current.copy(customFields = updated)
        }
    }

    fun saveDesign() {
        val state = _uiState.value
        if (state.name.isBlank()) return

        val newDesign = Design(
            id = UUID.randomUUID().toString(),
            name = state.name.trim(),
            category = state.category.trim(),
            imagePath = state.imagePath,
            sizeInventories = state.sizes,
            customFields = state.customFields.filterKeys { it.isNotBlank() }
        )

        repository.addOrUpdateDesign(newDesign)
        _uiState.update { it.copy(isSaved = true) }
    }
}
