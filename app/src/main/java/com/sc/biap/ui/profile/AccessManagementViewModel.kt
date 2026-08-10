package com.sc.biap.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.AccessLevel
import com.sc.biap.data.AccessLevelStore
import com.sc.biap.data.OrgMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @file AccessManagementViewModel.kt
 * @description Lists the organisation and assigns levels, within what the actor may do.
 */
data class AccessManagementUiState(
    val myLevel: AccessLevel = AccessLevel.DEFAULT,
    val members: List<OrgMember> = emptyList(),
    val isLoading: Boolean = true,
    /** uid currently being written, so only that row shows a spinner. */
    val pendingUid: String? = null,
    val error: String? = null,
    val lastChange: String? = null,
)

class AccessManagementViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AccessManagementUiState())
    val uiState: StateFlow<AccessManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val level = AccessLevelStore.current()
            val members = if (level.level.canManageAccess) AccessLevelStore.members() else emptyList()
            _uiState.update {
                it.copy(
                    myLevel = level.level,
                    members = members,
                    isLoading = false,
                    // An empty list for a genuine manager means the read was refused or the
                    // rules are not published yet - worth saying, rather than showing an
                    // empty screen that looks like an organisation of one.
                    error = if (level.level.canManageAccess && members.isEmpty()) {
                        "No colleagues could be read. If this is unexpected, check that the " +
                            "Firestore rules are published."
                    } else {
                        null
                    }
                )
            }
        }
    }

    /** Levels this actor may move [member] to. Empty means the row is not theirs to touch. */
    fun assignableLevels(member: OrgMember): List<AccessLevel> {
        val actor = _uiState.value.myLevel
        if (member.isSelf) return emptyList()
        return AccessLevel.entries.filter { actor.canAssign(member.level, it) }
    }

    fun assign(member: OrgMember, newLevel: AccessLevel) {
        if (newLevel == member.level) return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingUid = member.uid, error = null, lastChange = null) }
            val error = AccessLevelStore.assignRole(_uiState.value.myLevel, member, newLevel)
            if (error != null) {
                _uiState.update { it.copy(pendingUid = null, error = error) }
                return@launch
            }
            // Re-read rather than patching in place: the server is the authority on what
            // actually landed, and a local edit that quietly diverged from it would be
            // worse than a moment's delay.
            val members = AccessLevelStore.members()
            _uiState.update {
                it.copy(
                    members = members,
                    pendingUid = null,
                    lastChange = "${member.displayName} is now ${newLevel.label}.",
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, lastChange = null) }
    }
}
