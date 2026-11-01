package com.echoshield.core.di

import android.content.Context
import com.echoshield.calling.CallSessionCoordinator
import com.echoshield.core.theme.ThemePreferences
import com.echoshield.data.remote.AuthRemoteDataSource
import com.echoshield.data.remote.CallingRemoteDataSource
import com.echoshield.data.remote.CallingRemoteDataSourceImpl
import com.echoshield.data.remote.FamilyRemoteDataSource
import com.echoshield.data.remote.FamilyRemoteDataSourceImpl
import com.echoshield.data.remote.FirebaseAuthDataSource
import com.echoshield.data.remote.VoiceRemoteDataSource
import com.echoshield.data.remote.VoiceRemoteDataSourceImpl
import com.echoshield.data.repository.AuthRepositoryImpl
import com.echoshield.data.repository.CallingRepositoryImpl
import com.echoshield.data.repository.FamilyRepositoryImpl
import com.echoshield.data.repository.VoiceRepositoryImpl
import com.echoshield.domain.repository.AuthRepository
import com.echoshield.domain.repository.CallingRepository
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

    @Volatile
    private var customCallingRepository: CallingRepository? = null

    @Volatile
    private var customCallSessionCoordinator: CallSessionCoordinator? = null

    @Volatile
    private var customThemePreferences: ThemePreferences? = null

    fun getThemePreferences(context: Context): ThemePreferences {
        return customThemePreferences ?: synchronized(this) {
            customThemePreferences ?: ThemePreferences(context.applicationContext).also {
                customThemePreferences = it
            }
        }
    }

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

    /**
     * Singleton instance of [CallingRepository]. Allows override for automated testing.
     */
    val callingRepository: CallingRepository
        get() = customCallingRepository ?: synchronized(this) {
            customCallingRepository ?: createDefaultCallingRepository().also {
                customCallingRepository = it
            }
        }

    /**
     * Singleton instance of [CallSessionCoordinator]. Manages active calls, notifications and WebRTC.
     */
    val callSessionCoordinator: CallSessionCoordinator
        get() = customCallSessionCoordinator ?: synchronized(this) {
            customCallSessionCoordinator ?: CallSessionCoordinator(
                callingRepository = callingRepository,
                voiceRepository = voiceRepository,
                authRepository = authRepository
            ).also {
                customCallSessionCoordinator = it
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

    private fun createDefaultCallingRepository(): CallingRepository {
        val remoteDataSource: CallingRemoteDataSource = CallingRemoteDataSourceImpl(authRepository)
        return CallingRepositoryImpl(remoteDataSource)
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

    fun setCallingRepositoryForTesting(repository: CallingRepository?) {
        customCallingRepository = repository
    }

    fun setCallSessionCoordinatorForTesting(coordinator: CallSessionCoordinator?) {
        customCallSessionCoordinator = coordinator
    }
}
