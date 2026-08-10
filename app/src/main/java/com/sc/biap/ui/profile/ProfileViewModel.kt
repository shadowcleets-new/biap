package com.sc.biap.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.sc.biap.data.AccessLevel
import com.sc.biap.data.AccessLevelStore
import com.sc.biap.data.DataRepository
import com.sc.biap.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @file ProfileViewModel.kt
 * @description Edits the person's own details; reads, never writes, their access level.
 */

data class SignInMethod(val label: String, val value: String)

data class ProfileUiState(
    val draft: UserProfile = UserProfile(),
    val accessLevel: AccessLevel = AccessLevel.DEFAULT,
    /** False when the level is the fail-closed fallback rather than a value actually read. */
    val accessLevelConfirmed: Boolean = false,
    /** Every sign-in method linked to this one account. */
    val signInMethods: List<SignInMethod> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val savedAt: Long? = null,
)

class ProfileViewModel(private val repository: DataRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profile.collect { stored ->
                _uiState.update { current ->
                    // An edit in progress must survive a sync landing underneath it; only
                    // adopt stored values while the form is untouched.
                    if (current.isDirty) current
                    else current.copy(draft = seedFromAccount(stored))
                }
            }
        }

        viewModelScope.launch {
            val result = AccessLevelStore.current()
            _uiState.update {
                it.copy(
                    accessLevel = result.level,
                    accessLevelConfirmed = result.isAuthoritative,
                )
            }
        }

        _uiState.update { it.copy(signInMethods = readSignInMethods()) }
    }

    /**
     * Fill blanks from the Google account, without ever overwriting what the user typed.
     *
     * Only genuinely empty fields are filled: someone who corrected the name Google holds
     * must not have that correction undone every time this screen opens.
     */
    private fun seedFromAccount(stored: UserProfile): UserProfile {
        val user = FirebaseAuth.getInstance().currentUser ?: return stored
        val googleName = user.displayName.orEmpty().trim()

        val first = stored.firstName.ifBlank { googleName.substringBefore(' ', "").trim() }
        val last = stored.lastName.ifBlank {
            googleName.substringAfter(' ', "").trim()
        }
        // A single-word display name is a first name, not a surname.
        val resolvedFirst = first.ifBlank { googleName }

        return stored.copy(
            firstName = resolvedFirst,
            lastName = last,
            photoRef = stored.photoRef ?: user.photoUrl?.toString(),
        )
    }

    /** The linked methods, so the profile shows one identity rather than three logins. */
    private fun readSignInMethods(): List<SignInMethod> {
        val user = FirebaseAuth.getInstance().currentUser ?: return emptyList()
        return user.providerData
            .filter { it.providerId != "firebase" }
            .mapNotNull { info ->
                when (info.providerId) {
                    "google.com" -> SignInMethod("Google", info.email ?: user.email.orEmpty())
                    "phone" -> SignInMethod("Phone", info.phoneNumber ?: user.phoneNumber.orEmpty())
                    "password" -> SignInMethod("Email", info.email ?: user.email.orEmpty())
                    else -> null
                }
            }
            .filter { it.value.isNotBlank() }
    }

    fun onFirstNameChange(value: String) = edit { it.copy(firstName = value) }
    fun onLastNameChange(value: String) = edit { it.copy(lastName = value) }
    fun onDesignationChange(value: String) = edit { it.copy(designation = value) }
    fun onPhotoSelected(ref: String?) = edit { it.copy(photoRef = ref) }

    /** Digits only, and capped: an age is at most three characters and never negative. */
    fun onAgeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(3)
        edit { it.copy(age = digits) }
    }

    private fun edit(transform: (UserProfile) -> UserProfile) {
        _uiState.update { it.copy(draft = transform(it.draft), isDirty = true, savedAt = null) }
    }

    fun save() {
        val draft = _uiState.value.draft
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.saveProfile(draft)
            _uiState.update {
                it.copy(isSaving = false, isDirty = false, savedAt = System.currentTimeMillis())
            }
        }
    }
}
