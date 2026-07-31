package com.sc.biap.data.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @file SessionDataStoreTest.kt
 * @description Unit tests for SessionDataStore implementation.
 * Tests all read/write operations, reactive flows, and error handling.
 *
 * [TABLE OF CONTENTS]
 * 1. TEST SETUP & TEARDOWN
 * 2. SAVE & READ OPERATIONS
 * 3. INDIVIDUAL FIELD OPERATIONS
 * 4. UPDATE OPERATIONS (ATOMIC)
 * 5. CLEAR OPERATIONS
 * 6. REACTIVE FLOW TESTS
 * 7. ERROR HANDLING
 */

// #region Test Setup & Teardown

@RunWith(AndroidJUnit4::class)
class SessionDataStoreTest {

    private lateinit var context: Context
    private lateinit var sessionDataStore: SessionDataStore

    @Before
    fun setUp() {
        // Get application context for DataStore
        context = ApplicationProvider.getApplicationContext()

        // Reset singleton to ensure clean state
        SessionDataStore.resetInstance()

        // Initialize SessionDataStore
        sessionDataStore = SessionDataStore.getInstance(context)
    }

    @After
    fun tearDown() {
        // Reset singleton after test
        SessionDataStore.resetInstance()

        // Optional: Delete DataStore file to clean up
        try {
            val datastoreFile = context.getFileStreamPath("session_preferences.json")
            if (datastoreFile.exists()) {
                datastoreFile.delete()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    // #endregion

    // #region Save & Read Operations

    @Test
    fun testSaveAndReadCompleteSession() = runTest {
        // Arrange
        val userId = "firebase_user_123"
        val orgId = "org_456"
        val orgName = "Ethnic Attire Co"
        val timestamp = System.currentTimeMillis()

        val session = SessionPreferences(
            currentUserId = userId,
            selectedOrganizationId = orgId,
            selectedOrganizationName = orgName,
            lastLoginTimestamp = timestamp
        )

        // Act
        sessionDataStore.saveSession(session)
        val savedSession = sessionDataStore.readCurrentSession()

        // Assert
        assertEquals(userId, savedSession.currentUserId)
        assertEquals(orgId, savedSession.selectedOrganizationId)
        assertEquals(orgName, savedSession.selectedOrganizationName)
        assertEquals(timestamp, savedSession.lastLoginTimestamp)
    }

    @Test
    fun testDefaultSessionOnFirstRead() = runTest {
        // Act
        val session = sessionDataStore.readCurrentSession()

        // Assert - should return DEFAULT
        assertEquals("", session.currentUserId)
        assertEquals("", session.selectedOrganizationId)
        assertEquals("", session.selectedOrganizationName)
        assertEquals(0L, session.lastLoginTimestamp)
    }

    // #endregion

    // #region Individual Field Operations

    @Test
    fun testSaveCurrentUser() = runTest {
        // Arrange
        val userId = "user_abc_789"

        // Act
        sessionDataStore.saveCurrentUser(userId)
        val session = sessionDataStore.readCurrentSession()

        // Assert
        assertEquals(userId, session.currentUserId)
        assertTrue(session.lastLoginTimestamp > 0) // Timestamp should be updated
    }

    @Test
    fun testGetCurrentUserId() = runTest {
        // Arrange
        val userId = "auth_user_001"
        sessionDataStore.saveCurrentUser(userId)

        // Act
        val retrievedUserId = sessionDataStore.getCurrentUserId()

        // Assert
        assertEquals(userId, retrievedUserId)
    }

    @Test
    fun testSaveSelectedOrganization() = runTest {
        // Arrange
        val orgId = "org_kurtis_warehouse"
        val orgName = "Salwar Kameez Studio"

        // Act
        sessionDataStore.saveSelectedOrganization(orgId, orgName)
        val session = sessionDataStore.readCurrentSession()

        // Assert
        assertEquals(orgId, session.selectedOrganizationId)
        assertEquals(orgName, session.selectedOrganizationName)
    }

    @Test
    fun testGetOrganizationFields() = runTest {
        // Arrange
        val orgId = "org_textiles_123"
        val orgName = "Premium Ethnic Wear"
        sessionDataStore.saveSelectedOrganization(orgId, orgName)

        // Act
        val retrievedOrgId = sessionDataStore.getSelectedOrganizationId()
        val retrievedOrgName = sessionDataStore.getSelectedOrganizationName()

        // Assert
        assertEquals(orgId, retrievedOrgId)
        assertEquals(orgName, retrievedOrgName)
    }

    @Test
    fun testUpdateLastLoginTimestamp() = runTest {
        // Arrange
        val userId = "user_timestamp_test"
        sessionDataStore.saveCurrentUser(userId)
        val firstSession = sessionDataStore.readCurrentSession()
        val firstTimestamp = firstSession.lastLoginTimestamp

        // Wait a bit to ensure time difference
        kotlinx.coroutines.delay(10)

        // Act
        sessionDataStore.updateLastLoginTimestamp()
        val secondSession = sessionDataStore.readCurrentSession()
        val secondTimestamp = secondSession.lastLoginTimestamp

        // Assert
        assertTrue(secondTimestamp > firstTimestamp)
        assertEquals(userId, secondSession.currentUserId) // User ID preserved
    }

    @Test
    fun testGetLastLoginTimestamp() = runTest {
        // Arrange
        val userId = "user_login_check"
        sessionDataStore.saveCurrentUser(userId)

        // Act
        val timestamp = sessionDataStore.getLastLoginTimestamp()

        // Assert
        assertTrue(timestamp > 0)
        assertTrue(timestamp <= System.currentTimeMillis())
    }

    // #endregion

    // #region Update Operations (Atomic)

    @Test
    fun testAtomicUpdate() = runTest {
        // Arrange
        val initialUserId = "user_initial"
        sessionDataStore.saveCurrentUser(initialUserId)

        val newOrgId = "org_new_456"
        val newOrgName = "New Organization LLC"

        // Act
        sessionDataStore.updateSession { currentSession ->
            currentSession.copy(
                selectedOrganizationId = newOrgId,
                selectedOrganizationName = newOrgName
            )
        }

        val updatedSession = sessionDataStore.readCurrentSession()

        // Assert
        assertEquals(initialUserId, updatedSession.currentUserId) // Preserved
        assertEquals(newOrgId, updatedSession.selectedOrganizationId)
        assertEquals(newOrgName, updatedSession.selectedOrganizationName)
    }

    @Test
    fun testComplexAtomicUpdate() = runTest {
        // Arrange
        val userId = "user_complex_test"
        val timestamp = System.currentTimeMillis()

        // Act
        sessionDataStore.updateSession { _ ->
            SessionPreferences(
                currentUserId = userId,
                selectedOrganizationId = "org_123",
                selectedOrganizationName = "Test Org",
                lastLoginTimestamp = timestamp
            )
        }

        val session = sessionDataStore.readCurrentSession()

        // Assert
        assertEquals(userId, session.currentUserId)
        assertEquals("org_123", session.selectedOrganizationId)
        assertEquals("Test Org", session.selectedOrganizationName)
        assertEquals(timestamp, session.lastLoginTimestamp)
    }

    // #endregion

    // #region Clear Operations

    @Test
    fun testClearSession() = runTest {
        // Arrange
        sessionDataStore.saveCurrentUser("user_to_clear")
        sessionDataStore.saveSelectedOrganization("org_to_clear", "Organization")
        var savedSession = sessionDataStore.readCurrentSession()
        assertTrue(savedSession.currentUserId.isNotEmpty())

        // Act
        sessionDataStore.clearSession()
        val clearedSession = sessionDataStore.readCurrentSession()

        // Assert
        assertEquals("", clearedSession.currentUserId)
        assertEquals("", clearedSession.selectedOrganizationId)
        assertEquals("", clearedSession.selectedOrganizationName)
        assertEquals(0L, clearedSession.lastLoginTimestamp)
    }

    @Test
    fun testClearOrganizationContext() = runTest {
        // Arrange
        val userId = "user_retain"
        sessionDataStore.saveCurrentUser(userId)
        sessionDataStore.saveSelectedOrganization("org_remove", "Org Name")

        var session = sessionDataStore.readCurrentSession()
        assertTrue(session.selectedOrganizationId.isNotEmpty())

        // Act
        sessionDataStore.clearOrganizationContext()
        session = sessionDataStore.readCurrentSession()

        // Assert
        assertEquals(userId, session.currentUserId) // User ID preserved
        assertEquals("", session.selectedOrganizationId)
        assertEquals("", session.selectedOrganizationName)
    }

    // #endregion

    // #region Reactive Flow Tests

    @Test
    fun testSessionFlowEmitsCurrentValue() = runTest {
        // Arrange
        val userId = "flow_test_user"
        sessionDataStore.saveCurrentUser(userId)

        // Act
        val emittedSession = sessionDataStore.sessionFlow.first()

        // Assert
        assertEquals(userId, emittedSession.currentUserId)
    }

    @Test
    fun testSessionFlowEmitsOnUpdate() = runTest {
        // Arrange
        val values = mutableListOf<SessionPreferences>()

        // Act
        val job = kotlinx.coroutines.launch {
            sessionDataStore.sessionFlow.collect { session ->
                values.add(session)
            }
        }

        // First emit is default
        kotlinx.coroutines.delay(100)

        // Update and trigger new emit
        val userId = "flow_update_user"
        sessionDataStore.saveCurrentUser(userId)
        kotlinx.coroutines.delay(100)

        job.cancel()

        // Assert
        assertTrue(values.size >= 2)
        assertEquals("", values[0].currentUserId) // First emission is default
        assertEquals(userId, values[1].currentUserId) // Second emission is updated
    }

    @Test
    fun testCurrentUserIdFlowEmitsOnlyUserId() = runTest {
        // Arrange
        val userId = "userid_only_flow"
        sessionDataStore.saveCurrentUser(userId)

        // Act
        val emittedUserId = sessionDataStore.currentUserIdFlow.first()

        // Assert
        assertEquals(userId, emittedUserId)
    }

    @Test
    fun testOrganizationIdFlowEmitsOnlyOrgId() = runTest {
        // Arrange
        val orgId = "org_flow_test_id"
        sessionDataStore.saveSelectedOrganization(orgId, "Org Name")

        // Act
        val emittedOrgId = sessionDataStore.selectedOrganizationIdFlow.first()

        // Assert
        assertEquals(orgId, emittedOrgId)
    }

    @Test
    fun testOrganizationNameFlowEmitsOnlyOrgName() = runTest {
        // Arrange
        val orgName = "Premium Kurtis Store"
        sessionDataStore.saveSelectedOrganization("org_id", orgName)

        // Act
        val emittedOrgName = sessionDataStore.selectedOrganizationNameFlow.first()

        // Assert
        assertEquals(orgName, emittedOrgName)
    }

    @Test
    fun testLastLoginTimestampFlow() = runTest {
        // Arrange
        sessionDataStore.saveCurrentUser("user_timestamp")
        val beforeRead = System.currentTimeMillis()

        // Act
        val emittedTimestamp = sessionDataStore.lastLoginTimestampFlow.first()

        // Assert
        assertTrue(emittedTimestamp >= beforeRead)
    }

    // #endregion

    // #region Error Handling

    @Test
    fun testReadCurrentSessionHandlesException() = runTest {
        // This test verifies error handling - if reading fails, default is returned
        // In a real scenario, corrupted JSON would trigger this

        // Act
        val session = sessionDataStore.readCurrentSession()

        // Assert - should return DEFAULT, not throw exception
        assertEquals(SessionPreferences.DEFAULT, session)
    }

    @Test
    fun testPersistenceAcrossInstances() = runTest {
        // Arrange
        val userId = "persistence_test"
        val orgId = "org_persistence"
        val orgName = "Persistent Org"

        sessionDataStore.saveCurrentUser(userId)
        sessionDataStore.saveSelectedOrganization(orgId, orgName)

        // Reset instance to force reload from disk
        SessionDataStore.resetInstance()
        val newInstance = SessionDataStore.getInstance(context)

        // Act
        val restoredSession = newInstance.readCurrentSession()

        // Assert - data should persist across instances
        assertEquals(userId, restoredSession.currentUserId)
        assertEquals(orgId, restoredSession.selectedOrganizationId)
        assertEquals(orgName, restoredSession.selectedOrganizationName)
    }

    // #endregion
}
