# SessionManager Integration Guide

## Overview

`SessionManager` is a unified, production-ready session management system that orchestrates:

1. **App Launch State Restoration** — Verify persisted session against Firebase auth on app startup
2. **Firebase Authentication Verification** — Detect auth state changes, account switching, session corruption
3. **DataStore Persistence** — Type-safe, reactive session preferences with offline support
4. **Organization Context** — Save, load, and clear user organization selection
5. **Reactive UI Updates** — Flows for observing session changes (user ID, org selection, auth status)

---

## Architecture & Design Patterns

### Defensive Session Validation

SessionManager implements a **defensive validation matrix** to prevent invalid session states:

```
┌─────────────────────────────────────────────────────────────┐
│ checkAndRestoreSession() Flow                               │
├─────────────────────────────────────────────────────────────┤
│ 1. Read persisted SessionPreferences from DataStore         │
│ 2. Verify Firebase.currentUser exists                       │
│ 3. Validate Firebase UID matches persisted UID              │
│ 4. Update last login timestamp                              │
│ 5. Return SessionCheckResult with validity flag             │
│                                                              │
│ Security Measures:                                           │
│ • Clear stale sessions if Firebase user logged out          │
│ • Clear session on UID mismatch (possible account switch)   │
│ • Never leave partial/inconsistent state                    │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow Layers

```
┌──────────────────────────┐
│   Firebase Auth (Live)   │
│   currentUser, UID       │
└──────────────┬───────────┘
               │ verify consistency
               ↓
┌──────────────────────────┐
│  SessionManager (Logic)  │  ← Main orchestrator
│  Validation, restoration │
└──────────────┬───────────┘
               │ read/write
               ↓
┌──────────────────────────┐
│ DataStore (Persisted)    │
│ SessionPreferences       │
└──────────────────────────┘
```

### Dual Access Patterns

SessionManager provides **both suspend and Flow-based access** for different use cases:

| Method | Pattern | Use Case |
|--------|---------|----------|
| `checkAndRestoreSession()` | Suspend | App launch, one-time checks |
| `saveSelectedOrganization()` | Suspend | Navigation actions, user interactions |
| `getCurrentSession()` | Suspend | Immediate data needs in coroutine context |
| `sessionStatusFlow` | Flow | ViewModel observation, reactive UI updates |
| `isAuthenticatedFlow` | Flow | Navigation guards, auth status display |
| `isOrganizationSelectedFlow` | Flow | Conditional UI rendering, feature gates |

---

## Integration Steps

### Step 1: MainActivity — Session Restoration on App Launch

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        Firebase.initialize(this)
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        enableEdgeToEdge()
        setContent {
            val sessionManager = SessionManager.getInstance(this@MainActivity)
            
            // Check and restore session (must be inside Composable for lifecycle)
            val navController = rememberNavController()
            
            LaunchedEffect(Unit) {
                val checkResult = sessionManager.checkAndRestoreSession()
                
                val route = when {
                    checkResult.isSessionValid && checkResult.selectedOrgId.isNotEmpty() 
                        -> NavigationRoute.HOME
                    checkResult.isSessionValid 
                        -> NavigationRoute.ORG_PICKER
                    else 
                        -> NavigationRoute.AUTH
                }
                
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }

            BiapTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainNavigation(navController, sessionManager)
                }
            }
        }
    }
}
```

### Step 2: Navigation Guards — Protect Routes with Auth Status

```kotlin
// navigation/NavigationGuard.kt

class NavigationGuard(
    private val sessionManager: SessionManager
) {
    suspend fun canAccessRoute(route: NavigationRoute): Boolean {
        return when (route) {
            NavigationRoute.HOME -> {
                // Require both authentication AND organization selection
                val session = sessionManager.getCurrentSession()
                session.currentUserId.isNotEmpty() && 
                    session.selectedOrganizationId.isNotEmpty()
            }
            
            NavigationRoute.ORG_PICKER -> {
                // Require authentication but not org selection
                sessionManager.isFirebaseUserAuthenticated()
            }
            
            NavigationRoute.AUTH -> true // Always accessible
            
            else -> false
        }
    }
}
```

### Step 3: ViewModels — Reactive Session Observation

```kotlin
// ui/auth/viewmodel/AuthViewModel.kt

class AuthViewModel(
    context: Context,
    private val sessionManager: SessionManager = SessionManager.getInstance(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Observe authentication status changes
        viewModelScope.launch {
            sessionManager.isAuthenticatedFlow.collect { isAuthenticated ->
                _uiState.update { it.copy(isAuthenticated = isAuthenticated) }
            }
        }

        // Observe organization selection status
        viewModelScope.launch {
            sessionManager.isOrganizationSelectedFlow.collect { isOrgSelected ->
                _uiState.update { it.copy(organizationSelected = isOrgSelected) }
            }
        }

        // Observe current user ID for logging/analytics
        viewModelScope.launch {
            sessionManager.currentUserIdFlow.collect { userId ->
                _uiState.update { it.copy(currentUserId = userId) }
            }
        }
    }

    fun onAuthSuccess(userId: String) {
        viewModelScope.launch {
            sessionManager.saveCurrentUser(userId)
        }
    }
}
```

### Step 4: Organization Switching

```kotlin
// ui/org/viewmodel/OrganizationViewModel.kt

class OrganizationViewModel(
    context: Context,
    private val sessionManager: SessionManager = SessionManager.getInstance(context)
) : ViewModel() {

    suspend fun switchOrganization(orgId: String, orgName: String) {
        val result = sessionManager.saveSelectedOrganization(orgId, orgName)
        
        result.onSuccess {
            // Trigger any data sync needed for new org context
            // syncManager.syncForOrganization(orgId)
            
            // Navigation will happen automatically via isOrganizationSelectedFlow
        }
        
        result.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message) }
        }
    }
}
```

### Step 5: Sign Out Flow

```kotlin
// ui/settings/SettingsViewModel.kt

class SettingsViewModel(
    context: Context,
    private val sessionManager: SessionManager = SessionManager.getInstance(context)
) : ViewModel() {

    fun handleSignOut() {
        viewModelScope.launch {
            val result = sessionManager.signOut()
            
            result.onSuccess {
                // isAuthenticatedFlow will emit false, triggering navigation
                // to auth screen via navigation guard or automatic routing
            }
            
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = "Sign out failed: ${error.message}") }
            }
        }
    }
}
```

---

## Core Method Reference

### Session Restoration & Validation

```kotlin
// PRIMARY ENTRY POINT: App launch
suspend fun checkAndRestoreSession(): SessionCheckResult
```

Returns `SessionCheckResult`:
```kotlin
data class SessionCheckResult(
    val isSessionValid: Boolean,           // Session is valid and usable
    val currentUserId: String = "",        // Firebase UID
    val selectedOrgId: String = "",        // Organization ID (if selected)
    val selectedOrgName: String = "",      // Organization name (if selected)
    val lastLoginTimestamp: Long = 0L,     // Last successful auth timestamp
    val reasonInvalid: String? = null      // Detailed failure reason
)
```

### Firebase Verification (Fast, No I/O)

```kotlin
// Quick snapshot checks
fun getCurrentFirebaseUser(): FirebaseUser?
fun getCurrentFirebaseUid(): String
fun isFirebaseUserAuthenticated(): Boolean
fun getFirebaseUserEmail(): String
fun getFirebaseUserPhoneNumber(): String?
fun getFirebaseUserDisplayName(): String?

// Verify Firebase user is still authenticated
fun verifyFirebaseUser(): FirebaseUser?

// Detect account switching or session corruption
suspend fun verifyUidConsistency(): Boolean
```

### Organization Management (Suspend)

```kotlin
// Save organization context
suspend fun saveSelectedOrganization(
    organizationId: String,
    organizationName: String
): Result<Unit>

// Load organization context
suspend fun loadSelectedOrganizationId(): String
suspend fun loadSelectedOrganizationName(): String
suspend fun loadSelectedOrganization(): Pair<String, String>

// Clear organization context (keep user authenticated)
suspend fun clearOrganizationContext(): Result<Unit>
```

### Session Lifecycle (Suspend)

```kotlin
// Get current session snapshot
suspend fun getCurrentSession(): SessionPreferences

// Get specific session fields
suspend fun getCurrentUserId(): String
suspend fun getLastLoginTimestamp(): Long

// Clear all session data
suspend fun clearSession(): Result<Unit>

// Complete sign out (DataStore + Firebase)
suspend fun signOut(): Result<Unit>
```

### Reactive Flows

```kotlin
// Complete session changes
val sessionStatusFlow: Flow<SessionPreferences>

// Individual field changes
val currentUserIdFlow: Flow<String>
val selectedOrganizationIdFlow: Flow<String>
val selectedOrganizationNameFlow: Flow<String>
val lastLoginTimestampFlow: Flow<Long>

// Derived boolean flows
val isAuthenticatedFlow: Flow<Boolean>  // Firebase auth + DataStore consistency
val isOrganizationSelectedFlow: Flow<Boolean>  // Both ID and name non-empty
```

---

## Error Handling Patterns

### Pattern 1: Silent Failure with Logging

```kotlin
suspend fun attemptOperation() {
    val result = sessionManager.loadSelectedOrganizationId()
    if (result.isEmpty()) {
        Log.w("MyViewModel", "No organization selected")
        // Fall back to default behavior
    }
}
```

### Pattern 2: Explicit Error Propagation

```kotlin
suspend fun saveOrgWithErrorHandling(orgId: String, orgName: String) {
    val result = sessionManager.saveSelectedOrganization(orgId, orgName)
    
    result.onSuccess {
        Log.i("MyViewModel", "Organization saved: $orgName")
    }
    
    result.onFailure { error ->
        Log.e("MyViewModel", "Failed to save organization", error)
        _uiState.update { it.copy(errorMessage = error.message) }
    }
}
```

### Pattern 3: Verify Before Sensitive Operations

```kotlin
suspend fun executeSensitiveOperation() {
    // Verify Firebase user is still authenticated
    val firebaseUser = sessionManager.verifyFirebaseUser()
    if (firebaseUser == null) {
        Log.e("MyViewModel", "User logged out during operation")
        // Navigate to auth screen
        return
    }

    // Verify UID consistency (no account switching)
    val isConsistent = sessionManager.verifyUidConsistency()
    if (!isConsistent) {
        Log.e("MyViewModel", "Session UID mismatch - clearing session")
        sessionManager.clearSession()
        // Navigate to auth screen
        return
    }

    // Safe to proceed
    performOperation()
}
```

---

## Testing Guidelines

### Unit Tests (MockK)

```kotlin
@Test
fun testCheckAndRestoreSession_ValidSession() = runBlocking {
    // Arrange
    every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
    coEvery { mockSessionDataStore.readCurrentSession() } returns sessionPrefs

    // Act
    val result = sessionManager.checkAndRestoreSession()

    // Assert
    assertTrue(result.isSessionValid)
    coVerify { mockSessionDataStore.updateLastLoginTimestamp() }
}
```

### Integration Tests (Real DataStore)

```kotlin
@Test
fun testEndToEndSessionFlow() = runBlocking {
    // Use real SessionDataStore with test database
    val sessionManager = SessionManager(context)
    
    // Simulate user login
    sessionManager.saveCurrentUser("testUser")
    sessionManager.saveSelectedOrganization("testOrg", "Test Organization")
    
    // Verify session restoration
    val session = sessionManager.getCurrentSession()
    assertEquals("testUser", session.currentUserId)
    assertEquals("testOrg", session.selectedOrganizationId)
}
```

---

## Best Practices

### ✓ DO

- **Use `checkAndRestoreSession()` once at app launch** to determine initial navigation destination
- **Observe Flows in ViewModels** for reactive UI updates when session changes
- **Call `verifyUidConsistency()` before sensitive operations** (payment, export, deletion)
- **Use `Result<T>` pattern** for operations that can fail (org save, logout)
- **Clear organization context separately** if user should remain authenticated after leaving org
- **Log detailed failure reasons** from `SessionCheckResult.reasonInvalid` for debugging

### ✗ DON'T

- **Don't call `checkAndRestoreSession()` repeatedly** — run once at app launch only
- **Don't ignore `reasonInvalid` in SessionCheckResult** — log it for debugging production issues
- **Don't mix suspend calls and Firebase direct calls** — always go through SessionManager
- **Don't trust persisted session without Firebase verification** — SessionManager always cross-checks
- **Don't save partial organization data** (ID without name or vice versa)
- **Don't clear full session when leaving org** — use `clearOrganizationContext()` instead

---

## Troubleshooting

### Issue: Session restored but Firebase user is null

**Cause**: DataStore has stale session, Firebase user has logged out in another session/device.

**Solution**: `checkAndRestoreSession()` automatically clears stale session. User will be sent to auth screen.

### Issue: UID mismatch on every app launch

**Cause**: Firebase UID is changing (rare) or session data is corrupting.

**Solution**: Enable Firestore logging: `FirebaseAuth.getInstance().enableLogging = true`. Check for account switching.

### Issue: Organization selection not persisting

**Cause**: `saveSelectedOrganization()` called with empty/null org ID or name.

**Solution**: Validate inputs before calling: `require(orgId.isNotBlank()) { "org ID required" }`

### Issue: Navigation doesn't update when session changes

**Cause**: ViewModel not collecting Flows or navigation guard not checking Flows.

**Solution**: Ensure ViewModel collects `isAuthenticatedFlow` and `isOrganizationSelectedFlow` in `init` block.

---

## Dependencies

SessionManager requires:

```gradle
// Firebase
implementation("com.google.firebase:firebase-auth-ktx:22.3.1")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Kotlin Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Kotlin Serialization (for DataStore)
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
```

---

## API Summary

```
SESSION RESTORATION
├─ checkAndRestoreSession(): SessionCheckResult
├─ verifyFirebaseUser(): FirebaseUser?
└─ verifyUidConsistency(): Boolean

FIREBASE UTILITIES
├─ getCurrentFirebaseUser(): FirebaseUser?
├─ getCurrentFirebaseUid(): String
├─ isFirebaseUserAuthenticated(): Boolean
├─ getFirebaseUserEmail(): String
├─ getFirebaseUserPhoneNumber(): String?
└─ getFirebaseUserDisplayName(): String?

ORGANIZATION MANAGEMENT
├─ saveSelectedOrganization(id, name): Result<Unit>
├─ loadSelectedOrganizationId(): String
├─ loadSelectedOrganizationName(): String
├─ loadSelectedOrganization(): Pair<String, String>
└─ clearOrganizationContext(): Result<Unit>

SESSION LIFECYCLE
├─ getCurrentSession(): SessionPreferences
├─ getCurrentUserId(): String
├─ getLastLoginTimestamp(): Long
├─ clearSession(): Result<Unit>
└─ signOut(): Result<Unit>

REACTIVE FLOWS
├─ sessionStatusFlow: Flow<SessionPreferences>
├─ currentUserIdFlow: Flow<String>
├─ selectedOrganizationIdFlow: Flow<String>
├─ selectedOrganizationNameFlow: Flow<String>
├─ lastLoginTimestampFlow: Flow<Long>
├─ isAuthenticatedFlow: Flow<Boolean>
└─ isOrganizationSelectedFlow: Flow<Boolean>

SINGLETON
├─ getInstance(context): SessionManager
└─ createWithDependencies(...): SessionManager
```

---

## Support & Maintenance

For issues or questions:
1. Check logs from `SessionManager` tag
2. Review `SessionCheckResult.reasonInvalid` for detailed failure info
3. Verify Firebase auth state: `adb shell pm dump com.sc.biap | grep firebase`
4. Test with MockK unit tests to isolate issues
