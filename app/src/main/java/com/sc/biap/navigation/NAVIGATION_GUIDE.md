# Navigation Architecture Guide

Complete Jetpack Compose Navigation system with type-safe routes, guards, and back stack management.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Route Definitions](#route-definitions)
3. [Navigation Guards](#navigation-guards)
4. [Back Stack Management](#back-stack-management)
5. [Usage Examples](#usage-examples)
6. [Testing](#testing)
7. [Migration from Legacy Code](#migration-from-legacy-code)

---

## Architecture Overview

The navigation system is organized into four core modules:

### 1. **NavigationRoute.kt** — Route Definitions
Sealed classes for all navigation destinations with metadata.

```kotlin
@Serializable data object HomeRoute : NavKey
@Serializable data class DesignDetailRoute(val designId: String) : NavKey
```

**Key functions:**
- `requiresAuthentication()` — Determines if route needs logged-in user
- `requiresOrganization()` — Determines if route needs active org selection
- `backStackPolicy()` — Defines back stack behavior (TERMINAL, NORMAL, MODAL)
- `clearsBackStack()` — Whether navigation should wipe history

### 2. **NavigationGuard.kt** — Access Control
Guards enforce authentication and organization selection requirements.

```kotlin
val result = evaluateNavigationGuard(targetRoute, authState)
when (result) {
    GuardResult.Allow -> { /* Proceed */ }
    GuardResult.RedirectToAuth -> { /* Go to login */ }
    GuardResult.RedirectToOrgPicker -> { /* Go to org selection */ }
    is GuardResult.Deny -> { /* Access forbidden */ }
}
```

### 3. **NavigationGraph.kt** — Complete Graph
The main composable defining all screens and transitions.

```kotlin
MainNavigationGraph(
    authStateViewModel = authVM,
    isDebugMode = BuildConfig.DEBUG
)
```

### 4. **NavigationState.kt** — State Tracking
Back stack utilities, history tracking, and state persistence.

---

## Route Definitions

### Public Routes (No Auth Required)

| Route | Description | Parent |
|-------|-------------|--------|
| `SplashRoute` | Entry point, auth check | N/A |
| `AuthChooserRoute` | Method selection (Google/Email/Phone) | N/A |
| `EmailLoginRoute` | Email/password login | AuthChooserRoute |
| `EmailRegisterRoute` | New account creation | AuthChooserRoute |
| `PhoneEntryRoute` | Phone number entry (E.164) | AuthChooserRoute |
| `OtpVerifyRoute` | 6-digit OTP verification | PhoneEntryRoute |

### Protected Routes (Requires Authentication)

| Route | Requires Org | Description | Parent |
|-------|--------------|-------------|--------|
| `OrganizationPickerRoute` | ❌ | Select or create org | N/A |
| `HomeRoute` | ✅ | Main dashboard | N/A |
| `AddDesignRoute` | ✅ | Create new design | HomeRoute |
| `DesignDetailRoute(id)` | ✅ | View design details | HomeRoute |
| `OrganizationSettingsRoute` | ✅ | Org management | HomeRoute |

### Terminal Routes (Cannot Pop)

Terminal routes act as "dead ends" — you cannot navigate back before them without explicit logout:

- `SplashRoute` — First-time entry
- `AuthChooserRoute` — Auth boundary
- `HomeRoute` — App main screen (clears auth history)

### Route Transitions

```
Unauthenticated User:
  SplashRoute → AuthChooserRoute → {EmailLogin|PhoneEntry|OtpVerify|EmailRegister}

After Authentication:
  OrganizationPickerRoute → HomeRoute (terminal)

From HomeRoute:
  HomeRoute → {AddDesignRoute|DesignDetailRoute|OrganizationSettingsRoute}
  Settings/Design → pop → HomeRoute
```

---

## Navigation Guards

### Guard Types

```kotlin
sealed class GuardResult {
    data object Allow : GuardResult()                          // ✓ Proceed
    data object RedirectToAuth : GuardResult()                 // → Go to login
    data object RedirectToOrgPicker : GuardResult()            // → Select org
    data class Deny(val reason: String) : GuardResult()        // ✗ Forbidden
}
```

### Guard Evaluation

Guards run **before** navigation to prevent unauthorized access:

```kotlin
// Automatic guard enforcement in MainNavigationGraph
LaunchedEffect(backStack.current) {
    val targetRoute = backStack.current ?: SplashRoute
    val guardResult = evaluateNavigationGuard(targetRoute, authState)
    
    when (guardResult) {
        GuardResult.Allow -> { /* Continue */ }
        GuardResult.RedirectToAuth -> {
            backStack.removeLastOrNull()
            backStack.add(AuthChooserRoute)
        }
        // ... handle other results
    }
}
```

### Custom Guard Checkpoint

For complex multi-condition checks:

```kotlin
val checkpoint = GuardCheckpoint(
    isAuthenticated = authState.isLoggedIn,
    hasOrganization = authState.currentOrganization != null,
    isAuthRoute = targetRoute.requiresAuthentication(),
    isOrgRoute = targetRoute.requiresOrganization()
)

val result = checkpoint.evaluate()
```

---

## Back Stack Management

### Back Stack Policies

Each route has a back stack policy controlling pop behavior:

```kotlin
enum class BackStackPolicy {
    TERMINAL,  // Cannot pop (SplashRoute, AuthChooserRoute, HomeRoute)
    NORMAL,    // Standard back stack pop
    MODAL      // Appears over previous content, dismisses on back
}
```

### Back Stack Operations

```kotlin
// Add route
backStack.add(DesignDetailRoute("design-123"))

// Pop (go back)
backStack.removeLastOrNull()

// Pop until reaching a specific type
backStack.popUntilType<HomeRoute>()

// Clear and set root
backStack.clearAndSetRoot(SplashRoute)

// Check current
if (backStack.current == HomeRoute) { }

// Distance to route
val distance = backStack.distanceTo(HomeRoute)
```

### Preventing Auth Bypass

Back stack is automatically managed to prevent bypassing authentication:

1. **Splash → Auth → Design Detail:**
   - Pop from Detail → Auth (terminal boundary prevents further pop)
   
2. **Auth → Org Picker → Home:**
   - Cannot pop behind AuthChooserRoute without explicit logout
   
3. **Home acts as terminal:**
   - Settings/Design screens pop back to Home
   - Cannot pop behind Home without logout

---

## Usage Examples

### Example 1: Navigate to Design Detail

```kotlin
// From HomeScreen
onDesignClick = { designId ->
    backStack.add(DesignDetailRoute(designId))
}

// Back button
onBackClick = {
    backStack.removeLastOrNull() // Returns to HomeRoute
}
```

### Example 2: Email Registration Flow

```kotlin
// User chooses Email auth
AuthChooserScreen(
    onEmailAuth = { 
        backStack.add(EmailLoginRoute) 
    }
)

// User clicks "Create account"
EmailLoginScreen(
    onNavigateToRegister = { 
        backStack.add(EmailRegisterRoute) 
    }
)

// After successful registration
EmailRegisterScreen(
    onRegistrationSuccess = {
        backStack.removeLastOrNull() // Remove register form
        backStack.add(EmailLoginRoute) // Show login (user can now sign in)
    }
)
```

### Example 3: Organization Selection

```kotlin
// After successful auth, if no org selected
if (authState.currentOrganization == null) {
    backStack.add(OrganizationPickerRoute)
}

// User selects organization
OrganizationPickerScreen(
    onOrganizationSelected = { orgId ->
        authStateViewModel.selectOrganization(selectedOrg)
        backStack.removeLastOrNull()
        backStack.add(HomeRoute) // Terminal route
    }
)
```

### Example 4: Debug Navigation

```kotlin
// Enable guard logging
MainNavigationGraph(
    authStateViewModel = authVM,
    isDebugMode = true // Logs all guard evaluations
)

// In Logcat:
// ✓ Route HomeRoute — Access granted
// ⤵ Route HomeRoute — Redirect to Auth (not logged in)
// ✗ Route SettingsRoute — Denied: insufficient permissions
```

---

## Testing

### Test Setup

```kotlin
import com.sc.biap.navigation.*

// Create test auth state
val testState = TestAppAuthStateBuilder()
    .withUser("user123", "test@example.com")
    .withOrganization("org123", "My Org")
    .loggedIn()
    .build()

// Test guard
val result = evaluateNavigationGuard(HomeRoute, testState)
assert(result is GuardResult.Allow)
```

### Test Navigation Sequences

```kotlin
// Simulate user flow
NavigationSimulator()
    .startWith(SplashRoute)
    .navigateTo(AuthChooserRoute, unauthState)
    .assertCurrentRoute(AuthChooserRoute)
    .navigateTo(EmailLoginRoute, unauthState)
    .assertCurrentRoute(EmailLoginRoute)
    .back()
    .assertCurrentRoute(AuthChooserRoute)
```

### Guard Unit Tests

```kotlin
// Unauthenticated access denied
NavigationGuardTests.testUnauthenticatedUserRedirect()

// Authenticated without org redirects to picker
NavigationGuardTests.testAuthenticatedWithoutOrgRedirect()

// Full access for authenticated with org
NavigationGuardTests.testFullyAuthenticatedAccess()

// Public routes always allow
NavigationGuardTests.testPublicRoutesAlwaysAllow()
```

---

## Migration from Legacy Code

### Before (Legacy)

```kotlin
@Serializable data object Splash : NavKey
@Serializable data object MultiAuth : NavKey
@Serializable data object OrganizationSelection : NavKey

MainNavigation(authStateViewModel)
```

### After (New)

```kotlin
import com.sc.biap.navigation.*

// Routes are now in navigation package
SplashRoute
AuthChooserRoute
OrganizationPickerRoute

MainNavigationGraph(authStateViewModel, isDebugMode = false)
```

### Deprecation Timeline

1. **Phase 1:** New code uses `navigation.NavigationRoute`
   - Old `NavigationKeys.kt` remains as deprecated aliases
   - Existing code continues to work

2. **Phase 2:** Existing screens migrated incrementally
   - No breaking changes during this phase

3. **Phase 3 (Future):** Remove `NavigationKeys.kt`
   - Only new route names in use
   - Full migration to new architecture

### Step-by-Step Migration

For each screen using legacy routes:

```kotlin
// BEFORE
import com.sc.biap.Splash
import com.sc.biap.Main

backStack.add(Splash)
backStack.add(Main)

// AFTER
import com.sc.biap.navigation.SplashRoute
import com.sc.biap.navigation.HomeRoute

backStack.add(SplashRoute)
backStack.add(HomeRoute)
```

---

## Performance Considerations

### Animation Profiles

Navigation transitions adapt to device performance:

- **High-Fidelity:** Spring physics + slide + fade
  - High-end devices
  - High refresh rates
  - Active performance mode
  
- **Battery Saver:** Simple fade only
  - Low-end devices
  - Low CPU cores (`navigator.hardwareConcurrency <= 4`)
  - Battery saver enabled
  - Prefers reduced motion

### Back Stack Memory

- Initial back stack size: ~1-2 routes
- Typical depth: 3-5 routes (Home → Detail → Settings)
- Max recommended: 8-10 routes (exceeding risks context loss)

### Guard Evaluation

Guards run synchronously and are optimized for:
- O(1) auth check
- O(1) organization check
- Zero allocations in guard path

---

## Troubleshooting

### Issue: Can't Navigate Back from Home

**Expected Behavior:** HomeRoute is terminal — you cannot pop back past it.

**Solution:** To leave HomeRoute, user must:
1. Tap explicit "Sign Out" button, OR
2. Session expires (app returns to auth)

### Issue: User Stuck on Auth Screen

**Cause:** Guard preventing authentication state propagation.

**Debug:**
```kotlin
MainNavigationGraph(authStateViewModel, isDebugMode = true)
// Check logcat for guard evaluation logs
```

### Issue: Organization Picker Never Appears

**Cause:** `authState.currentOrganization` is not null.

**Solution:**
```kotlin
// Verify in AppAuthStateViewModel
if (authState.currentOrganization == null) {
    backStack.add(OrganizationPickerRoute)
}
```

---

## API Reference

### NavigationRoute.kt

| Function | Returns | Description |
|----------|---------|-------------|
| `route.requiresAuthentication()` | Boolean | Is auth required |
| `route.requiresOrganization()` | Boolean | Is org required |
| `route.backStackPolicy()` | BackStackPolicy | Back behavior |
| `route.clearsBackStack()` | Boolean | Clears history on nav |

### NavigationGuard.kt

| Function | Returns | Description |
|----------|---------|-------------|
| `evaluateNavigationGuard(route, state)` | GuardResult | Guard evaluation |
| `fallbackRoute(state)` | NavKey | Safe fallback |
| `logGuardEvaluation(route, result, debug)` | Unit | Debug logging |

### NavigationGraph.kt

| Composable | Purpose |
|-----------|---------|
| `MainNavigationGraph(vm, debug)` | Root navigation |

### NavigationState.kt

| Class | Purpose |
|-------|---------|
| `BackStackState` | Snapshot of current state |
| `NavigationHistory` | Record navigation events |
| `BackStackSnapshot` | For state restoration |

---

## Resources

- [Jetpack Compose Navigation](https://developer.android.com/jetpack/compose/navigation)
- [Navigation3 Runtime](https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/package-summary)
- [Type-Safe Navigation](https://developer.android.com/jetpack/compose/navigation/type-safe)
- [Back Stack Behavior](https://developer.android.com/guide/navigation/design/back)
