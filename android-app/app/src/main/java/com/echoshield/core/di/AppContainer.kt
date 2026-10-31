package com.echoshield.core.di

import com.echoshield.data.remote.AuthRemoteDataSource
import com.echoshield.data.remote.FamilyRemoteDataSource
import com.echoshield.data.remote.FamilyRemoteDataSourceImpl
import com.echoshield.data.remote.FirebaseAuthDataSource
import com.echoshield.data.remote.VoiceRemoteDataSource
import com.echoshield.data.remote.VoiceRemoteDataSourceImpl
import com.echoshield.data.repository.AuthRepositoryImpl
import com.echoshield.data.repository.FamilyRepositoryImpl
import com.echoshield.data.repository.VoiceRepositoryImpl
import com.echoshield.domain.repository.AuthRepository
import com.echoshield.domain.repository.FamilyRepository
import com.echoshield.domain.repository.VoiceRepository

/**
 * Lightweight Dependency Container providing application-scoped singleton instances.
 * Clean, zero-reflection, testable architecture.
 */
object AppContainer {

    @Volatile
    private var customAuthRepository: AuthRepository? = null

    @Volatile
    private var customFamilyRepository: FamilyRepository? = null

    @Volatile
    private var customVoiceRepository: VoiceRepository? = null

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

    /**
     * Singleton instance of [VoiceRepository]. Allows override for automated testing.
     */
    val voiceRepository: VoiceRepository
        get() = customVoiceRepository ?: synchronized(this) {
            customVoiceRepository ?: createDefaultVoiceRepository().also {
                customVoiceRepository = it
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

    private fun createDefaultVoiceRepository(): VoiceRepository {
        val remoteDataSource: VoiceRemoteDataSource = VoiceRemoteDataSourceImpl(authRepository)
        return VoiceRepositoryImpl(remoteDataSource)
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

    fun setVoiceRepositoryForTesting(repository: VoiceRepository?) {
        customVoiceRepository = repository
    }
}
