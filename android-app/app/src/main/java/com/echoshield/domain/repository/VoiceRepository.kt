package com.echoshield.domain.repository

import com.echoshield.domain.model.AntiSpoofAnalysisResult
import com.echoshield.domain.model.CombinedSecurityAnalysisResult
import com.echoshield.domain.model.VerificationHistoryRecord
import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import com.echoshield.domain.model.VoiceVerificationResult
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface VoiceRepository {
    val voiceProfile: StateFlow<VoiceProfileInfo?>
    val isConsentGranted: StateFlow<Boolean>

    suspend fun recordConsent(granted: Boolean): Result<VoiceConsentRecord>
    suspend fun enrollVoiceSamples(sampleFiles: List<File>): Result<VoiceEnrollmentResult>
    suspend fun refreshVoiceProfile(): Result<VoiceProfileInfo>
    suspend fun deleteVoiceProfile(): Result<Unit>

    suspend fun verifyVoiceSample(memberId: String, audioFile: File): Result<VoiceVerificationResult>
    suspend fun analyzeSpoofSample(audioFile: File, requestId: String? = null): Result<AntiSpoofAnalysisResult>
    suspend fun analyzeSecurity(memberId: String, audioFile: File, requestId: String? = null): Result<CombinedSecurityAnalysisResult>
    suspend fun getVerificationHistory(): Result<List<VerificationHistoryRecord>>
    suspend fun clearVerificationHistory(): Result<Int>
}
