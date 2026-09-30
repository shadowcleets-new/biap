package com.sc.biap.ui.hub

// #region Imports
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.DataRepository
import com.sc.biap.data.Karigar
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

/**
 * @file BusinessHubViewModel.kt
 * @description Who works here, and what each of them does.
 *
 * WHY THE BOARD IS DERIVED RATHER THAN STORED:
 *   The assignment lives on the karigar, as a list of activity names. The board below is
 *   built by inverting that - activity to the people doing it - rather than being a second
 *   copy that has to be kept in step. One direction is the truth; the other is a view of it.
 */
class BusinessHubViewModel(private val repository: DataRepository) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    /** Something the owner needs told - a duplicate name, a failed write. Cleared once shown. */
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<BusinessHubUiState> = repository.biData
        .map { data ->
            val karigars = data.karigars.sortedBy { it.name.lowercase() }
            val activities = data.activities.keys.sortedBy { name ->
                // Keep the seeded order the owner gave rather than going alphabetical, so
                // the board reads the way the workshop was described.
                com.sc.biap.data.Activities.DEFAULTS.indexOf(name).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            BusinessHubUiState(
                karigars = karigars,
                activities = activities,
                assignedBy = activities.associateWith { activity ->
                    karigars.filter { activity in it.activities }
                },
                unassigned = karigars.filter { it.activities.isEmpty() },
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusinessHubUiState())

    /** Drop a karigar onto an activity. Idempotent, so a second drop is not an error. */
    fun assign(karigarId: String, activity: String) {
        val karigar = state.value.karigars.firstOrNull { it.id == karigarId } ?: return
        if (activity in karigar.activities) return
        viewModelScope.launch {
            repository.setKarigarActivities(karigarId, karigar.activities + activity)
        }
    }

    fun unassign(karigarId: String, activity: String) {
        val karigar = state.value.karigars.firstOrNull { it.id == karigarId } ?: return
        viewModelScope.launch {
            repository.setKarigarActivities(karigarId, karigar.activities - activity)
        }
    }

    fun addKarigar(name: String) {
        viewModelScope.launch { _message.value = repository.addKarigar(name) }
    }

    fun renameKarigar(id: String, name: String) {
        viewModelScope.launch { _message.value = repository.renameKarigar(id, name) }
    }

    fun removeKarigar(id: String) {
        viewModelScope.launch { repository.deleteKarigar(id) }
    }

    fun addActivity(name: String) {
        viewModelScope.launch { _message.value = repository.addActivity(name) }
    }

    fun removeActivity(name: String) {
        viewModelScope.launch { repository.deleteActivity(name) }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/**
 * [assignedBy] is activity to the people doing it; [unassigned] is everyone doing nothing
 * yet, which is what the pool at the top of the board holds.
 */
data class BusinessHubUiState(
    val karigars: List<Karigar> = emptyList(),
    val activities: List<String> = emptyList(),
    val assignedBy: Map<String, List<Karigar>> = emptyMap(),
    val unassigned: List<Karigar> = emptyList(),
)
