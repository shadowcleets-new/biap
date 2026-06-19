package com.example.biap.ui.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biap.data.DataRepository
import com.example.biap.data.Design
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DesignDetailUiState {
    object Loading : DesignDetailUiState
    data class Success(val design: Design) : DesignDetailUiState
    object NotFound : DesignDetailUiState
}

class DesignDetailViewModel(
    private val designId: String,
    private val repository: DataRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<DesignDetailUiState>(DesignDetailUiState.Loading)
    val uiState: StateFlow<DesignDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.biData.collect { biData ->
                val design = biData.designs.find { it.id == designId }
                if (design != null) {
                    _uiState.value = DesignDetailUiState.Success(design)
                } else {
                    _uiState.value = DesignDetailUiState.NotFound
                }
            }
        }
    }

    fun deleteDesign() {
        repository.deleteDesign(designId)
    }

    fun updateKarigars(karigars: List<String>) {
        val current = _uiState.value
        if (current is DesignDetailUiState.Success) {
            val updatedDesign = current.design.copy(karigars = karigars)
            repository.addOrUpdateDesign(updatedDesign)
        }
    }

    fun updateDesign(design: Design) {
        repository.addOrUpdateDesign(design)
    }
}
