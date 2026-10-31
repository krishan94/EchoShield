package com.echoshield.core.di

import com.echoshield.data.remote.AuthRemoteDataSource
import com.echoshield.data.remote.FirebaseAuthDataSource
import com.echoshield.data.repository.AuthRepositoryImpl
import com.echoshield.domain.repository.AuthRepository

/**
 * Lightweight Dependency Container providing application-scoped singleton instances.
 * Clean, zero-reflection, testable architecture.
 */
object AppContainer {

    @Volatile
    private var customAuthRepository: AuthRepository? = null

    /**
     * Singleton instance of [AuthRepository]. Allows override for automated testing.
     */
    val authRepository: AuthRepository
        get() = customAuthRepository ?: synchronized(this) {
            customAuthRepository ?: createDefaultAuthRepository().also {
                customAuthRepository = it
            }
        }

    private fun createDefaultAuthRepository(): AuthRepository {
        val remoteDataSource: AuthRemoteDataSource = FirebaseAuthDataSource()
        return AuthRepositoryImpl(remoteDataSource)
    }

    /**
     * Allows test suites to inject mock or fake repository implementations.
     */
    fun setAuthRepositoryForTesting(repository: AuthRepository?) {
        customAuthRepository = repository
    }
}
