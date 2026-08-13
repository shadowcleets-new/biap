package com.sc.biap.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sc.biap.data.AccessLevel
import com.sc.biap.data.AccessLevelStore
import com.sc.biap.data.InviteStore
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
    val invites: List<InviteStore.Invite> = emptyList(),
    val isLoading: Boolean = true,
    /** uid currently being written, so only that row shows a spinner. */
    val pendingUid: String? = null,
    val isInviting: Boolean = false,
    /**
     * Set once an invite is stored and the SMS is ready to hand to the messaging app.
     *
     * Carried in state rather than sent from here because the ViewModel has no Activity, and
     * because the message must never leave without the human pressing send.
     */
    val smsToSend: PendingSms? = null,
    val error: String? = null,
    val lastChange: String? = null,
)

data class PendingSms(val phone: String, val body: String)

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
            val invites = if (level.level.canManageAccess) InviteStore.pending() else emptyList()
            _uiState.update {
                it.copy(
                    myLevel = level.level,
                    members = members,
                    invites = invites,
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

    /** Levels this actor may invite someone in at. Someone with no account starts at Viewer. */
    fun invitableLevels(): List<AccessLevel> {
        val actor = _uiState.value.myLevel
        return AccessLevel.entries.filter {
            it != AccessLevel.VIEWER && actor.canAssign(AccessLevel.VIEWER, it)
        }
    }

    /**
     * Store an invite, then hand the SMS to the screen to open in the messaging app.
     *
     * The record is written FIRST. If it were the other way round, a message could go out
     * promising access that was never granted - and the recipient would arrive to a Viewer
     * account with no explanation.
     */
    fun invite(rawPhone: String, level: AccessLevel) {
        val phone = InviteStore.normalise(rawPhone)
        if (phone == null) {
            _uiState.update { it.copy(error = "That does not look like a phone number.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isInviting = true, error = null, lastChange = null) }
            val error = InviteStore.invite(_uiState.value.myLevel, phone, level)
            if (error != null) {
                _uiState.update { it.copy(isInviting = false, error = error) }
                return@launch
            }
            val invites = InviteStore.pending()
            val stored = invites.firstOrNull { it.phone == phone }
                ?: InviteStore.Invite(phone, level, "")
            _uiState.update {
                it.copy(
                    invites = invites,
                    isInviting = false,
                    smsToSend = PendingSms(phone, InviteStore.messageFor(stored)),
                    lastChange = "Invited $phone as ${level.label}.",
                )
            }
        }
    }

    fun revokeInvite(invite: InviteStore.Invite) {
        viewModelScope.launch {
            val error = InviteStore.revoke(invite.phone)
            if (error != null) {
                _uiState.update { it.copy(error = error) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    invites = it.invites.filterNot { existing -> existing.phone == invite.phone },
                    lastChange = "Invite for ${invite.phone} withdrawn.",
                )
            }
        }
    }

    /** Called once the messaging app has been opened, so it is not opened again on recompose. */
    fun smsHandedOff() {
        _uiState.update { it.copy(smsToSend = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, lastChange = null) }
    }
}
