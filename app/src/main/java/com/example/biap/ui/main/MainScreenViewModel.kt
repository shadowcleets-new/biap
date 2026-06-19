package com.example.biap.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.biap.data.DataRepository
import com.example.biap.data.BusinessIntelligenceData
import com.example.biap.ui.main.MainScreenUiState.Success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  val uiState: StateFlow<MainScreenUiState> = combine(
    dataRepository.biData,
    _searchQuery
  ) { data, query ->
    val result: MainScreenUiState = if (query.isBlank()) {
      Success(data)
    } else {
      val filteredDesigns = data.designs.filter { design ->
        // 1. Search by design name or category (case-insensitive)
        val matchesBasic = design.name.contains(query, ignoreCase = true) ||
            design.category.contains(query, ignoreCase = true)

        // 2. Search by custom attributes key/value (case-insensitive)
        val matchesAttributes = design.customFields.any { (key, value) ->
          key.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)
        }

        // 3. Search by size inventory (e.g. searching "L" or "XL" matches designs that have stock > 0 in size L or XL)
        val matchesSize = design.sizeInventories.any { sizeInv ->
          sizeInv.stock > 0 && sizeInv.size.equals(query, ignoreCase = true)
        }

        matchesBasic || matchesAttributes || matchesSize
      }
      Success(data.copy(designs = filteredDesigns))
    }
    result
  }
    .catch { emit(MainScreenUiState.Error(it)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun deleteDesign(id: String) {
    dataRepository.deleteDesign(id)
  }

  val authManager get() = dataRepository.authManager
  val isSyncing: StateFlow<Boolean> = dataRepository.isSyncing

  fun syncWithGoogleDrive() {
    dataRepository.syncWithGoogleDrive()
  }

  fun signOut(onComplete: () -> Unit) {
    dataRepository.signOut(onComplete)
  }
}

sealed interface MainScreenUiState {
  object Loading : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(val data: BusinessIntelligenceData) : MainScreenUiState
}
