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
import com.example.biap.util.SizeUtils


data class AddDesignUiState(
    val name: String = "",
    val category: String = "Salwar Kameez",
    val imagePath: String? = null,
    val sizes: List<SizeInventory> = emptyList(),
    val availableSizes: List<String> = SizeUtils.sortSizes(listOf("S", "M", "L", "XL", "XXL")),
    val customFields: Map<String, String> = emptyMap(),
    val globalCustomFields: Set<String> = emptySet(),
    val karigars: List<String> = emptyList(),
    val isSaved: Boolean = false
)

class AddDesignViewModel(private val repository: DataRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddDesignUiState())
    val uiState: StateFlow<AddDesignUiState> = _uiState.asStateFlow()
    private val sizeCache = mutableMapOf<String, SizeInventory>()

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

    fun updateSizeInventory(size: String, stock: Int, price: Long, enabled: Boolean) {
        _uiState.update { current ->
            val updated = current.sizes.toMutableList()
            val existing = updated.find { it.size == size }
            updated.removeAll { it.size == size }
            if (enabled) {
                val cached = sizeCache[size]
                val finalStock = if (stock == 0 && price == 0L && cached != null) cached.stock else stock
                val finalPrice = if (stock == 0 && price == 0L && cached != null) cached.price else price
                val newItem = SizeInventory(size, finalStock, finalPrice)
                updated.add(newItem)
                if (finalStock > 0 || finalPrice > 0L) {
                    sizeCache[size] = newItem
                }
            } else {
                existing?.let { sizeCache[size] = it }
            }
            current.copy(sizes = updated)
        }
    }
    fun addAvailableSize(size: String) {
        if (size.isBlank()) return
        val trimmed = size.trim().uppercase()
        _uiState.update { current ->
            if (current.availableSizes.contains(trimmed)) {
                current
            } else {
                current.copy(availableSizes = SizeUtils.sortSizes(current.availableSizes + trimmed))
            }
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
            customFields = state.customFields.filterKeys { it.isNotBlank() },
            karigars = state.karigars
        )

        repository.addOrUpdateDesign(newDesign)
        _uiState.update { it.copy(isSaved = true) }
    }

    fun addKarigar(name: String) {
        if (name.isBlank()) return
        val trimmed = name.trim()
        _uiState.update { current ->
            if (current.karigars.contains(trimmed)) {
                current
            } else {
                current.copy(karigars = current.karigars + trimmed)
            }
        }
    }

    fun removeKarigar(name: String) {
        _uiState.update { current ->
            current.copy(karigars = current.karigars - name)
        }
    }
}

