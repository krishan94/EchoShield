package com.echoshield.data.repository

import com.echoshield.data.remote.AuthRemoteDataSource
import com.echoshield.domain.model.AuthResult
import com.echoshield.domain.model.AuthUser
import com.echoshield.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Concrete implementation of [AuthRepository] mediating between domain layers and [AuthRemoteDataSource].
 */
class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource
) : AuthRepository {

    override val currentUser: StateFlow<AuthUser?> = remoteDataSource.currentUserFlow

    override suspend fun getCurrentUser(): AuthUser? {
        return remoteDataSource.currentUserFlow.value
    }

    override fun isUserLoggedIn(): Boolean {
        return remoteDataSource.currentUserFlow.value != null
    }

    override suspend fun signup(
        name: String,
        email: String,
        password: String
    ): AuthResult<AuthUser> {
        return remoteDataSource.createUserWithEmailAndPassword(name, email, password)
    }

    override suspend fun login(
        email: String,
        password: String
    ): AuthResult<AuthUser> {
        return remoteDataSource.signInWithEmailAndPassword(email, password)
    }

    override suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        return remoteDataSource.sendPasswordResetEmail(email)
    }

    override suspend fun sendEmailVerification(): AuthResult<Unit> {
        return remoteDataSource.sendEmailVerification()
    }

    override suspend fun reloadUser(): AuthResult<AuthUser> {
        return remoteDataSource.reloadCurrentUser()
    }

    override suspend fun logout(): AuthResult<Unit> {
        return remoteDataSource.signOut()
    }

    override suspend fun getIdToken(forceRefresh: Boolean): AuthResult<String> {
        return remoteDataSource.getIdToken(forceRefresh)
    }
}

