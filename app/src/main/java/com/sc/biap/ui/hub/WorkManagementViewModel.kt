package com.sc.biap.ui.hub

// #region Imports
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.DataRepository
import com.sc.biap.data.Design
import com.sc.biap.data.suggestCut
import com.sc.biap.data.sizeDemand
import com.sc.biap.data.Karigar
import com.sc.biap.data.SampleOutcome
import com.sc.biap.data.WorkKind
import com.sc.biap.data.WorkLot
import com.sc.biap.data.openLots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// #endregion

/**
 * @file WorkManagementViewModel.kt
 * @description Everything that is with a karigar, as one list the whole workshop can read.
 *
 * ONE LIST, NOT THREE TABS:
 *   The question this screen is opened to answer is "what is out and what is late". Split
 *   across tabs, that question needs three visits and an overdue alteration can sit unseen
 *   behind a tab nobody opened. The filter is there to narrow when wanted; the default shows
 *   everything, worst first.
 */
class WorkManagementViewModel(private val repository: DataRepository) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Null means everything, which is the default and the common case. */
    private val _filter = MutableStateFlow<WorkKind?>(null)
    val filter: StateFlow<WorkKind?> = _filter.asStateFlow()

    val state: StateFlow<WorkUiState> = repository.biData
        .map { data ->
            val now = System.currentTimeMillis()
            val lots = openLots(data.jobWork, now)
            WorkUiState(
                lots = lots,
                overdue = lots.count { it.isOverdue(now) },
                countByKind = WorkKind.entries.associateWith { kind ->
                    lots.count { it.kind == kind }
                },
                designsById = data.designs.associateBy { it.id },
                karigars = data.karigars,
                activities = data.activities.keys.sorted(),
                // Judged separately from the open list: an approved sample is closed, so it
                // would vanish from a screen built only out of what is still outstanding.
                awaitingDecision = data.jobWork.filter {
                    it.kind == WorkKind.SAMPLE &&
                        it.outcome == SampleOutcome.PENDING &&
                        it.receivedQuantity > 0
                },
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUiState())

    /** The list as the screen shows it, after whatever the owner has narrowed it to. */
    val visibleLots: StateFlow<List<WorkLot>> = combine(state, _filter) { s, kind ->
        if (kind == null) s.lots else s.lots.filter { it.kind == kind }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(kind: WorkKind?) {
        _filter.value = kind
    }

    // #region Doing things

    fun startSample(name: String, karigar: String, activity: String, note: String, photo: String?) {
        viewModelScope.launch {
            _message.value = repository.startSample(name, karigar, activity, note, photo, null)
        }
    }

    fun startProduction(
        designId: String, karigar: String, activity: String,
        quantities: Map<String, Int>, batchNumber: Int, note: String,
    ) {
        viewModelScope.launch {
            _message.value = repository.startProduction(
                designId, karigar, activity, quantities, batchNumber, note,
            )
        }
    }

    fun startAlteration(
        designId: String, size: String, karigar: String, quantity: Int,
        note: String, photo: String?,
    ) {
        viewModelScope.launch {
            _message.value =
                repository.startAlteration(designId, size, karigar, quantity, note, photo, null)
        }
    }

    /**
     * What to make, per size, for a design about to be ordered.
     *
     * The size-curve advice stops being something to read and becomes the number already in
     * the box. The owner changes it or accepts it; either way they are not starting from
     * five of everything, which is the habit it exists to replace.
     */
    fun suggestedQuantities(designId: String, total: Int): Map<String, Int> {
        val data = repository.biData.value
        val design = data.designs.firstOrNull { it.id == designId } ?: return emptyMap()
        val demand = sizeDemand(design, data.batches, data.orders)
        val want = total.takeIf { it > 0 }
            ?: design.sizeInventories.size.times(5).coerceAtLeast(5)
        return suggestCut(demand, want).associate { it.size to it.pieces }
    }

    /** The next batch number for a design, so a production order carries one from the start. */
    suspend fun suggestBatchNumber(designId: String, size: String): Int =
        repository.suggestBatchNumber(designId, size)

    fun receive(jobId: String, quantity: Int) {
        viewModelScope.launch { _message.value = repository.receiveJobWork(jobId, quantity) }
    }

    /** Receive a whole lot in one go, which is how most of them actually arrive. */
    fun receiveWholeLot(lot: WorkLot) {
        viewModelScope.launch {
            for (row in lot.rows) {
                if (row.outstanding > 0) {
                    repository.receiveJobWork(row.id, row.outstanding)
                }
            }
        }
    }

    fun close(lot: WorkLot) {
        viewModelScope.launch {
            for (row in lot.rows) repository.closeJobWork(row.id)
        }
    }

    fun decideSample(jobId: String, approved: Boolean) {
        viewModelScope.launch { _message.value = repository.decideSample(jobId, approved) }
    }

    fun clearMessage() {
        _message.value = null
    }
    // #endregion
}

/**
 * [awaitingDecision] is separate from [lots] because a sample that has arrived is no longer
 * outstanding - it is sitting on the table waiting to be judged, which is a different kind of
 * "needs you" and would otherwise disappear from the screen entirely.
 */
data class WorkUiState(
    val lots: List<WorkLot> = emptyList(),
    val overdue: Int = 0,
    val countByKind: Map<WorkKind, Int> = emptyMap(),
    val designsById: Map<String, Design> = emptyMap(),
    val karigars: List<Karigar> = emptyList(),
    val activities: List<String> = emptyList(),
    val awaitingDecision: List<com.sc.biap.data.JobWork> = emptyList(),
) {
    val piecesOut: Int get() = lots.sumOf { it.outstanding }
}
