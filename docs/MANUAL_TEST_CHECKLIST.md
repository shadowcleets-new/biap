# BIAP Authentication & Session Management - Manual Test Checklist

**Document Version:** 1.0  
**Last Updated:** 2026-07-31  
**Test Scope:** Authentication flows, session management, organization selection, and offline behavior  
**Platform:** Android (Mobile ERP)

---

## Table of Contents

1. [Google Login](#google-login)
2. [Email Authentication](#email-authentication)
3. [Phone OTP Authentication](#phone-otp-authentication)
4. [Session Restoration](#session-restoration)
5. [Organization Management](#organization-management)
6. [Logout Flow](#logout-flow)
7. [Offline Behavior](#offline-behavior)
8. [Navigation & UI State](#navigation--ui-state)
9. [Orientation Changes](#orientation-changes)
10. [Edge Cases & Error Scenarios](#edge-cases--error-scenarios)

---

## Google Login

### TC-G-001: Successful Google Login

**Precondition:** App installed, user not authenticated, device has Google Play Services  
**Steps:**
1. Launch the app and navigate to login screen
2. Tap "Login with Google" button
3. Complete Google sign-in flow (account selection, permissions)
4. Verify Google account credentials are confirmed

**Expected Outcome:**
- Google authentication dialog opens
- User account list displays correctly
- Permissions prompt appears (if first time)
- App dismisses auth screen and navigates to dashboard/organization selection
- User credentials are stored securely (no plaintext tokens visible)

**Notes:** Test with both existing Google account and new account creation scenario

---

### TC-G-002: Google Login - User Cancels Dialog

**Precondition:** User on login screen, Google auth dialog open  
**Steps:**
1. Tap "Login with Google"
2. In Google auth dialog, tap back button or "Cancel" (if available)
3. Verify app state

**Expected Outcome:**
- User returns to login screen without any errors
- No partial authentication state persists
- Login UI remains functional for retry

---

### TC-G-003: Google Login - Network Timeout During Auth

**Precondition:** Device has slow/unstable network, user initiating Google login  
**Steps:**
1. Tap "Login with Google"
2. During auth flow, disconnect network (toggle airplane mode)
3. Observe behavior

**Expected Outcome:**
- Auth flow gracefully handles timeout (should fail within 30 seconds)
- Error message displays: "Authentication failed - please check your network connection"
- User can retry login
- App does not crash or hang

---

### TC-G-004: Google Login - Invalid/Revoked Token

**Precondition:** User previously logged in with revoked Google token  
**Steps:**
1. Manually revoke Google token from Google Account settings
2. Force app restart or logout/login
3. Attempt Google login

**Expected Outcome:**
- Auth fails with clear error message
- User is prompted to login again
- Previous session data is cleared
- No crash or security bypass

---

## Email Authentication

### TC-E-001: Email Registration - New User

**Precondition:** App installed, user not registered  
**Steps:**
1. Tap "Login with Email" or "Sign Up" tab
2. Enter new email address (e.g., test@example.com)
3. Enter valid password (8+ chars, mix of uppercase/lowercase/numbers)
4. Confirm password
5. Tap "Sign Up" or "Register"
6. Verify verification email received

**Expected Outcome:**
- Email address validation succeeds
- Password meets requirements (show validation indicator)
- Confirmation email sent within 5 seconds
- Success message: "Verification email sent. Please check your inbox."
- User receives email with verification link
- Verification link is valid for 24 hours

---

### TC-E-002: Email Registration - Duplicate Email

**Precondition:** Email already registered  
**Steps:**
1. Attempt to register with existing email (e.g., admin@company.com)
2. Observe validation response

**Expected Outcome:**
- Registration blocked
- Error message: "Email already registered. Please login or use password recovery."
- User directed to login screen option

---

### TC-E-003: Email Registration - Weak Password

**Precondition:** User on registration screen  
**Steps:**
1. Enter email: test@example.com
2. Enter weak password: "123456" or "password"
3. Attempt registration

**Expected Outcome:**
- Password validation fails in real-time or on submit
- Clear error message: "Password must contain uppercase, lowercase, numbers, and be at least 8 characters"
- Registration button remains disabled until password meets criteria
- Password strength indicator shows red/weak status

---

### TC-E-004: Email Registration - Invalid Email Format

**Precondition:** User on registration screen  
**Steps:**
1. Enter invalid email: "notanemail" or "test@" or "@example.com"
2. Attempt to proceed

**Expected Outcome:**
- Real-time validation catches format error
- Error displays: "Please enter a valid email address"
- Registration button disabled
- On blur of email field, validation triggers

---

### TC-E-005: Email Login - Valid Credentials

**Precondition:** User already registered  
**Steps:**
1. Tap "Login with Email"
2. Enter registered email
3. Enter correct password
4. Tap "Login"

**Expected Outcome:**
- Login succeeds
- Session token obtained and stored securely
- User navigates to dashboard/organization selection
- No plaintext credentials stored locally

---

### TC-E-006: Email Login - Invalid Password

**Precondition:** User registered with email  
**Steps:**
1. Tap "Login with Email"
2. Enter registered email
3. Enter incorrect password
4. Tap "Login"

**Expected Outcome:**
- Login fails with: "Invalid email or password"
- User remains on login screen
- Attempt counter incremented (should lock after 5 attempts)
- No indication whether email or password is wrong (security best practice)

---

### TC-E-007: Email Login - Account Locked (Too Many Attempts)

**Precondition:** User made 5+ failed login attempts  
**Steps:**
1. Attempt login with incorrect password for 5th+ time
2. Observe response

**Expected Outcome:**
- Account locked for 15 minutes
- Error message: "Account temporarily locked. Please try again later or reset your password."
- Password reset link offered
- After 15 minutes, login is available again

---

### TC-E-008: Email Login - Resend Verification Email

**Precondition:** User registered but email not verified  
**Steps:**
1. Attempt to login with unverified email
2. Tap "Resend verification email"

**Expected Outcome:**
- New verification email sent
- Confirmation message displays
- Email received within 5 seconds
- Verification link is unique and valid for 24 hours

---

### TC-E-009: Email - Password Reset Flow

**Precondition:** User has active account  
**Steps:**
1. On login screen, tap "Forgot Password?"
2. Enter registered email
3. Tap "Send Reset Link"
4. Check email for reset link
5. Tap reset link
6. Enter new password (with confirmation)
7. Tap "Reset Password"

**Expected Outcome:**
- Reset email sent successfully
- Email arrives within 5 seconds
- Reset link valid for 24 hours
- New password accepted and session invalidated
- User must login with new password
- Old password no longer works
- All active sessions terminated after reset

---

## Phone OTP Authentication

### TC-P-001: Phone OTP - Valid Number & Successful OTP

**Precondition:** App on authentication screen, user has valid phone  
**Steps:**
1. Tap "Login with Phone"
2. Select country code (e.g., +91 for India)
3. Enter valid phone number
4. Tap "Send OTP"
5. Wait for SMS delivery (simulated in test environment)
6. Enter 6-digit OTP code
7. Tap "Verify"

**Expected Outcome:**
- Phone number field accepts input with proper masking
- OTP sent confirmation message: "OTP sent to +91-XXXXX1234"
- OTP input field appears with 6-digit formatting
- OTP accepted and user navigated to next step (organization selection)
- Session created successfully
- OTP expires after 10 minutes

---

### TC-P-002: Phone OTP - Invalid Phone Number Format

**Precondition:** User on phone login screen  
**Steps:**
1. Tap "Login with Phone"
2. Select country code
3. Enter invalid phone (e.g., "abc123" or 3 digits)
4. Attempt to send OTP

**Expected Outcome:**
- Real-time validation prevents invalid submission
- Error: "Please enter a valid phone number"
- Send OTP button disabled
- Helpful hint shows expected format (e.g., "10 digits for India")

---

### TC-P-003: Phone OTP - Resend OTP

**Precondition:** OTP sent to phone, timer visible  
**Steps:**
1. Send OTP successfully
2. Wait 30 seconds (or until resend available)
3. Tap "Resend OTP"

**Expected Outcome:**
- First resend available after 30-60 seconds (based on design)
- New OTP sent to phone
- Countdown timer resets (e.g., "Resend in 45s")
- Previous OTP invalidated
- User can attempt 3 resends maximum within 10 minutes

---

### TC-P-004: Phone OTP - Timeout (No OTP Entry)

**Precondition:** OTP sent, verification screen shown  
**Steps:**
1. Send OTP
2. Wait without entering OTP for 10 minutes
3. Attempt to enter OTP after timeout

**Expected Outcome:**
- OTP code expires after 10 minutes
- Error message: "OTP expired. Please request a new one."
- Resend OTP button becomes primary action
- User can request new OTP with rate limit (max 3 per hour)

---

### TC-P-005: Phone OTP - Incorrect OTP Entry

**Precondition:** OTP sent to phone  
**Steps:**
1. Send OTP successfully
2. Enter wrong 6-digit code (e.g., 000000)
3. Tap "Verify"

**Expected Outcome:**
- Verification fails with: "Invalid OTP. Please try again."
- OTP field cleared for retry
- Attempt counter shown (e.g., "1 of 3 attempts")
- After 3 failed attempts, require new OTP via resend

---

### TC-P-006: Phone OTP - Rate Limiting (Too Many Requests)

**Precondition:** User repeatedly sending OTP  
**Steps:**
1. Send OTP
2. Resend OTP multiple times in rapid succession (5+ times)
3. Attempt another send

**Expected Outcome:**
- After 3 resends within 10 minutes, rate limit enforced
- Error: "Too many OTP requests. Please try again in 1 hour."
- Countdown timer displayed showing when next request allowed
- Security logged (potential spam/attack detection)

---

### TC-P-007: Phone OTP - Network Error During Send

**Precondition:** Device initiating OTP send with unstable network  
**Steps:**
1. Start OTP send with poor connectivity
2. Network disconnects mid-request
3. Observe error handling

**Expected Outcome:**
- Clear error: "Failed to send OTP. Please check your network and try again."
- Retry button available
- OTP not sent (no charge/duplicate sends)
- User can retry after network restored

---

### TC-P-008: Phone OTP - SMS Not Received

**Precondition:** OTP send appears successful but SMS doesn't arrive  
**Steps:**
1. Send OTP
2. Wait 2 minutes for SMS
3. Tap "Didn't receive OTP?"
4. Check if resend is offered or alternative method

**Expected Outcome:**
- "Didn't receive OTP?" link available after 30 seconds
- Option to resend or use alternative (email/call)
- Clear instructions provided
- Support contact option visible

---

### TC-P-009: Phone OTP - Wrong Phone Number Entered

**Precondition:** User sends OTP to wrong number  
**Steps:**
1. Send OTP to incorrect phone number
2. Realize mistake before verification
3. Tap back or "Use different number"

**Expected Outcome:**
- Ability to go back to phone entry screen
- Previous OTP invalidated
- New phone entry field cleared
- No session created for wrong number

---

## Session Restoration

### TC-S-001: Session Persistence - Successful App Restart

**Precondition:** User logged in, app running  
**Steps:**
1. User logged in and viewing dashboard/main screen
2. Force close app (swipe from recent apps)
3. Relaunch app
4. Observe app state

**Expected Outcome:**
- App restores to previous state (dashboard/main screen)
- No login screen displayed (session valid)
- User data/UI state matches pre-close state
- Session token valid (no 401 errors)
- Network requests succeed without re-authentication

---

### TC-S-002: Session Restoration - Token Expiry During Idle

**Precondition:** User logged in, app closed for extended period (session expires)  
**Steps:**
1. User logs in (session expires in 24 hours for testing use 1 hour)
2. App closes
3. Wait 1+ hours (or simulate time passage)
4. Relaunch app

**Expected Outcome:**
- App detects expired token
- User redirected to login screen
- Error message: "Your session has expired. Please login again."
- Graceful logout (no crash)
- Previous session data cleared

---

### TC-S-003: Session Restoration - Refresh Token Mechanism

**Precondition:** User logged in with refresh token mechanism  
**Steps:**
1. User logs in (access token + refresh token obtained)
2. Wait for access token to expire (e.g., 30 min)
3. Make network request (trigger automatic refresh)
4. Observe token refresh

**Expected Outcome:**
- App automatically uses refresh token to obtain new access token
- No user interruption
- Network request succeeds with refreshed token
- Refresh token rotation (old token invalidated, new issued)
- If refresh token expired, user redirected to login

---

### TC-S-004: Session Restoration - Kill App Process (No Graceful Close)

**Precondition:** User logged in  
**Steps:**
1. Launch app from settings > Applications > Force Stop
2. Or kill process via adb: `adb shell am force-stop`
3. Relaunch app

**Expected Outcome:**
- App restarts cleanly (no crash)
- Session state recovered if available
- If session expired, login screen shown
- No data corruption or undefined states

---

### TC-S-005: Session Restoration - Device Restart with App in Background

**Precondition:** User logged in, app in background  
**Steps:**
1. User logs in
2. Press home button (app goes to background)
3. Restart device
4. After boot, tap app icon

**Expected Outcome:**
- App launches successfully
- Session restored if still valid
- If session expired, login screen shown
- No ANR (Application Not Responding) errors
- No data loss

---

### TC-S-006: Session Restoration - Concurrent Session Invalidation

**Precondition:** User logged in on multiple devices  
**Steps:**
1. User logged in on Device A
2. User logs in on Device B with same account
3. Return to Device A, make network request

**Expected Outcome:**
- Concurrent session handling follows app design (allow or revoke)
- If single-session policy: Device A session invalidated, user sees: "Your account logged in elsewhere"
- User redirected to login
- Clear messaging about session conflict
- Option to re-authenticate

---

## Organization Management

### TC-O-001: Auto-Select Single Organization

**Precondition:** User belongs to exactly one organization  
**Steps:**
1. User logs in successfully
2. After authentication, system checks organizations

**Expected Outcome:**
- Organization list screen skipped
- App auto-selects single organization
- User navigates directly to dashboard
- Organization name/logo displayed in header
- No organization selection prompt shown

---

### TC-O-002: Multiple Organization Selection

**Precondition:** User belongs to 3+ organizations  
**Steps:**
1. User logs in successfully
2. Organization selection screen displays
3. User taps on one organization (e.g., "Brand ABC")
4. Confirm selection

**Expected Outcome:**
- All organizations listed with logos/names
- Current organization highlighted (if returning user)
- Recent organizations shown at top (optional UX)
- Tap to select loads organization context
- Dashboard loads with org-specific data
- Header shows selected organization name

---

### TC-O-003: Switch Organization Mid-Session

**Precondition:** User logged in with Organization A context  
**Steps:**
1. User viewing data for Organization A (inventory, orders, etc.)
2. Tap organization name/switcher in header
3. Organization list displays
4. Select Organization B

**Expected Outcome:**
- Organization context switches to B
- All dashboards/data refresh with Organization B data
- Previous org context cleared
- UI updates with org-specific settings/branding
- No data bleed from Organization A to B
- Network requests include correct org identifier

---

### TC-O-004: Organization Switcher - Accessible in All Screens

**Precondition:** User with multiple orgs, in various app sections  
**Steps:**
1. Navigate to different screens (dashboard, inventory, orders, settings)
2. Verify organization switcher available in each

**Expected Outcome:**
- Organization name/switcher visible in navigation header (consistent placement)
- Clickable from any screen
- Quick access without deep navigation
- Loading state shown while switching

---

### TC-O-005: No Organization Assigned (Edge Case)

**Precondition:** User account exists but not assigned to any organization  
**Steps:**
1. User logs in successfully
2. Check organization assignment

**Expected Outcome:**
- Clear message: "No organization assigned. Please contact administrator."
- Option to request access or contact support
- Graceful state (no crash)
- User can logout and try different account

---

### TC-O-006: Organization Membership Revoked

**Precondition:** User logged in, organization access revoked by admin  
**Steps:**
1. User is in organization context
2. Admin removes user from organization
3. User makes next network request or refreshes data

**Expected Outcome:**
- 403 Forbidden or similar access denied response
- Error message: "Your access to this organization has been revoked."
- User redirected to login or organization selection
- If user in other orgs, switch to available org
- Session cleared for revoked org

---

## Logout Flow

### TC-L-001: Logout - Successful Session Termination

**Precondition:** User logged in and on dashboard  
**Steps:**
1. Tap menu/profile icon
2. Tap "Logout" or "Sign Out"
3. Confirm logout if prompted

**Expected Outcome:**
- User session terminated on server
- Local session cleared (tokens removed)
- User redirected to login screen
- Dashboard/user data not accessible
- No cached data visible on login screen

---

### TC-L-002: Logout - Token Invalidation

**Precondition:** User logged in  
**Steps:**
1. Perform logout
2. Attempt to use old token (via API call or cache)

**Expected Outcome:**
- Old token invalid on server (401 Unauthorized)
- App redirects to login
- No unauthorized API calls succeed
- Refresh token also invalidated

---

### TC-L-003: Logout - Cached Data Cleared

**Precondition:** User logged in, navigated multiple screens (app cached data)  
**Steps:**
1. Logout
2. App clears all cached user data
3. Login with different user account

**Expected Outcome:**
- Previous user's data not visible
- No data bleed between accounts
- Cache cleared includes: inventory, orders, customer data, settings
- Fresh data loaded for new user

---

### TC-L-004: Logout - Network Failure During Logout

**Precondition:** User attempting logout, network disconnects  
**Steps:**
1. Tap logout
2. Network unavailable (airplane mode)
3. Observe behavior

**Expected Outcome:**
- Local session cleared anyway (graceful degradation)
- User redirected to login screen
- Server-side logout attempted on network restore (optional async cleanup)
- Clear message: "Logged out. Network unavailable - please ensure server session also cleared."

---

### TC-L-005: Logout from Multiple Devices

**Precondition:** User logged in on Devices A and B  
**Steps:**
1. Logout from Device A
2. Check Device B session status

**Expected Outcome:**
- Behavior depends on logout scope design:
  - **Single Device Logout:** Device B session remains valid
  - **All Devices Logout:** Device B session invalidated, user redirected to login

---

## Offline Behavior

### TC-F-001: Offline - Initial Login Attempt

**Precondition:** Device offline (no network)  
**Steps:**
1. Disable network (airplane mode on)
2. Launch app if not already running
3. Attempt to login

**Expected Outcome:**
- Error message: "No network connection. Please check your internet and try again."
- Login button remains active for retry
- Clear network status indicator (optional)
- Offline mode not entered (login requires network)

---

### TC-F-002: Offline - Existing Session Works Temporarily

**Precondition:** User logged in and has cached data  
**Steps:**
1. User logged in, app cached data from previous session
2. Disable network (airplane mode on)
3. Navigate to cached screens (e.g., view dashboard, view cached inventory)

**Expected Outcome:**
- Cached data displays (read-only)
- Clear indicator showing "Offline Mode"
- Timestamps show when data was last synced
- Refresh/reload attempts show: "No network connection"
- Critical features disabled (no writes)

---

### TC-F-003: Offline - Write Operations Blocked

**Precondition:** Offline mode, cached data visible  
**Steps:**
1. Offline with cached inventory data visible
2. Attempt to create/edit order or update inventory
3. Tap save

**Expected Outcome:**
- Write operation blocked
- Message: "This operation requires an internet connection. Please check your network and try again."
- Data not saved locally (or saved as draft with clear indication)
- No background sync until network restored

---

### TC-F-004: Offline - Network Restoration & Sync

**Precondition:** App was offline, data may be stale  
**Steps:**
1. App in offline mode
2. Re-enable network (disable airplane mode)
3. App detects network restoration
4. Observe sync behavior

**Expected Outcome:**
- Automatic network detection (within 5-10 seconds)
- "Syncing..." indicator briefly shown
- Data refreshed from server
- Timestamps updated to show current sync time
- No user intervention required for basic sync
- Conflicts handled gracefully (server version takes precedence)

---

### TC-F-005: Offline - Logout Attempted

**Precondition:** App offline, user logged in  
**Steps:**
1. Offline mode active
2. Tap logout

**Expected Outcome:**
- Local session cleared
- User returned to login screen
- Server-side logout queued for next network connection (optional)
- No error or blocking dialog

---

### TC-F-006: Offline - Session Expiry During Offline

**Precondition:** Session expires while app offline  
**Steps:**
1. User logged in with session expiring in 1 hour
2. Go offline immediately
3. Wait 1+ hour
4. Bring network back online

**Expected Outcome:**
- When network restored, session expiry detected
- User redirected to login screen
- Error: "Your session has expired. Please login again."
- No automatic re-auth attempted

---

## Navigation & UI State

### TC-N-001: Back Navigation - Login Screen

**Precondition:** User on login screen  
**Steps:**
1. On login screen (not authenticated)
2. Tap system back button

**Expected Outcome:**
- Back button either disabled or exits app (design dependent)
- No navigation to previous hidden screen
- Clear behavior documented

---

### TC-N-002: Back Navigation - After Login

**Precondition:** User just logged in, before dashboard loads  
**Steps:**
1. Complete login
2. System navigates to dashboard
3. Immediately tap back button (before dashboard fully loads)

**Expected Outcome:**
- Back not available (login is terminal state)
- Tapping back does not return to login
- Dashboard loads
- No logout triggered by back press

---

### TC-N-003: Back Navigation - Organization Selection

**Precondition:** Organization selection screen displayed (multiple orgs)  
**Steps:**
1. On organization selection screen
2. Tap back button

**Expected Outcome:**
- User returns to login screen (confirm logout intent if needed)
- Or: Show "Are you sure?" dialog
- Graceful navigation without errors

---

### TC-N-004: Back Navigation - Deep Screen with Organization Context

**Precondition:** User navigated deep into app (Orders > Order Detail)  
**Steps:**
1. In Order Detail screen with Organization B context
2. Switch to Organization A (via switcher)
3. Navigate to Orders > Order Detail for Org A
4. Tap back multiple times

**Expected Outcome:**
- Back navigation respects organization context
- Navigating back stays within current organization
- No cross-organization data shown
- Back stack cleared properly on org switch

---

### TC-N-005: Back Navigation - Authentication Screen Dismissed

**Precondition:** Authentication flow (OTP entry, email verification, etc.)  
**Steps:**
1. In OTP/verification screen
2. Tap back button

**Expected Outcome:**
- Return to phone/email entry screen
- OTP not sent again (reuse still available if time permits)
- State preserved (phone number still filled)
- Verification session not terminated

---

## Orientation Changes

### TC-R-001: Portrait to Landscape - Login Screen

**Precondition:** App on login screen in portrait  
**Steps:**
1. App in portrait orientation showing login form
2. Rotate device to landscape
3. Verify UI adaptation

**Expected Outcome:**
- Layout adapts smoothly (no reload)
- All fields visible without scrolling (if possible)
- Form state preserved (entered email/phone not cleared)
- Keyboard behavior appropriate
- No overlap of elements
- No crashes or ANR

---

### TC-R-002: Portrait to Landscape - OTP Entry

**Precondition:** OTP verification screen in portrait  
**Steps:**
1. OTP entry screen with timer showing
2. Rotate device to landscape
3. Verify timer and state preserved

**Expected Outcome:**
- Layout optimized for landscape
- OTP code field properly sized
- Timer countdown continues correctly
- Partially entered OTP not cleared
- Button positioning adjusted for landscape

---

### TC-R-003: Portrait to Landscape - Dashboard

**Precondition:** User logged in on dashboard in portrait  
**Steps:**
1. Dashboard showing cards/list in portrait
2. Rotate to landscape
3. Navigate to different screen, rotate again

**Expected Outcome:**
- Layout transforms appropriately
- Content reorganizes (e.g., 1 column → 2 columns)
- Scroll position preserved or adapted sensibly
- Images/charts scale appropriately
- No data loss or UI corruption

---

### TC-R-004: Multiple Orientation Changes - Session Maintained

**Precondition:** User logged in  
**Steps:**
1. Rotate portrait → landscape → portrait → landscape (5+ times rapidly)
2. Verify session and UI state

**Expected Outcome:**
- Session maintained through all rotations
- No logout triggered
- UI updates smoothly without lag
- Memory leaks not introduced (test with profiler if needed)
- App responsive throughout

---

### TC-R-005: Orientation Lock - Landscape Only

**Precondition:** Device orientation lock enabled (landscape)  
**Steps:**
1. Enable device landscape lock
2. Launch app or rotate to trigger lock
3. Navigate through various screens

**Expected Outcome:**
- App respects device orientation lock
- UI renders correctly in locked orientation
- All functionality accessible
- No force portrait orientation attempts

---

## Edge Cases & Error Scenarios

### TC-X-001: Rapid Successive Login Attempts

**Precondition:** User on login screen  
**Steps:**
1. Enter credentials
2. Tap login button 10 times rapidly

**Expected Outcome:**
- Single authentication request sent (debounced)
- Button disabled during request
- No duplicate sessions created
- Single success/failure response

---

### TC-X-002: Very Long Email Address

**Precondition:** Registration screen  
**Steps:**
1. Enter extremely long email (300+ characters)
2. Attempt registration

**Expected Outcome:**
- Email field limited to reasonable length (254 chars is standard)
- Validation catches invalid length
- Clear error message

---

### TC-X-003: Special Characters in Phone Number

**Precondition:** Phone login screen  
**Steps:**
1. Enter phone with special characters: +91-9876-54-3210
2. Attempt to send OTP

**Expected Outcome:**
- App strips special characters (keeps only digits + country code)
- Correctly identifies as +919876543210
- OTP sent successfully
- Or: Clear validation message if format not accepted

---

### TC-X-004: Emoji/Unicode in Password

**Precondition:** Registration/password reset  
**Steps:**
1. Set password including emoji: "Pass@123🚀"
2. Attempt registration

**Expected Outcome:**
- Password accepts unicode/emoji (if design supports)
- Or: Clear validation explaining what characters allowed
- Stored securely (hashed, not visible)
- Login works with emoji password

---

### TC-X-005: Extremely Fast Network (Instant Auth)

**Precondition:** Device on high-speed network  
**Steps:**
1. Login with email/OTP
2. Auth responds in <100ms

**Expected Outcome:**
- No UI glitches or blank states
- Loading indicators still show briefly (or no jarring immediate transition)
- Session properly initialized
- No race conditions

---

### TC-X-006: Memory Pressure - Auth During Low Memory

**Precondition:** Device in low memory state  
**Steps:**
1. Device running low on RAM (simulate via developer settings)
2. Attempt login flow
3. Make network request post-login

**Expected Outcome:**
- Auth flow completes without crashes
- App does not forcefully close
- Graceful degradation (no animations if needed)
- Session maintained even under memory pressure

---

### TC-X-007: Auth with Proxy/VPN

**Precondition:** Device connected to VPN or proxy  
**Steps:**
1. Enable VPN on device
2. Attempt login

**Expected Outcome:**
- Auth succeeds normally
- No IP-based restrictions block legitimate VPN users (if not by design)
- Or: Clear message if VPN users blocked

---

### TC-X-008: Time Zone Change During Session

**Precondition:** User logged in, time zone changed  
**Steps:**
1. User logged in with UTC timezone
2. Change device timezone to different region
3. Make network requests or check timestamps

**Expected Outcome:**
- App continues functioning
- Timestamps displayed in new timezone (if timezone-aware)
- No session invalidation
- No time-based token expiry miscalculations

---

### TC-X-009: Certificate Pinning - Invalid Certificate

**Precondition:** App has certificate pinning enabled  
**Steps:**
1. Attempt login with invalid/expired server certificate (simulate via proxy)

**Expected Outcome:**
- Certificate validation fails
- Clear security error message
- Auth does not proceed
- User informed of security issue (no vague "network error")
- App does not allow bypassing certificate validation in production

---

### TC-X-010: Data Limit Exhaustion During Auth

**Precondition:** Device with data limit approaching limit  
**Steps:**
1. Device data plan nearly exhausted
2. Attempt login (which uses data)

**Expected Outcome:**
- Auth attempts normally (respects device settings)
- If data blocked by carrier, clear error about data limit
- No confusing network error
- User aware of data usage

---

## Test Execution Summary

| Test Category | Total Tests | Passed | Failed | Blocked | Notes |
|---|---|---|---|---|---|
| Google Login | 4 | | | | |
| Email Authentication | 9 | | | | |
| Phone OTP | 9 | | | | |
| Session Restoration | 6 | | | | |
| Organization Management | 6 | | | | |
| Logout Flow | 5 | | | | |
| Offline Behavior | 6 | | | | |
| Navigation & Back | 5 | | | | |
| Orientation Changes | 5 | | | | |
| Edge Cases | 10 | | | | |
| **TOTAL** | **65** | | | | |

---

## Defect Reporting Template

When a test fails, document:

```markdown
### Defect: [Short Title]
- **Test Case:** TC-XXX-### 
- **Environment:** Device/OS (e.g., Samsung Galaxy A12, Android 11)
- **Severity:** Critical / High / Medium / Low
- **Steps to Reproduce:**
  1. ...
  2. ...
- **Expected:** [Expected outcome from checklist]
- **Actual:** [What actually happened]
- **Screenshots:** [Attach UI screenshots if applicable]
- **Logs:** [Include error logs if available]
- **Impact:** [Effect on user / business]
```

---

## Sign-Off

**Tester Name:** ___________________  
**Date:** ___________________  
**Platform:** Android | iOS  
**Device:** Model: _________________ OS Version: _________________  
**Build Version:** _________________  
**Overall Status:** ✅ PASS | ⚠️ PASS WITH ISSUES | ❌ FAIL

**Notes:**
_________________________________________________

---

**Document Version History**
- v1.0 (2026-07-31): Initial comprehensive checklist
