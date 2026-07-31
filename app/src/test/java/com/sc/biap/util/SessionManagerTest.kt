package com.sc.biap.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sc.biap.data.datastore.SessionDataStore
import com.sc.biap.data.datastore.SessionPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @file SessionManagerTest.kt
 * @description Unit tests for SessionManager covering session restoration,
 * Firebase verification, organization management, and error handling.
 *
 * [TABLE OF CONTENTS]
 * 1. TEST SETUP & TEARDOWN
 * 2. SESSION RESTORATION TESTS
 * 3. FIREBASE VERIFICATION TESTS
 * 4. ORGANIZATION MANAGEMENT TESTS
 * 5. SESSION LIFECYCLE TESTS
 * 6. ERROR HANDLING TESTS
 */

// #region Test Setup & Teardown

@RunWith(AndroidJUnit4::class)
class SessionManagerTest {

    @MockK
    private lateinit var mockSessionDataStore: SessionDataStore

    @MockK
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @MockK
    private lateinit var mockFirebaseUser: FirebaseUser

    private lateinit var context: Context
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = ApplicationProvider.getApplicationContext()
        sessionManager = SessionManager(context, mockSessionDataStore, mockFirebaseAuth)
    }

    @After
    fun tearDown() {
        SessionManager.resetInstance()
        SessionDataStore.resetInstance()
    }

    // #endregion

    // #region Session Restoration Tests

    /**
     * Test: Session restoration with valid Firebase user and persisted session.
     * Expected: Session marked as valid with correct user ID and org context.
     */
    @Test
    fun testCheckAndRestoreSession_ValidSession() = runBlocking {
        // Arrange
        val testUserId = "user123"
        val testOrgId = "org456"
        val testOrgName = "Test Organization"
        val testTimestamp = System.currentTimeMillis()

        val persistedSession = SessionPreferences(
            currentUserId = testUserId,
            selectedOrganizationId = testOrgId,
            selectedOrganizationName = testOrgName,
            lastLoginTimestamp = testTimestamp
        )

        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        every { mockFirebaseUser.uid } returns testUserId
        coEvery { mockSessionDataStore.readCurrentSession() } returns persistedSession
        coEvery { mockSessionDataStore.updateLastLoginTimestamp() } returns Unit

        // Act
        val result = sessionManager.checkAndRestoreSession()

        // Assert
        assertTrue(result.isSessionValid)
        assertEquals(testUserId, result.currentUserId)
        assertEquals(testOrgId, result.selectedOrgId)
        assertEquals(testOrgName, result.selectedOrgName)
        assertFalse(result.reasonInvalid?.isNotEmpty() ?: false)

        coVerify { mockSessionDataStore.updateLastLoginTimestamp() }
    }

    /**
     * Test: Session restoration when Firebase user is logged out.
     * Expected: Session marked as invalid, stale session cleared from DataStore.
     */
    @Test
    fun testCheckAndRestoreSession_FirebaseUserNull() = runBlocking {
        // Arrange
        val staleSession = SessionPreferences(
            currentUserId = "oldUser",
            selectedOrganizationId = "oldOrg",
            selectedOrganizationName = "Old Org",
            lastLoginTimestamp = 1000L
        )

        every { mockFirebaseAuth.currentUser } returns null
        coEvery { mockSessionDataStore.readCurrentSession() } returns staleSession
        coEvery { mockSessionDataStore.clearSession() } returns Unit

        // Act
        val result = sessionManager.checkAndRestoreSession()

        // Assert
        assertFalse(result.isSessionValid)
        assertEquals("Firebase user not authenticated", result.reasonInvalid)

        // Verify stale session was cleared
        coVerify { mockSessionDataStore.clearSession() }
    }

    /**
     * Test: First-time login with no persisted session.
     * Expected: Firebase UID persisted, session marked as valid.
     */
    @Test
    fun testCheckAndRestoreSession_FirstTimeLogin() = runBlocking {
        // Arrange
        val newUserId = "newUser789"
        val emptySession = SessionPreferences.DEFAULT

        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        every { mockFirebaseUser.uid } returns newUserId
        coEvery { mockSessionDataStore.readCurrentSession() } returns emptySession
        coEvery { mockSessionDataStore.saveCurrentUser(newUserId) } returns Unit

        // Act
        val result = sessionManager.checkAndRestoreSession()

        // Assert
        assertTrue(result.isSessionValid)
        assertEquals(newUserId, result.currentUserId)
        assertEquals("", result.selectedOrgId) // No org selected yet
        assertTrue(result.selectedOrgName.isEmpty())

        coVerify { mockSessionDataStore.saveCurrentUser(newUserId) }
    }

    /**
     * Test: UID mismatch between Firebase and persisted session.
     * Expected: Session marked as invalid, entire session cleared (security measure).
     */
    @Test
    fun testCheckAndRestoreSession_UidMismatch() = runBlocking {
        // Arrange
        val persistedUserId = "user123"
        val firebaseUserId = "user456"

        val persistedSession = SessionPreferences(
            currentUserId = persistedUserId,
            selectedOrganizationId = "org789",
            selectedOrganizationName = "Test Org",
            lastLoginTimestamp = 1000L
        )

        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        every { mockFirebaseUser.uid } returns firebaseUserId
        coEvery { mockSessionDataStore.readCurrentSession() } returns persistedSession
        coEvery { mockSessionDataStore.clearSession() } returns Unit

        // Act
        val result = sessionManager.checkAndRestoreSession()

        // Assert
        assertFalse(result.isSessionValid)
        assertEquals("Firebase UID mismatch with persisted session", result.reasonInvalid)

        // Verify entire session was cleared for security
        coVerify { mockSessionDataStore.clearSession() }
    }

    // #endregion

    // #region Firebase Verification Tests

    /**
     * Test: Firebase user verification when user is authenticated.
     * Expected: Returns current FirebaseUser object.
     */
    @Test
    fun testVerifyFirebaseUser_Authenticated() {
        // Arrange
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser

        // Act
        val result = sessionManager.verifyFirebaseUser()

        // Assert
        assertEquals(mockFirebaseUser, result)
    }

    /**
     * Test: Firebase user verification when user is not authenticated.
     * Expected: Returns null.
     */
    @Test
    fun testVerifyFirebaseUser_NotAuthenticated() {
        // Arrange
        every { mockFirebaseAuth.currentUser } returns null

        // Act
        val result = sessionManager.verifyFirebaseUser()

        // Assert
        assertEquals(null, result)
    }

    /**
     * Test: Quick boolean check for Firebase authentication status.
     * Expected: Returns true if currentUser is not null.
     */
    @Test
    fun testIsFirebaseUserAuthenticated() {
        // Arrange
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser

        // Act
        val result = sessionManager.isFirebaseUserAuthenticated()

        // Assert
        assertTrue(result)
    }

    /**
     * Test: UID consistency check when UIDs match.
     * Expected: Returns true.
     */
    @Test
    fun testVerifyUidConsistency_UidsMatch() = runBlocking {
        // Arrange
        val testUid = "user123"

        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        every { mockFirebaseUser.uid } returns testUid
        coEvery { mockSessionDataStore.getCurrentUserId() } returns testUid

        // Act
        val result = sessionManager.verifyUidConsistency()

        // Assert
        assertTrue(result)
    }

    /**
     * Test: UID consistency check when UUIDs don't match.
     * Expected: Returns false.
     */
    @Test
    fun testVerifyUidConsistency_UidsMismatch() = runBlocking {
        // Arrange
        every { mockFirebaseAuth.currentUser } returns mockFirebaseUser
        every { mockFirebaseUser.uid } returns "firebaseUid"
        coEvery { mockSessionDataStore.getCurrentUserId() } returns "storedUid"

        // Act
        val result = sessionManager.verifyUidConsistency()

        // Assert
        assertFalse(result)
    }

    /**
     * Test: UID consistency check when Firebase user is null.
     * Expected: Returns false.
     */
    @Test
    fun testVerifyUidConsistency_FirebaseUserNull() = runBlocking {
        // Arrange
        every { mockFirebaseAuth.currentUser } returns null
        coEvery { mockSessionDataStore.getCurrentUserId() } returns "storedUid"

        // Act
        val result = sessionManager.verifyUidConsistency()

        // Assert
        assertFalse(result)
    }

    // #endregion

    // #region Organization Management Tests

    /**
     * Test: Save selected organization with valid inputs.
     * Expected: Success result, DataStore called with correct parameters.
     */
    @Test
    fun testSaveSelectedOrganization_Success() = runBlocking {
        // Arrange
        val orgId = "org123"
        val orgName = "My Organization"
        coEvery { mockSessionDataStore.saveSelectedOrganization(orgId, orgName) } returns Unit

        // Act
        val result = sessionManager.saveSelectedOrganization(orgId, orgName)

        // Assert
        assertTrue(result.isSuccess)
        coVerify { mockSessionDataStore.saveSelectedOrganization(orgId, orgName) }
    }

    /**
     * Test: Save selected organization with empty organization ID.
     * Expected: Failure result, validation error.
     */
    @Test
    fun testSaveSelectedOrganization_EmptyOrgId() = runBlocking {
        // Act
        val result = sessionManager.saveSelectedOrganization("", "Org Name")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("organizationId cannot be empty", result.exceptionOrNull()?.message)
    }

    /**
     * Test: Save selected organization with empty organization name.
     * Expected: Failure result, validation error.
     */
    @Test
    fun testSaveSelectedOrganization_EmptyOrgName() = runBlocking {
        // Act
        val result = sessionManager.saveSelectedOrganization("orgId", "")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("organizationName cannot be empty", result.exceptionOrNull()?.message)
    }

    /**
     * Test: Load selected organization ID.
     * Expected: Returns correct organization ID from DataStore.
     */
    @Test
    fun testLoadSelectedOrganizationId() = runBlocking {
        // Arrange
        val testOrgId = "org456"
        coEvery { mockSessionDataStore.getSelectedOrganizationId() } returns testOrgId

        // Act
        val result = sessionManager.loadSelectedOrganizationId()

        // Assert
        assertEquals(testOrgId, result)
    }

    /**
     * Test: Load selected organization name.
     * Expected: Returns correct organization name from DataStore.
     */
    @Test
    fun testLoadSelectedOrganizationName() = runBlocking {
        // Arrange
        val testOrgName = "Test Organization Inc."
        coEvery { mockSessionDataStore.getSelectedOrganizationName() } returns testOrgName

        // Act
        val result = sessionManager.loadSelectedOrganizationName()

        // Assert
        assertEquals(testOrgName, result)
    }

    /**
     * Test: Clear organization context while preserving user auth.
     * Expected: Success result, clearOrganizationContext called on DataStore.
     */
    @Test
    fun testClearOrganizationContext() = runBlocking {
        // Arrange
        coEvery { mockSessionDataStore.clearOrganizationContext() } returns Unit

        // Act
        val result = sessionManager.clearOrganizationContext()

        // Assert
        assertTrue(result.isSuccess)
        coVerify { mockSessionDataStore.clearOrganizationContext() }
    }

    // #endregion

    // #region Session Lifecycle Tests

    /**
     * Test: Complete session clear on logout.
     * Expected: Success result, clearSession called on DataStore.
     */
    @Test
    fun testClearSession() = runBlocking {
        // Arrange
        coEvery { mockSessionDataStore.clearSession() } returns Unit

        // Act
        val result = sessionManager.clearSession()

        // Assert
        assertTrue(result.isSuccess)
        coVerify { mockSessionDataStore.clearSession() }
    }

    /**
     * Test: Sign out performs both DataStore clear and Firebase signOut.
     * Expected: Success result, both operations called.
     */
    @Test
    fun testSignOut_Success() = runBlocking {
        // Arrange
        coEvery { mockSessionDataStore.clearSession() } returns Unit
        every { mockFirebaseAuth.signOut() } returns Unit

        // Act
        val result = sessionManager.signOut()

        // Assert
        assertTrue(result.isSuccess)
        coVerify { mockSessionDataStore.clearSession() }
    }

    /**
     * Test: Get current user ID from session.
     * Expected: Returns correct user ID from DataStore.
     */
    @Test
    fun testGetCurrentUserId() = runBlocking {
        // Arrange
        val testUserId = "user123"
        coEvery { mockSessionDataStore.getCurrentUserId() } returns testUserId

        // Act
        val result = sessionManager.getCurrentUserId()

        // Assert
        assertEquals(testUserId, result)
    }

    /**
     * Test: Get last login timestamp from session.
     * Expected: Returns correct timestamp from DataStore.
     */
    @Test
    fun testGetLastLoginTimestamp() = runBlocking {
        // Arrange
        val testTimestamp = 1234567890L
        coEvery { mockSessionDataStore.getLastLoginTimestamp() } returns testTimestamp

        // Act
        val result = sessionManager.getLastLoginTimestamp()

        // Assert
        assertEquals(testTimestamp, result)
    }

    // #endregion

    // #region Error Handling Tests

    /**
     * Test: Session restoration exception handling.
     * Expected: Returns invalid session with error message.
     */
    @Test
    fun testCheckAndRestoreSession_ExceptionHandling() = runBlocking {
        // Arrange
        coEvery { mockSessionDataStore.readCurrentSession() } throws Exception("DataStore error")

        // Act
        val result = sessionManager.checkAndRestoreSession()

        // Assert
        assertFalse(result.isSessionValid)
        assertTrue(result.reasonInvalid?.contains("DataStore error") ?: false)
    }

    /**
     * Test: Save organization exception handling.
     * Expected: Returns failure result with exception.
     */
    @Test
    fun testSaveSelectedOrganization_ExceptionHandling() = runBlocking {
        // Arrange
        coEvery { mockSessionDataStore.saveSelectedOrganization(any(), any()) } throws Exception("DataStore write failed")

        // Act
        val result = sessionManager.saveSelectedOrganization("org123", "Org Name")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("DataStore write failed", result.exceptionOrNull()?.message)
    }

    /**
     * Test: Clear session exception handling.
     * Expected: Returns failure result with exception.
     */
    @Test
    fun testClearSession_ExceptionHandling() = runBlocking {
        // Arrange
        coEvery { mockSessionDataStore.clearSession() } throws Exception("Clear failed")

        // Act
        val result = sessionManager.clearSession()

        // Assert
        assertTrue(result.isFailure)
        assertEquals("Clear failed", result.exceptionOrNull()?.message)
    }

    // #endregion
}
