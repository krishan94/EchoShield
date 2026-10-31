package com.echoshield.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echoshield.core.di.AppContainer
import com.echoshield.domain.model.AuthResult
import com.echoshield.domain.model.AuthUser
import com.echoshield.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State representing the active user session and UI transaction progress.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val isAuthenticated: Boolean = false,
    val isEmailVerified: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

/**
 * Architecture-compliant ViewModel managing Firebase Authentication state and user actions.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Collect real-time authentication session changes
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { current ->
                    current.copy(
                        user = user,
                        isAuthenticated = user != null,
                        isEmailVerified = user?.isEmailVerified == true
                    )
                }
            }
        }
    }

    /**
     * Authenticates existing user with email and password.
     */
    fun login(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onUnverifiedEmail: () -> Unit
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid family account email.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.login(trimmedEmail, password)) {
                is AuthResult.Success -> {
                    val user = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            isAuthenticated = true,
                            isEmailVerified = user.isEmailVerified
                        )
                    }
                    if (user.isEmailVerified) {
                        onSuccess()
                    } else {
                        onUnverifiedEmail()
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Creates a new family account and triggers initial verification email dispatch.
     */
    fun signup(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        termsAccepted: Boolean,
        onSuccess: () -> Unit
    ) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Full name is required.") }
            return
        }
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please provide a valid family email address.") }
            return
        }
        if (password.length < 8) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 8 characters long.") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match.") }
            return
        }
        if (!termsAccepted) {
            _uiState.update { it.copy(errorMessage = "You must agree to the Terms & Biometric Privacy Policy.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.signup(trimmedName, trimmedEmail, password)) {
                is AuthResult.Success -> {
                    val user = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            isAuthenticated = true,
                            isEmailVerified = user.isEmailVerified,
                            successMessage = "Account created. A verification link has been dispatched to ${user.email}."
                        )
                    }
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Sends password recovery email.
     */
    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            _uiState.update { it.copy(errorMessage = "Please enter your registered family email address.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.sendPasswordReset(trimmedEmail)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Recovery instructions dispatched. Please check your inbox and spam folder."
                        )
                    }
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Dispatches a fresh email verification link.
     */
    fun sendEmailVerification(onSent: (() -> Unit)? = null) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "A new verification email has been dispatched. Please check your inbox."
                        )
                    }
                    onSent?.invoke()
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Synchronizes current user metadata with Firebase servers to detect email confirmation.
     */
    fun checkEmailVerification(onVerified: () -> Unit) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.reloadUser()) {
                is AuthResult.Success -> {
                    val user = result.data
                    val isVerified = user.isEmailVerified
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            isEmailVerified = isVerified,
                            successMessage = if (isVerified) "Email successfully verified!" else null,
                            errorMessage = if (!isVerified) "Email is not yet verified. Please click the link in your email and tap refresh." else null
                        )
                    }
                    if (isVerified) {
                        onVerified()
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Terminates the active session and clears local credentials.
     */
    fun logout(onLoggedOut: () -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update {
                AuthUiState(
                    isLoading = false,
                    user = null,
                    isAuthenticated = false,
                    isEmailVerified = false
                )
            }
            onLoggedOut()
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel(AppContainer.authRepository)
            }
        }
    }
}

