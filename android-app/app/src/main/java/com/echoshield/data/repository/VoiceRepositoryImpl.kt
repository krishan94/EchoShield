package com.echoshield.data.repository

import com.echoshield.data.remote.VoiceRemoteDataSource
import com.echoshield.domain.model.AntiSpoofAnalysisResult
import com.echoshield.domain.model.CombinedSecurityAnalysisResult
import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.model.VoiceVerificationResult
import com.echoshield.domain.model.VerificationHistoryRecord
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
        if (sampleFiles.size < 3) {
            return Result.failure(IllegalArgumentException("At least 3 audio samples are required for voice enrollment."))
        }

        // Pre-validate all files exist, are readable, and non-empty before initiating upload
        for ((index, file) in sampleFiles.withIndex()) {
            if (!file.exists()) {
                return Result.failure(java.io.FileNotFoundException("Sample #${index + 1} file not found: ${file.name}"))
            }
            if (!file.canRead()) {
                return Result.failure(java.io.IOException("Sample #${index + 1} file cannot be read: ${file.name}"))
            }
            if (file.length() <= 44) {
                return Result.failure(java.io.IOException("Sample #${index + 1} file has invalid or empty audio data (${file.length()} bytes)"))
            }
        }

        return try {
            val result = remoteDataSource.enrollVoice(sampleFiles)
            refreshVoiceProfile()
            // Clean up temporary local files ONLY after successful upload and server processing
            for (file in sampleFiles) {
                try {
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            Result.success(result)
        } catch (e: Exception) {
            // Crucial: preserve local audio files on network/server error so user can retry
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

    override suspend fun verifyVoiceSample(
        memberId: String,
        audioFile: File
    ): Result<VoiceVerificationResult> {
        return try {
            val result = remoteDataSource.verifyVoice(memberId, audioFile)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun analyzeSpoofSample(
        audioFile: File,
        requestId: String?
    ): Result<AntiSpoofAnalysisResult> {
        return try {
            val result = remoteDataSource.analyzeSpoof(audioFile, requestId)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun analyzeSecurity(
        memberId: String,
        audioFile: File,
        requestId: String?
    ): Result<CombinedSecurityAnalysisResult> {
        return try {
            val result = remoteDataSource.analyzeSecurity(memberId, audioFile, requestId)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVerificationHistory(): Result<List<VerificationHistoryRecord>> {
        return try {
            val history = remoteDataSource.getVerificationHistory()
            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearVerificationHistory(): Result<Int> {
        return try {
            val deletedCount = remoteDataSource.clearVerificationHistory()
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
