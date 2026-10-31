package com.echoshield.core.di

import com.echoshield.data.remote.AuthRemoteDataSource
import com.echoshield.data.remote.FamilyRemoteDataSource
import com.echoshield.data.remote.FamilyRemoteDataSourceImpl
import com.echoshield.data.remote.FirebaseAuthDataSource
import com.echoshield.data.repository.AuthRepositoryImpl
import com.echoshield.data.repository.FamilyRepositoryImpl
import com.echoshield.domain.repository.AuthRepository
import com.echoshield.domain.repository.FamilyRepository

/**
 * Lightweight Dependency Container providing application-scoped singleton instances.
 * Clean, zero-reflection, testable architecture.
 */
object AppContainer {

    @Volatile
    private var customAuthRepository: AuthRepository? = null

    @Volatile
    private var customFamilyRepository: FamilyRepository? = null

    /**
     * Singleton instance of [AuthRepository]. Allows override for automated testing.
     */
    val authRepository: AuthRepository
        get() = customAuthRepository ?: synchronized(this) {
            customAuthRepository ?: createDefaultAuthRepository().also {
                customAuthRepository = it
            }
        }

    /**
     * Singleton instance of [FamilyRepository]. Allows override for automated testing.
     */
    val familyRepository: FamilyRepository
        get() = customFamilyRepository ?: synchronized(this) {
            customFamilyRepository ?: createDefaultFamilyRepository().also {
                customFamilyRepository = it
            }
        }

    private fun createDefaultAuthRepository(): AuthRepository {
        val remoteDataSource: AuthRemoteDataSource = FirebaseAuthDataSource()
        return AuthRepositoryImpl(remoteDataSource)
    }

    private fun createDefaultFamilyRepository(): FamilyRepository {
        val remoteDataSource: FamilyRemoteDataSource = FamilyRemoteDataSourceImpl(authRepository)
        return FamilyRepositoryImpl(remoteDataSource)
    }

    /**
     * Allows test suites to inject mock or fake repository implementations.
     */
    fun setAuthRepositoryForTesting(repository: AuthRepository?) {
        customAuthRepository = repository
    }

    fun setFamilyRepositoryForTesting(repository: FamilyRepository?) {
        customFamilyRepository = repository
    }
}
