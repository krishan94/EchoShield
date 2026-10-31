package com.echoshield.domain.repository

import com.echoshield.domain.model.AuthResult
import com.echoshield.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for authentication operations in EchoShield.
 * Follows Clean Architecture boundaries: Domain depends only on pure models.
 */
interface AuthRepository {
    /**
     * Hot observable flow reflecting the current Firebase authentication session state.
     */
    val currentUser: StateFlow<AuthUser?>

    /**
     * Synchronously returns the currently cached authenticated user or null.
     */
    suspend fun getCurrentUser(): AuthUser?

    /**
     * Checks if a user is currently signed in.
     */
    fun isUserLoggedIn(): Boolean

    /**
     * Registers a new family member account with email, password, and display name.
     * Automatically dispatches a verification email upon successful creation.
     */
    suspend fun signup(name: String, email: String, password: String): AuthResult<AuthUser>

    /**
     * Signs in an existing family member using credentials.
     */
    suspend fun login(email: String, password: String): AuthResult<AuthUser>

    /**
     * Dispatches an encrypted Firebase password reset email.
     */
    suspend fun sendPasswordReset(email: String): AuthResult<Unit>

    /**
     * Re-dispatches a verification email to the current unverified user.
     */
    suspend fun sendEmailVerification(): AuthResult<Unit>

    /**
     * Reloads user metadata from Firebase servers to verify if email verification succeeded.
     */
    suspend fun reloadUser(): AuthResult<AuthUser>

    /**
     * Securely invalidates local Firebase credentials and session tokens.
     */
    suspend fun logout(): AuthResult<Unit>

    /**
     * Retrieves the Firebase ID token for authenticating against the EchoShield backend.
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): AuthResult<String>
}

