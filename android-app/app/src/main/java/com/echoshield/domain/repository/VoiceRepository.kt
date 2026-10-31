package com.echoshield.domain.repository

import com.echoshield.domain.model.VoiceConsentRecord
import com.echoshield.domain.model.VoiceEnrollmentResult
import com.echoshield.domain.model.VoiceProfileInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface VoiceRepository {
    val voiceProfile: StateFlow<VoiceProfileInfo?>
    val isConsentGranted: StateFlow<Boolean>

    suspend fun recordConsent(granted: Boolean): Result<VoiceConsentRecord>
    suspend fun enrollVoiceSamples(sampleFiles: List<File>): Result<VoiceEnrollmentResult>
    suspend fun refreshVoiceProfile(): Result<VoiceProfileInfo>
    suspend fun deleteVoiceProfile(): Result<Unit>
}

