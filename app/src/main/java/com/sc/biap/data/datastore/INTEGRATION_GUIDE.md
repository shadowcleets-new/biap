# SessionDataStore Integration Guide

## Overview

This guide shows how to integrate the `SessionDataStore` implementation into your existing authentication flow and application architecture.

## Architecture

```
SessionPreferences.kt
  └─ @Serializable data class with default values

SessionDataStoreSerializer.kt
  └─ Custom Serializer for JSON serialization/deserialization

SessionDataStore.kt
  └─ Repository with suspend functions and reactive Flows
  └─ Singleton pattern with thread-safe initialization
  └─ Dependency injection ready (Hilt compatible)

SessionDataStoreExample.kt
  └─ Integration examples and best practices
```

## Step 1: Update AuthRepository to Save Session

Integrate with your existing `AuthRepository.kt`:

```kotlin
class AuthRepository(private val context: Context) {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val sessionDataStore = SessionDataStore.getInstance(context)

    // In signInWithGoogle()
    suspend fun signInWithGoogle(credential: AuthCredential): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = firebaseAuth.currentUser ?: return@withContext ...

                val userProfile = firebaseUser.toUserProfile(...)
                _currentUser.value = userProfile

                // NEW: Save session to disk
                sessionDataStore.saveCurrentUser(firebaseUser.uid)
                sessionDataStore.updateLastLoginTimestamp()

                Result.success(userProfile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // In signInWithEmail()
    suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val firebaseUser = firebaseAuth.currentUser ?: return@withContext ...

                val userProfile = firebaseUser.toUserProfile(...)
                _currentUser.value = userProfile

                // NEW: Save session to disk
                sessionDataStore.saveCurrentUser(firebaseUser.uid)
                sessionDataStore.updateLastLoginTimestamp()

                Result.success(userProfile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // In verifyOtp()
    suspend fun verifyOtp(verificationId: String, code: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, code)
                firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = firebaseAuth.currentUser ?: return@withContext ...

                val userProfile = firebaseUser.toUserProfile(...)
                _currentUser.value = userProfile

                // NEW: Save session to disk
                sessionDataStore.saveCurrentUser(firebaseUser.uid)
                sessionDataStore.updateLastLoginTimestamp()

                Result.success(userProfile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // In signOut()
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.signOut()
            _currentUser.value = null
            _phoneOtpState.update { PhoneOtpState() }

            // NEW: Clear session from disk
            sessionDataStore.clearSession()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Step 2: Create Hilt Module for Dependency Injection

Create a new file `data/datastore/SessionDataStoreModule.kt`:

```kotlin
package com.sc.biap.data.datastore

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionDataStoreModule {

    @Singleton
    @Provides
    fun provideSessionDataStore(
        @ApplicationContext context: Context
    ): SessionDataStore = SessionDataStore.getInstance(context)
}
```

## Step 3: Inject into ViewModels

Update your existing ViewModels to use SessionDataStore:

```kotlin
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Observe organization changes from session
    val selectedOrganizationName: Flow<String> = sessionDataStore.selectedOrganizationNameFlow

    fun signInWithEmailPassword(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = authRepository.signInWithEmail(email, password)
            result.onSuccess { userProfile ->
                // Session is automatically saved by AuthRepository
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentUser = userProfile,
                        error = null
                    )
                }
                // Navigate to org selector
            }
            result.onFailure { exception ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "Sign in failed"
                    )
                }
            }
        }
    }

    fun selectOrganization(org: Organization) {
        viewModelScope.launch {
            // Save organization context to session
            sessionDataStore.saveSelectedOrganization(org.id, org.name)
            // Navigate to main screen
        }
    }
}
```

## Step 4: Implement Splash/Session Check Screen

Create a session check in your splash screen:

```kotlin
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    fun checkSession() {
        viewModelScope.launch {
            try {
                val session = sessionDataStore.readCurrentSession()

                when {
                    session.currentUserId.isEmpty() -> {
                        // No valid session, go to auth screen
                        _navigationEvent.value = NavigationEvent.NavigateToAuth
                    }
                    session.selectedOrganizationId.isEmpty() -> {
                        // User is authenticated but hasn't selected organization
                        _navigationEvent.value = NavigationEvent.NavigateToOrgPicker
                    }
                    else -> {
                        // Full valid session, go to main screen
                        _navigationEvent.value = NavigationEvent.NavigateToMain
                    }
                }
            } catch (e: Exception) {
                // Error reading session, start fresh at auth
                _navigationEvent.value = NavigationEvent.NavigateToAuth
            }
        }
    }
}
```

## Step 5: Use in Main App

Add session data to your app-level composable:

```kotlin
@Composable
fun MyApp() {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val sessionState by sessionViewModel.sessionState.collectAsState()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(
                onNavigateToAuth = { navController.navigate("auth") },
                onNavigateToMain = { navController.navigate("main") }
            )
        }

        composable("auth") {
            AuthScreen(
                onSignInSuccess = {
                    navController.navigate("orgPicker") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("orgPicker") {
            OrganizationPickerScreen(
                onOrgSelected = { org ->
                    sessionViewModel.switchOrganization(org.id, org.name)
                    navController.navigate("main") {
                        popUpTo("orgPicker") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            // Current organization name available from sessionState
            MainScreen(
                organizationName = sessionState.organizationName,
                onSignOut = {
                    sessionViewModel.signOut()
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}
```

## Step 6: Update TopAppBar with Organization

Display selected organization in your TopAppBar:

```kotlin
@Composable
fun EthnicOpsTopAppBar(
    sessionViewModel: SessionViewModel
) {
    val sessionState by sessionViewModel.sessionState.collectAsState()

    TopAppBar(
        title = {
            Text("${sessionState.organizationName} - BIAP Atelier")
        },
        actions = {
            IconButton(onClick = { /* show org menu */ }) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = "Organization"
                )
            }
        }
    )
}
```

## Testing

### Unit Test Example

```kotlin
@RunWith(RobolectricTestRunner::class)
class SessionDataStoreTest {

    private lateinit var sessionDataStore: SessionDataStore
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset singleton between tests
        SessionDataStore.resetInstance()
    }

    @After
    fun tearDown() {
        SessionDataStore.resetInstance()
        context.deleteDatabase("session_preferences.json")
    }

    @Test
    fun testSaveAndLoadSession() = runTest {
        sessionDataStore = SessionDataStore.getInstance(context)

        val userId = "user123"
        val orgId = "org456"
        val orgName = "My Organization"

        sessionDataStore.saveCurrentUser(userId)
        sessionDataStore.saveSelectedOrganization(orgId, orgName)

        val session = sessionDataStore.readCurrentSession()

        assertEquals(userId, session.currentUserId)
        assertEquals(orgId, session.selectedOrganizationId)
        assertEquals(orgName, session.selectedOrganizationName)
    }

    @Test
    fun testSessionFlow() = runTest {
        sessionDataStore = SessionDataStore.getInstance(context)

        val userId = "user789"
        sessionDataStore.saveCurrentUser(userId)

        sessionDataStore.currentUserIdFlow.first().apply {
            assertEquals(userId, this)
        }
    }
}
```

## API Reference

### Read Operations

```kotlin
// One-time read
val session = sessionDataStore.readCurrentSession()
val userId = sessionDataStore.getCurrentUserId()
val orgId = sessionDataStore.getSelectedOrganizationId()
val orgName = sessionDataStore.getSelectedOrganizationName()
val lastLogin = sessionDataStore.getLastLoginTimestamp()

// Reactive Flows
sessionDataStore.sessionFlow.collect { session ->
    // Handle complete session changes
}

sessionDataStore.currentUserIdFlow.collect { userId ->
    // Handle user ID changes
}

sessionDataStore.selectedOrganizationIdFlow.collect { orgId ->
    // Handle org ID changes
}

sessionDataStore.selectedOrganizationNameFlow.collect { orgName ->
    // Handle org name changes
}

sessionDataStore.lastLoginTimestampFlow.collect { timestamp ->
    // Handle login timestamp changes
}
```

### Write Operations

```kotlin
// Save complete session
sessionDataStore.saveSession(SessionPreferences(
    currentUserId = "user123",
    selectedOrganizationId = "org456",
    selectedOrganizationName = "My Org",
    lastLoginTimestamp = System.currentTimeMillis()
))

// Save individual fields
sessionDataStore.saveCurrentUser("user123")
sessionDataStore.saveSelectedOrganization("org456", "My Org")
sessionDataStore.updateLastLoginTimestamp()

// Atomic update
sessionDataStore.updateSession { currentSession ->
    currentSession.copy(
        currentUserId = "newUser",
        selectedOrganizationId = "newOrg"
    )
}

// Clear operations
sessionDataStore.clearSession() // Clear all
sessionDataStore.clearOrganizationContext() // Clear org only
```

## Best Practices

1. **Always inject via Hilt** - Use dependency injection for consistency
2. **Update after auth** - Save session immediately after successful authentication
3. **Clear on sign out** - Always call `clearSession()` when user signs out
4. **Use Flows for UI** - Observe changes with `.collect { }` in ViewModels
5. **Handle errors gracefully** - SessionDataStore returns defaults on corruption
6. **Encrypt sensitive data** - Consider using `androidx.security:security-crypto` for encrypted storage
7. **Test thoroughly** - Use `resetInstance()` between test cases

## Files Created

- `SessionPreferences.kt` - Data class with serialization
- `SessionDataStoreSerializer.kt` - Custom JSON serializer
- `SessionDataStore.kt` - Main repository with API
- `SessionDataStoreExample.kt` - Integration examples
- `INTEGRATION_GUIDE.md` - This file
