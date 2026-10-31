package com.echoshield.authentication

import com.echoshield.domain.model.AuthResult
import com.echoshield.domain.model.AuthUser
import com.echoshield.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake implementation of [AuthRepository] for deterministic, hermetic unit testing.
 */
class FakeAuthRepository : AuthRepository {
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    var shouldFailSignup: Boolean = false
    var shouldFailLogin: Boolean = false
    var shouldFailPasswordReset: Boolean = false
    var reloadVerificationStatus: Boolean = false

    override suspend fun getCurrentUser(): AuthUser? = _currentUser.value

    override fun isUserLoggedIn(): Boolean = _currentUser.value != null

    override suspend fun signup(name: String, email: String, password: String): AuthResult<AuthUser> {
        if (shouldFailSignup) {
            return AuthResult.Error("An account with this email address already exists. Please sign in instead.")
        }
        val user = AuthUser(
            uid = "uid_${System.currentTimeMillis()}",
            email = email,
            displayName = name,
            isEmailVerified = false
        )
        _currentUser.value = user
        return AuthResult.Success(user)
    }

    override suspend fun login(email: String, password: String): AuthResult<AuthUser> {
        if (shouldFailLogin) {
            return AuthResult.Error("Incorrect password. Please verify your credentials.")
        }
        val user = AuthUser(
            uid = "uid_existing",
            email = email,
            displayName = "Family Guardian",
            isEmailVerified = reloadVerificationStatus
        )
        _currentUser.value = user
        return AuthResult.Success(user)
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        if (shouldFailPasswordReset) {
            return AuthResult.Error("No EchoShield account found matching this email address.")
        }
        return AuthResult.Success(Unit)
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        if (_currentUser.value == null) {
            return AuthResult.Error("No active session found.")
        }
        return AuthResult.Success(Unit)
    }

    override suspend fun reloadUser(): AuthResult<AuthUser> {
        val current = _currentUser.value ?: return AuthResult.Error("No session to reload.")
        val updated = current.copy(isEmailVerified = reloadVerificationStatus)
        _currentUser.value = updated
        return AuthResult.Success(updated)
    }

    override suspend fun logout(): AuthResult<Unit> {
        _currentUser.value = null
        return AuthResult.Success(Unit)
    }

    override suspend fun getIdToken(forceRefresh: Boolean): AuthResult<String> {
        return if (_currentUser.value != null) {
            AuthResult.Success("mock_firebase_id_token_jwt")
        } else {
            AuthResult.Error("No active user session.")
        }
    }

    fun setInitialUser(user: AuthUser?) {
        _currentUser.value = user
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = AuthViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAuthUserDomainModel() {
        val user = AuthUser(
            uid = "user_abc123",
            email = "guardian@echoshield.family",
            displayName = "Alex Carter",
            isEmailVerified = true
        )
        assertEquals("user_abc123", user.uid)
        assertEquals("guardian@echoshield.family", user.email)
        assertEquals("Alex Carter", user.displayName)
        assertTrue(user.isEmailVerified)
        assertNull(user.photoUrl)
    }

    @Test
    fun testAuthResultSemantics() {
        val success = AuthResult.Success("verified_token")
        assertTrue(success.isSuccess)
        assertFalse(success.isError)
        assertEquals("verified_token", success.getOrNull())
        assertNull(success.errorOrNull())

        val error = AuthResult.Error("Network failure", errorCode = "ERROR_NETWORK")
        assertFalse(error.isSuccess)
        assertTrue(error.isError)
        assertNull(error.getOrNull())
        assertEquals("Network failure", error.errorOrNull()?.message)
        assertEquals("ERROR_NETWORK", error.errorOrNull()?.errorCode)
    }

    @Test
    fun testInitialStateUnauthenticated() = testScope.runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertFalse(state.isEmailVerified)
        assertNull(state.user)
        assertNull(state.errorMessage)
    }

    @Test
    fun testSignupValidationFailsOnShortPassword() = testScope.runTest {
        var successCalled = false
        viewModel.signup(
            name = "Test User",
            email = "user@echoshield.family",
            password = "123", // too short
            confirmPassword = "123",
            termsAccepted = true,
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals("Password must be at least 8 characters long.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSignupValidationFailsOnMismatchedPasswords() = testScope.runTest {
        var successCalled = false
        viewModel.signup(
            name = "Test User",
            email = "user@echoshield.family",
            password = "Password123#",
            confirmPassword = "DifferentPassword123#",
            termsAccepted = true,
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals("Passwords do not match.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSignupValidationFailsWithoutTermsAcceptance() = testScope.runTest {
        var successCalled = false
        viewModel.signup(
            name = "Test User",
            email = "user@echoshield.family",
            password = "Password123#",
            confirmPassword = "Password123#",
            termsAccepted = false,
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertEquals("You must agree to the Terms & Biometric Privacy Policy.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSuccessfulSignup() = testScope.runTest {
        var successCalled = false
        viewModel.signup(
            name = "Sarah Henderson",
            email = "sarah.h@echoshield.family",
            password = "SecurePassword2026!",
            confirmPassword = "SecurePassword2026!",
            termsAccepted = true,
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertTrue(successCalled)
        val state = viewModel.uiState.value
        assertTrue(state.isAuthenticated)
        assertFalse(state.isEmailVerified) // Initially unverified
        assertNotNull(state.user)
        assertEquals("Sarah Henderson", state.user?.displayName)
        assertEquals("sarah.h@echoshield.family", state.user?.email)
        assertNotNull(state.successMessage)
    }

    @Test
    fun testFailedSignupFromRepository() = testScope.runTest {
        fakeRepository.shouldFailSignup = true
        var successCalled = false
        viewModel.signup(
            name = "Sarah Henderson",
            email = "sarah.h@echoshield.family",
            password = "SecurePassword2026!",
            confirmPassword = "SecurePassword2026!",
            termsAccepted = true,
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("already exists"))
    }

    @Test
    fun testLoginSuccessWithVerifiedEmail() = testScope.runTest {
        fakeRepository.reloadVerificationStatus = true
        var successCalled = false
        var unverifiedCalled = false

        viewModel.login(
            email = "verified@echoshield.family",
            password = "ValidPassword123",
            onSuccess = { successCalled = true },
            onUnverifiedEmail = { unverifiedCalled = true }
        )
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(unverifiedCalled)
        val state = viewModel.uiState.value
        assertTrue(state.isAuthenticated)
        assertTrue(state.isEmailVerified)
    }

    @Test
    fun testLoginSuccessWithUnverifiedEmailNavigatesToVerification() = testScope.runTest {
        fakeRepository.reloadVerificationStatus = false
        var successCalled = false
        var unverifiedCalled = false

        viewModel.login(
            email = "unverified@echoshield.family",
            password = "ValidPassword123",
            onSuccess = { successCalled = true },
            onUnverifiedEmail = { unverifiedCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertTrue(unverifiedCalled)
        val state = viewModel.uiState.value
        assertTrue(state.isAuthenticated)
        assertFalse(state.isEmailVerified)
    }

    @Test
    fun testLoginFailureSurfacesErrorMessage() = testScope.runTest {
        fakeRepository.shouldFailLogin = true
        var successCalled = false
        var unverifiedCalled = false

        viewModel.login(
            email = "wrong@echoshield.family",
            password = "WrongPassword",
            onSuccess = { successCalled = true },
            onUnverifiedEmail = { unverifiedCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertFalse(unverifiedCalled)
        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertEquals("Incorrect password. Please verify your credentials.", state.errorMessage)
    }

    @Test
    fun testCheckEmailVerificationTransitionsState() = testScope.runTest {
        // First log in unverified
        fakeRepository.reloadVerificationStatus = false
        viewModel.login(
            email = "pending@echoshield.family",
            password = "Password123",
            onSuccess = {},
            onUnverifiedEmail = {}
        )
        advanceUntilIdle()

        // User is unverified, check status
        var verifiedCallbackTriggered = false
        viewModel.checkEmailVerification { verifiedCallbackTriggered = true }
        advanceUntilIdle()
        assertFalse(verifiedCallbackTriggered)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Now user clicks verification link in their email
        fakeRepository.reloadVerificationStatus = true
        viewModel.checkEmailVerification { verifiedCallbackTriggered = true }
        advanceUntilIdle()

        assertTrue(verifiedCallbackTriggered)
        assertTrue(viewModel.uiState.value.isEmailVerified)
        assertEquals("Email successfully verified!", viewModel.uiState.value.successMessage)
    }

    @Test
    fun testPasswordResetFlow() = testScope.runTest {
        var resetDispatched = false
        viewModel.sendPasswordReset(
            email = "forgot@echoshield.family",
            onSuccess = { resetDispatched = true }
        )
        advanceUntilIdle()

        assertTrue(resetDispatched)
        assertNotNull(viewModel.uiState.value.successMessage)
    }

    @Test
    fun testLogoutClearsSession() = testScope.runTest {
        fakeRepository.reloadVerificationStatus = true
        viewModel.login(
            email = "active@echoshield.family",
            password = "Password123",
            onSuccess = {},
            onUnverifiedEmail = {}
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAuthenticated)

        var logoutDone = false
        viewModel.logout { logoutDone = true }
        advanceUntilIdle()

        assertTrue(logoutDone)
        val state = viewModel.uiState.value
        assertFalse(state.isAuthenticated)
        assertFalse(state.isEmailVerified)
        assertNull(state.user)
    }
}

