package com.sc.biap.ui.design

// #region Imports
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.Batch
import com.sc.biap.data.attributeVocabulary
import com.sc.biap.data.AttributeValue
import com.sc.biap.data.suggestCut
import com.sc.biap.data.sizeDemand
import com.sc.biap.data.productionHistory
import com.sc.biap.data.designPace
import com.sc.biap.data.SizeDemand
import com.sc.biap.data.ProductionHistory
import com.sc.biap.data.DesignPace
import com.sc.biap.data.CutSuggestion
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

    /**
     * What the records already kept say about this design.
     *
     * Derived, never stored. Every number here comes out of batches and orders the owner
     * entered for other reasons, so nothing on this card costs them a keystroke.
     */
    val insights: StateFlow<DesignInsights> = repository.biData
        .map { data ->
            val design = data.designs.firstOrNull { it.id == designId }
                ?: return@map DesignInsights()
            val mine = data.batches.filter { it.designId == designId }
            val demand = sizeDemand(design, data.batches, data.orders)
            val history = productionHistory(mine)
            // Suggest a run the size of the last one, because that is the decision actually
            // being made: the same cut again, split better. Falling back to what is on the
            // shelf keeps it sensible for a design whose batches predate the ledger.
            val lastRunSize = mine
                .filter { history.lastMadeAt != null && it.createdAt >= history.lastMadeAt - 86_400_000L }
                .sumOf { it.quantity }
                .takeIf { it > 0 }
                ?: design.sizeInventories.sumOf { it.stock }
            DesignInsights(
                history = history,
                demand = demand,
                pace = designPace(design, data.batches, data.orders, System.currentTimeMillis()),
                suggestedCut = suggestCut(demand, lastRunSize),
                suggestedTotal = lastRunSize,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DesignInsights())

    /** What each attribute already holds across the catalogue, to pick instead of retype. */
    val vocabulary: StateFlow<Map<String, List<AttributeValue>>> = repository.biData
        .map { attributeVocabulary(it.designs) }
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

/**
 * Read-only advice drawn from the ledger. Empty is a valid and common state - a design made
 * once last week has nothing to say yet, and saying nothing is better than saying zero.
 */
data class DesignInsights(
    val history: ProductionHistory = ProductionHistory(),
    val demand: List<SizeDemand> = emptyList(),
    val pace: DesignPace = DesignPace(),
    val suggestedCut: List<CutSuggestion> = emptyList(),
    val suggestedTotal: Int = 0,
) {
    /** Nothing was ever cut and nothing ever sold: there is no history to read. */
    val isEmpty: Boolean get() = history.runs == 0 && pace.sold == 0
}
