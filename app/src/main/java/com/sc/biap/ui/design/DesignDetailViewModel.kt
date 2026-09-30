package com.sc.biap.ui.design

// #region Imports
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.Batch
import com.sc.biap.data.DataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.Karigar
import com.sc.biap.data.karigarsForWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// #endregion

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

    private val _message = MutableStateFlow<String?>(null)

    /** Something that needs saying - a rejected batch number, a failed write. */
    val message: StateFlow<String?> = _message.asStateFlow()

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

    // #region Batches

    /** This design's batches, grouped by size and in number order. */
    val batchesBySize: StateFlow<Map<String, List<Batch>>> = repository.biData
        .map { data ->
            data.batches
                .filter { it.designId == designId }
                .sortedBy { it.number }
                .groupBy { it.size }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    suspend fun suggestBatchNumber(size: String): Int = repository.suggestBatchNumber(designId, size)

    fun addBatch(size: String, number: Int, quantity: Int, price: Long, note: String) {
        viewModelScope.launch {
            _message.value = repository.addBatch(designId, size, number, quantity, price, note)
        }
    }

    fun resetSize(size: String) {
        viewModelScope.launch { _message.value = repository.resetSizeStock(designId, size) }
    }

    fun resetWholeDesign() {
        viewModelScope.launch { _message.value = repository.resetDesignStock(designId) }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** A name typed on a design joins the Hub, so the master list stays master. */
    fun addKarigarToHub(name: String) {
        viewModelScope.launch { repository.addKarigar(name) }
    }

    // #endregion

    // #region The Hub's karigars

    /** The master list, so the picker offers names rather than asking for spelling. */
    val hubKarigars: StateFlow<List<Karigar>> = repository.biData
        .map { data -> data.karigars.sortedBy { it.name.lowercase() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The karigars the Hub implies for a set of work fields, minus those already on.
     *
     * ADDITIVE ONLY. Changing a work value never removes anybody: somebody assigned by hand
     * is a deliberate act, and editing what the work says is not a statement that the person
     * who was doing it has stopped.
     */
    fun impliedKarigars(customFields: Map<String, String>, already: List<String>): List<String> {
        val implied = karigarsForWork(customFields, hubKarigars.value)
        return implied.filterNot { name -> already.any { it.equals(name, ignoreCase = true) } }
    }

    // #endregion

    /** Retire the design, or bring it back. The ordinary alternative to deleting it. */
    fun setArchived(archived: Boolean) {
        repository.setDesignArchived(designId, archived)
    }

    /** Destroy it for good. Offered only once a design is already archived. */
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
