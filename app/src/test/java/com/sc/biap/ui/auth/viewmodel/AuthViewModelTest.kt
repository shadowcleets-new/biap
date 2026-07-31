/**
 * @file AuthViewModelTest.kt
 * @description Unit tests for AuthViewModel using JUnit4, Mockk, and Turbine
 *
 * Test Strategy:
 * - Form validation tests
 * - State flow emission tests
 * - Event emission tests
 * - Coroutine scope tests
 * - Error handling tests
 */

package com.sc.biap.ui.auth.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthViewModelTest {

    // #region Test Setup & Teardown

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockFirebaseUser: FirebaseUser
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock Firebase dependencies
        mockFirebaseAuth = mockk(relaxed = true)
        mockFirebaseUser = mockk(relaxed = true)

        every { mockFirebaseUser.uid } returns "test_uid_123"
        every { mockFirebaseUser.email } returns "test@example.com"
        every { mockFirebaseUser.displayName } returns "Test User"

        viewModel = AuthViewModel(mockFirebaseAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // #endregion

    // #region Email Validation Tests

    @Test
    fun `valid email format passes validation`() = testScope.runTest {
        viewModel.updateEmailInput("test@example.com")

        val state = viewModel.uiState.first()
        assertEquals("test@example.com", state.emailInput)
        assertEquals(null, state.emailError)
    }

    @Test
    fun `invalid email format shows error`() = testScope.runTest {
        viewModel.updateEmailInput("invalid-email")

        val state = viewModel.uiState.first()
        assertEquals("invalid-email", state.emailInput)
        assertTrue(state.emailError != null)
        assertEquals("Invalid email format", state.emailError)
    }

    @Test
    fun `empty email clears validation error`() = testScope.runTest {
        viewModel.updateEmailInput("invalid-email")
        viewModel.updateEmailInput("")

        val state = viewModel.uiState.first()
        assertEquals("", state.emailInput)
        assertEquals(null, state.emailError)
    }

    @Test
    fun `multiple invalid email formats are rejected`() = testScope.runTest {
        val invalidEmails = listOf(
            "test@",
            "@example.com",
            "test example@com",
            "test@.com",
            "test@example",
            "test@@example.com"
        )

        for (email in invalidEmails) {
            viewModel.updateEmailInput(email)
            val state = viewModel.uiState.first()
            assertTrue(state.emailError != null, "Email '$email' should be invalid")
        }
    }

    // #endregion

    // #region Password Validation Tests

    @Test
    fun `short password shows error`() = testScope.runTest {
        viewModel.updatePasswordInput("abc")

        val state = viewModel.uiState.first()
        assertEquals("abc", state.passwordInput)
        assertTrue(state.passwordError != null)
    }

    @Test
    fun `password without uppercase shows error`() = testScope.runTest {
        viewModel.updatePasswordInput("password123")

        val state = viewModel.uiState.first()
        assertTrue(state.passwordError != null)
        assertEquals("Password must contain uppercase letter", state.passwordError)
    }

    @Test
    fun `password without digit shows error`() = testScope.runTest {
        viewModel.updatePasswordInput("Password")

        val state = viewModel.uiState.first()
        assertTrue(state.passwordError != null)
        assertEquals("Password must contain number", state.passwordError)
    }

    @Test
    fun `valid password passes all validations`() = testScope.runTest {
        viewModel.updatePasswordInput("ValidPass123")

        val state = viewModel.uiState.first()
        assertEquals("ValidPass123", state.passwordInput)
        assertEquals(null, state.passwordError)
    }

    // #endregion

    // #region Phone Validation Tests

    @Test
    fun `valid Indian phone format passes validation`() = testScope.runTest {
        viewModel.updatePhoneInput("9876543210")

        val state = viewModel.uiState.first()
        assertEquals("9876543210", state.phoneInput)
        assertEquals(null, state.phoneError)
    }

    @Test
    fun `invalid phone format shows error`() = testScope.runTest {
        viewModel.updatePhoneInput("1234567890")

        val state = viewModel.uiState.first()
        assertTrue(state.phoneError != null)
    }

    @Test
    fun `phone number with less than 10 digits is rejected`() = testScope.runTest {
        viewModel.updatePhoneInput("987654321")

        val state = viewModel.uiState.first()
        assertTrue(state.phoneError != null)
    }

    // #endregion

    // #region OTP Input Tests

    @Test
    fun `OTP input accepts only 6 digits`() = testScope.runTest {
        viewModel.updateOtpInput("123456")

        val state = viewModel.uiState.first()
        assertEquals("123456", state.otpInput)
    }

    @Test
    fun `OTP input truncates beyond 6 digits`() = testScope.runTest {
        viewModel.updateOtpInput("1234567890")

        val state = viewModel.uiState.first()
        assertEquals("123456", state.otpInput)
    }

    @Test
    fun `OTP input rejects non-numeric characters`() = testScope.runTest {
        viewModel.updateOtpInput("12A45B")

        val state = viewModel.uiState.first()
        assertEquals("1245", state.otpInput)
    }

    @Test
    fun `OTP input clears error on update`() = testScope.runTest {
        viewModel.updateOtpInput("invalid")
        viewModel.updateOtpInput("123456")

        val state = viewModel.uiState.first()
        assertEquals(null, state.otpError)
    }

    // #endregion

    // #region Form Validation Tests

    @Test
    fun `form is invalid when empty`() = testScope.runTest {
        viewModel.setAuthFlow(AuthFlow.SIGN_IN)
        val state = viewModel.uiState.first()
        assertFalse(state.isFormValid)
    }

    @Test
    fun `sign in form is valid with email and password`() = testScope.runTest {
        viewModel.setAuthFlow(AuthFlow.SIGN_IN)
        viewModel.updateEmailInput("test@example.com")
        viewModel.updatePasswordInput("ValidPass123")

        val state = viewModel.uiState.first()
        assertTrue(state.isFormValid)
    }

    @Test
    fun `registration form requires all fields`() = testScope.runTest {
        viewModel.setAuthFlow(AuthFlow.REGISTER)
        viewModel.updateFullNameInput("Test User")
        viewModel.updateEmailInput("test@example.com")
        viewModel.updatePasswordInput("ValidPass123")
        viewModel.updateConfirmPasswordInput("ValidPass123")

        val state = viewModel.uiState.first()
        assertTrue(state.isFormValid)
    }

    @Test
    fun `registration form is invalid with mismatched passwords`() = testScope.runTest {
        viewModel.setAuthFlow(AuthFlow.REGISTER)
        viewModel.updateFullNameInput("Test User")
        viewModel.updateEmailInput("test@example.com")
        viewModel.updatePasswordInput("ValidPass123")
        viewModel.updateConfirmPasswordInput("DifferentPass123")

        val state = viewModel.uiState.first()
        assertFalse(state.isFormValid)
    }

    // #endregion

    // #region Password Visibility Tests

    @Test
    fun `toggle password visibility works`() = testScope.runTest {
        val initialState = viewModel.uiState.first()
        assertFalse(initialState.isPasswordVisible)

        viewModel.togglePasswordVisibility()
        val afterToggle = viewModel.uiState.first()
        assertTrue(afterToggle.isPasswordVisible)

        viewModel.togglePasswordVisibility()
        val afterToggleAgain = viewModel.uiState.first()
        assertFalse(afterToggleAgain.isPasswordVisible)
    }

    @Test
    fun `toggle confirm password visibility works`() = testScope.runTest {
        val initialState = viewModel.uiState.first()
        assertFalse(initialState.isConfirmPasswordVisible)

        viewModel.toggleConfirmPasswordVisibility()
        val afterToggle = viewModel.uiState.first()
        assertTrue(afterToggle.isConfirmPasswordVisible)
    }

    // #endregion

    // #region Form Clearing Tests

    @Test
    fun `clear form resets all fields`() = testScope.runTest {
        viewModel.updateEmailInput("test@example.com")
        viewModel.updatePasswordInput("ValidPass123")
        viewModel.updatePhoneInput("9876543210")

        viewModel.clearForm()

        val state = viewModel.uiState.first()
        assertEquals("", state.emailInput)
        assertEquals("", state.passwordInput)
        assertEquals("", state.phoneInput)
        assertEquals(null, state.emailError)
        assertEquals(null, state.passwordError)
        assertEquals(null, state.error)
    }

    @Test
    fun `clear error removes error state`() = testScope.runTest {
        viewModel._uiState.update { it.copy(error = "Test error") }

        viewModel.clearError()

        val state = viewModel.uiState.first()
        assertEquals(null, state.error)
        assertFalse(state.showError)
    }

    // #endregion

    // #region State Flow Tests

    @Test
    fun `state flow emits initial state`() = testScope.runTest {
        val state = viewModel.uiState.first()
        assertEquals(AuthFlow.IDLE, state.authFlow)
        assertFalse(state.isLoading)
        assertFalse(state.isAuthenticated)
    }

    @Test
    fun `state flow updates are collected correctly`() = testScope.runTest {
        viewModel.updateEmailInput("test@example.com")

        val state = viewModel.uiState.first()
        assertEquals("test@example.com", state.emailInput)
    }

    @Test
    fun `multiple state updates accumulate correctly`() = testScope.runTest {
        viewModel.updateEmailInput("test@example.com")
        viewModel.updatePasswordInput("ValidPass123")
        viewModel.updatePhoneInput("9876543210")

        val state = viewModel.uiState.first()
        assertEquals("test@example.com", state.emailInput)
        assertEquals("ValidPass123", state.passwordInput)
        assertEquals("9876543210", state.phoneInput)
    }

    // #endregion

    // #region Event Flow Tests

    @Test
    fun `events are emitted to channel`() = testScope.runTest {
        val events = mutableListOf<AuthEvent>()

        launch {
            viewModel.eventFlow.collect { event ->
                events.add(event)
            }
        }

        viewModel._eventChannel.send(
            AuthEvent.AuthError("Test error", "TEST_CODE")
        )

        advanceUntilIdle()

        assertEquals(1, events.size)
        assertTrue(events[0] is AuthEvent.AuthError)
    }

    @Test
    fun `multiple events are emitted sequentially`() = testScope.runTest {
        val events = mutableListOf<AuthEvent>()

        launch {
            viewModel.eventFlow.collect { event ->
                events.add(event)
            }
        }

        viewModel._eventChannel.send(AuthEvent.SignInSuccess("uid", "email@test.com"))
        viewModel._eventChannel.send(AuthEvent.NavigateToHome)

        advanceUntilIdle()

        assertEquals(2, events.size)
        assertTrue(events[0] is AuthEvent.SignInSuccess)
        assertTrue(events[1] is AuthEvent.NavigateToHome)
    }

    // #endregion

    // #region OTP Countdown Timer Tests

    @Test
    fun `OTP countdown starts correctly`() = testScope.runTest {
        viewModel.sendOtp("test@example.com", OtpMethod.EMAIL)

        advanceTimeBy(100)

        val state = viewModel.uiState.first()
        assertEquals(OtpMethod.EMAIL, state.otpMethod)
        assertEquals("test@example.com", state.otpSentTo)
    }

    // #endregion

    // #region Edge Case Tests

    @Test
    fun `white space in email is detected as invalid`() = testScope.runTest {
        viewModel.updateEmailInput("test @example.com")

        val state = viewModel.uiState.first()
        assertTrue(state.emailError != null)
    }

    @Test
    fun `special characters in password are allowed`() = testScope.runTest {
        viewModel.updatePasswordInput("ValidPass123!@#")

        val state = viewModel.uiState.first()
        assertEquals(null, state.passwordError)
    }

    @Test
    fun `very long inputs are handled gracefully`() = testScope.runTest {
        val longEmail = "a".repeat(100) + "@example.com"
        viewModel.updateEmailInput(longEmail)

        val state = viewModel.uiState.first()
        assertEquals(longEmail, state.emailInput)
    }

    // #endregion

    // #region Computed Properties Tests

    @Test
    fun `hasNoErrors computed property works correctly`() = testScope.runTest {
        val initialState = viewModel.uiState.first()
        assertTrue(initialState.hasNoErrors)

        viewModel.updateEmailInput("invalid")
        val withError = viewModel.uiState.first()
        assertFalse(withError.hasNoErrors)
    }

    @Test
    fun `isAnyFieldFocused detects non-empty inputs`() = testScope.runTest {
        val initialState = viewModel.uiState.first()
        assertFalse(initialState.isAnyFieldFocused)

        viewModel.updateEmailInput("test@example.com")
        val withInput = viewModel.uiState.first()
        assertTrue(withInput.isAnyFieldFocused)
    }

    // #endregion
}
