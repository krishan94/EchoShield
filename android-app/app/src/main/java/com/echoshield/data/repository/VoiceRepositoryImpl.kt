package com.echoshield.data.repository

import com.echoshield.data.remote.VoiceRemoteDataSource
import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.repository.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceRepositoryImpl(
    private val remoteDataSource: VoiceRemoteDataSource
) : VoiceRepository {

    private val _voiceProfile = MutableStateFlow<VoiceProfileInfo?>(null)
    override val voiceProfile: StateFlow<VoiceProfileInfo?> = _voiceProfile.asStateFlow()

    private val _isConsentGranted = MutableStateFlow(false)
    override val isConsentGranted: StateFlow<Boolean> = _isConsentGranted.asStateFlow()

    override suspend fun recordConsent(granted: Boolean): Result<VoiceConsentRecord> {
        return try {
            val record = remoteDataSource.recordConsent(granted)
            _isConsentGranted.value = (record.consentStatus == "granted")
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enrollVoiceSamples(sampleFiles: List<File>): Result<VoiceEnrollmentResult> {
        return try {
            val result = remoteDataSource.enrollVoice(sampleFiles)
            refreshVoiceProfile()
            // Clean up temporary local files after successful upload and processing
            for (file in sampleFiles) {
                try {
                    file.delete()
                } catch (_: Exception) {}
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshVoiceProfile(): Result<VoiceProfileInfo> {
        return try {
            val profile = remoteDataSource.getVoiceProfile()
            _voiceProfile.value = profile
            _isConsentGranted.value = (profile.consentStatus == "granted")
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteVoiceProfile(): Result<Unit> {
        return try {
            remoteDataSource.deleteVoiceProfile()
            _voiceProfile.value = null
            _isConsentGranted.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

