package com.echoshield.data.remote

import com.echoshield.domain.model.AuthResult
import com.echoshield.domain.model.AuthUser
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthEmailException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Interface contract for remote authentication data operations.
 * Decouples Firebase implementation details for testability.
 */
interface AuthRemoteDataSource {
    val currentUserFlow: StateFlow<AuthUser?>
    fun getCurrentFirebaseUser(): FirebaseUser?
    suspend fun createUserWithEmailAndPassword(name: String, email: String, password: String): AuthResult<AuthUser>
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult<AuthUser>
    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit>
    suspend fun sendEmailVerification(): AuthResult<Unit>
    suspend fun reloadCurrentUser(): AuthResult<AuthUser>
    suspend fun signOut(): AuthResult<Unit>
    suspend fun getIdToken(forceRefresh: Boolean = false): AuthResult<String>
}

/**
 * Production implementation of [AuthRemoteDataSource] backed by Google Firebase Authentication SDK.
 */
class FirebaseAuthDataSource(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRemoteDataSource {

    private val _currentUserFlow = MutableStateFlow(firebaseAuth.currentUser?.toAuthUser())
    override val currentUserFlow: StateFlow<AuthUser?> = _currentUserFlow.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        _currentUserFlow.value = auth.currentUser?.toAuthUser()
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    override fun getCurrentFirebaseUser(): FirebaseUser? = firebaseAuth.currentUser

    override suspend fun createUserWithEmailAndPassword(
        name: String,
        email: String,
        password: String
    ): AuthResult<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val trimmedName = name.trim()

            val authResult = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user
                ?: return@withContext AuthResult.Error("Account creation failed: No user record returned.")

            // Update user profile display name
            if (trimmedName.isNotBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()
            }

            // Dispatch cryptographic email verification
            try {
                firebaseUser.sendEmailVerification().await()
            } catch (e: Exception) {
                // Account created, but initial verification dispatch failed
                // Non-fatal, user can resend later
            }

            // Reload user to ensure profile state is synchronized
            firebaseUser.reload().await()
            val freshUser = firebaseAuth.currentUser ?: firebaseUser
            val authUser = freshUser.toAuthUser()
            _currentUserFlow.value = authUser

            AuthResult.Success(authUser)
        } catch (e: Exception) {
            handleFirebaseException(e)
        }
    }

    override suspend fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): AuthResult<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            val authResult = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user
                ?: return@withContext AuthResult.Error("Sign-in failed: No user record returned.")

            val authUser = firebaseUser.toAuthUser()
            _currentUserFlow.value = authUser

            AuthResult.Success(authUser)
        } catch (e: Exception) {
            handleFirebaseException(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim()
            firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            handleFirebaseException(e)
        }
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth.currentUser
                ?: return@withContext AuthResult.Error("No active session found. Please sign in to verify email.")
            user.sendEmailVerification().await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            handleFirebaseException(e)
        }
    }

    override suspend fun reloadCurrentUser(): AuthResult<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth.currentUser
                ?: return@withContext AuthResult.Error("No active user session to reload.")
            user.reload().await()
            val updatedUser = firebaseAuth.currentUser ?: user
            val authUser = updatedUser.toAuthUser()
            _currentUserFlow.value = authUser
            AuthResult.Success(authUser)
        } catch (e: Exception) {
            handleFirebaseException(e)
        }
    }

    override suspend fun signOut(): AuthResult<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.signOut()
            _currentUserFlow.value = null
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error("Failed to sign out: ${e.localizedMessage ?: "Unknown error"}", cause = e)
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): AuthResult<String> = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth.currentUser
                ?: return@withContext AuthResult.Error("Authentication token unavailable: No active user session.")
            val result = user.getIdToken(forceRefresh).await()
            val token = result.token
                ?: return@withContext AuthResult.Error("Firebase returned an empty ID token.")
            AuthResult.Success(token)
        } catch (e: Exception) {
            handleFirebaseException(e)
        }
    }

    private fun <T> handleFirebaseException(e: Exception): AuthResult<T> {
        val message = when (e) {
            is FirebaseAuthInvalidUserException -> {
                when (e.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "No EchoShield account found matching this email address."
                    "ERROR_USER_DISABLED" -> "This family account has been disabled. Contact family administrator."
                    else -> "Account does not exist or has been disabled."
                }
            }
            is FirebaseAuthInvalidCredentialsException -> {
                when (e.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "The email address format is invalid."
                    "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please verify your credentials."
                    else -> "Invalid credentials provided. Please check your email and password."
                }
            }
            is FirebaseAuthUserCollisionException -> {
                "An account with this email address already exists. Please sign in instead."
            }
            is FirebaseAuthWeakPasswordException -> {
                "The password is too weak. Please use at least 8 characters with letters and numbers."
            }
            is FirebaseNetworkException -> {
                "Network connection unreachable. Please check your internet connection."
            }
            is FirebaseAuthRecentLoginRequiredException -> {
                "This operation requires recent authentication. Please log in again."
            }
            is FirebaseAuthActionCodeException -> {
                "The verification link has expired or has already been used."
            }
            is FirebaseAuthException -> {
                when (e.errorCode) {
                    "ERROR_TOO_MANY_REQUESTS" -> "Too many failed attempts. Security cooldown active, please wait a moment."
                    "ERROR_OPERATION_NOT_ALLOWED" -> "Email/Password authentication is not enabled on this project."
                    else -> e.localizedMessage ?: "Authentication service error."
                }
            }
            else -> e.localizedMessage ?: "An unexpected error occurred during authentication."
        }

        val errorCode = (e as? FirebaseAuthException)?.errorCode
        return AuthResult.Error(message = message, errorCode = errorCode, cause = e)
    }
}

/**
 * Mapper extension converting Firebase SDK user to EchoShield domain model.
 */
fun FirebaseUser.toAuthUser(): AuthUser {
    return AuthUser(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName,
        isEmailVerified = isEmailVerified,
        photoUrl = photoUrl?.toString()
    )
}
