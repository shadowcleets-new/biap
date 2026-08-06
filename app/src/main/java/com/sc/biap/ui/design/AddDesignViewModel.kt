package com.sc.biap.ui.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.DataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.SizeInventory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import com.sc.biap.util.SizeUtils


data class AddDesignUiState(
    val name: String = "",
    val category: String = AddDesignViewModel.DEFAULT_CATEGORY,
    val imagePath: String? = null,
    val sizes: List<SizeInventory> = emptyList(),
    val availableSizes: List<String> = SizeUtils.sortSizes(listOf("S", "M", "L", "XL", "XXL")),
    val customFields: Map<String, String> = emptyMap(),
    val globalCustomFields: Set<String> = emptySet(),
    val karigars: List<String> = emptyList(),
    val isSaved: Boolean = false
)

class AddDesignViewModel(private val repository: DataRepository) : ViewModel() {
    companion object {
        const val DEFAULT_CATEGORY = "Salwar Kameez"
    }

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

    fun updateSizeInventory(size: String, stock: Int, price: Long, enabled: Boolean, restoreFromCache: Boolean = false) {
        _uiState.update { current ->
            val updated = current.sizes.toMutableList()
            val existing = updated.find { it.size == size }
            updated.removeAll { it.size == size }
            if (enabled) {
                // Restore cached stock/price ONLY on an explicit chip re-enable — never while
                // the user is typing (BUG-18: clearing both fields to 0 must stay 0).
                val cached = sizeCache[size]
                val newItem = if (restoreFromCache && cached != null) cached else SizeInventory(size, stock, price)
                updated.add(newItem)
                if (newItem.stock > 0 || newItem.price > 0L) {
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

